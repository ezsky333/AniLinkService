package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import xyz.ezsky.anilink.model.dto.MatchResult;
import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.model.entity.MatchStatus;
import xyz.ezsky.anilink.repository.MediaFileRepository;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 媒体匹配队列管理器
 * 
 * 负责：
 * 1. 接收单个新增文件的匹配请求
 * 2. 将文件ID添加到队列
 * 3. 按固定间隔批量匹配，并在队列积压时尽快触发处理
 */
@Log4j2
@Service
public class MediaMatchQueueManager {

    private static final List<MatchStatus> AUTO_PENDING_STATUSES = Arrays.asList(MatchStatus.UNMATCHED);

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private DandanMatchService dandanMatchService;

    @Autowired
    private MediaHashService mediaHashService;
    
    @Autowired
    private xyz.ezsky.anilink.service.notification.EpisodeUpdateNotificationService episodeUpdateNotificationService;

    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> scheduledTask;
    private final AtomicBoolean immediateDrainScheduled = new AtomicBoolean(false);

    @Value("${anilink.match-queue.batch-size:20}")
    private int batchSize;

    @Value("${anilink.match-queue.interval-seconds:10}")
    private int queueIntervalSeconds;

    private final AtomicInteger activeBatches = new AtomicInteger(0);
    private final AtomicLong totalEnqueued = new AtomicLong(0);
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalMatched = new AtomicLong(0);
    private final AtomicLong totalNoMatch = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);

    /**
     * 初始化队列处理任务
     */
    @PostConstruct
    public void init() {
        startQueueProcessor();
        log.info("MediaMatchQueueManager initialized");
    }

    /**
     * 销毁时停止队列处理
     */
    @PreDestroy
    public void destroy() {
        stopQueueProcessor();
        log.info("MediaMatchQueueManager destroyed");
    }

    /**
     * 将文件添加到匹配队列
     * 
     * @param mediaFileId 待匹配文件的 ID
     */
    public void addToQueue(Long mediaFileId) {
        totalEnqueued.incrementAndGet();
        log.debug("Match trigger accepted for file: {}", mediaFileId);
        scheduleImmediateDrain();
    }

    /**
     * 将指定媒体库中满足条件的文件加入匹配队列。
     * 
     * @param libraryId 媒体库 ID
     * @return 成功加入队列的文件数量
     */
    public int enqueueLibraryForRematch(Long libraryId) {
        List<MediaFile> candidates = mediaFileRepository.findByLibraryIdAndMatchStatus(
                libraryId,
                new MatchStatus[]{MatchStatus.UNMATCHED, MatchStatus.NO_MATCH_FOUND}
        );

        int enqueued = 0;
        for (MediaFile mediaFile : candidates) {
            mediaFile.setMatchStatus(MatchStatus.UNMATCHED);
            mediaFileRepository.save(mediaFile);
            enqueued++;
        }

        if (enqueued > 0) {
            totalEnqueued.addAndGet(enqueued);
            scheduleImmediateDrain();
        }

        return enqueued;
    }

    /**
     * 启动定时队列处理任务
     */
    private void startQueueProcessor() {
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            return;
        }

        scheduledTask = scheduledExecutor.scheduleAtFixedRate(
            this::processQueue,
            queueIntervalSeconds,
            queueIntervalSeconds,
            TimeUnit.SECONDS
        );
        log.info("Queue processor started with interval: {}s", queueIntervalSeconds);
    }

    /**
     * 停止定时队列处理任务
     */
    private void stopQueueProcessor() {
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(false);
            log.info("Queue processor stopped");
        }
    }

    /**
     * 处理队列中的文件
     */
    private void processQueue() {
        List<MediaFile> mediaFiles = mediaFileRepository
                .findByMatchStatusIn(AUTO_PENDING_STATUSES, PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, "id")))
                .getContent();

        if (mediaFiles.isEmpty()) {
            return;
        }

        log.info("Processing {} files from match queue", mediaFiles.size());
        activeBatches.incrementAndGet();

        try {
            // 处理批次
            processBatch(mediaFiles);

        } catch (Exception e) {
            log.error("Error processing match queue", e);
            totalFailed.incrementAndGet();
        } finally {
            activeBatches.decrementAndGet();

            boolean hasMore = mediaFileRepository.countByMatchStatusIn(AUTO_PENDING_STATUSES) > 0;

            // 当前批次完成后，如果还有积压，继续快速排空，避免必须等待下一个固定周期。
            if (hasMore) {
                scheduleImmediateDrain();
            }
        }
    }

    private void scheduleImmediateDrain() {
        if (!immediateDrainScheduled.compareAndSet(false, true)) {
            return;
        }

        scheduledExecutor.execute(() -> {
            try {
                processQueue();
            } finally {
                immediateDrainScheduled.set(false);

                boolean hasMore = mediaFileRepository.countByMatchStatusIn(AUTO_PENDING_STATUSES) > 0;

                if (hasMore) {
                    scheduleImmediateDrain();
                }
            }
        });
    }

    /**
     * 处理单个批次的文件
     */
    private void processBatch(List<MediaFile> batch) {
        List<Map<String, Object>> fileInfos = new ArrayList<>();
        List<MediaFile> requestFiles = new ArrayList<>();

        for (MediaFile mediaFile : batch) {
            Long mediaFileId = mediaFile.getId();

            // 检查文件是否存在
            if (!Files.exists(Paths.get(mediaFile.getFilePath()))) {
                log.warn("File no longer exists: {}", mediaFile.getFilePath());
                markNoMatchPreservingMetadata(mediaFileId);
                totalProcessed.incrementAndGet();
                totalNoMatch.incrementAndGet();
                continue;
            }

            // 确保hash已计算
            if (mediaFile.getHash() == null || mediaFile.getHash().isEmpty()) {
                mediaFileRepository.findById(mediaFileId).ifPresent(latest -> {
                    if (latest.getHash() != null && !latest.getHash().isEmpty()) {
                        mediaFile.setHash(latest.getHash());
                    }
                });
            }

            if (mediaFile.getHash() == null || mediaFile.getHash().isEmpty()) {
                try {
                    String hash = mediaHashService.calculateHash(Paths.get(mediaFile.getFilePath()));
                    if (hash != null) {
                        mediaFile.setHash(hash);
                        saveHashOnly(mediaFileId, hash);
                        log.debug("Calculated hash for file: {}", mediaFile.getFilePath());
                    }
                } catch (Exception e) {
                    log.warn("Failed to calculate hash for {}: {}", mediaFile.getFilePath(), e.getMessage());
                }
            }

            // 准备匹配信息
            Map<String, Object> fileInfo = DandanMatchService.createFileInfo(
                mediaFile.getFileName(),
                mediaFile.getHash(),
                mediaFile.getSize()
            );
            fileInfos.add(fileInfo);
            requestFiles.add(mediaFile);
        }

        if (fileInfos.isEmpty()) {
            log.warn("No valid file info to match in queue batch");
            return;
        }

        // 调用批量匹配接口
        List<MatchResult> matchResults = dandanMatchService.batchMatch(fileInfos);

        // 更新匹配结果
        for (int i = 0; i < matchResults.size() && i < requestFiles.size(); i++) {
            MatchResult result = matchResults.get(i);
            MediaFile mediaFile = requestFiles.get(i);

            try {
                if (result.getSuccess() != null && result.getSuccess()) {
                    saveMatchedResultPreservingMetadata(mediaFile.getId(), result);
                    log.info("Matched file {} -> episodeId: {}", mediaFile.getFilePath(), result.getEpisodeId());
                    totalMatched.incrementAndGet();
                } else {
                    markNoMatchPreservingMetadata(mediaFile.getId());
                    log.debug("No match found for file: {}", mediaFile.getFilePath());
                    totalNoMatch.incrementAndGet();
                }
                totalProcessed.incrementAndGet();

            } catch (Exception e) {
                log.error("Error updating match result for file: {}", mediaFile.getFilePath(), e);
                totalFailed.incrementAndGet();
            }
        }

        if (matchResults.size() < requestFiles.size()) {
            long notReturned = requestFiles.size() - matchResults.size();
            totalFailed.addAndGet(notReturned);
            log.warn("Match results size mismatch, missing {} results in current batch", notReturned);
        }
    }

    /**
     * 获取队列中当前的文件数量（用于监控）
     */
    public int getQueueSize() {
        return (int) mediaFileRepository.countByMatchStatusIn(AUTO_PENDING_STATUSES);
    }

    public int getActiveBatches() {
        return activeBatches.get();
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getQueueIntervalSeconds() {
        return queueIntervalSeconds;
    }

    public long getTotalEnqueued() {
        return totalEnqueued.get();
    }

    public long getTotalProcessed() {
        return totalProcessed.get();
    }

    public long getTotalMatched() {
        return totalMatched.get();
    }

    public long getTotalNoMatch() {
        return totalNoMatch.get();
    }

    public long getTotalFailed() {
        return totalFailed.get();
    }

    private void saveHashOnly(Long mediaFileId, String hash) {
        if (mediaFileId == null || hash == null || hash.isEmpty()) {
            return;
        }

        mediaFileRepository.findById(mediaFileId).ifPresent(latest -> {
            latest.setHash(hash);
            mediaFileRepository.save(latest);
        });
    }

    private void markNoMatchPreservingMetadata(Long mediaFileId) {
        if (mediaFileId == null) {
            return;
        }

        mediaFileRepository.findById(mediaFileId).ifPresent(latest -> {
            latest.setMatchStatus(MatchStatus.NO_MATCH_FOUND);
            mediaFileRepository.save(latest);
        });
    }

    private void saveMatchedResultPreservingMetadata(Long mediaFileId, MatchResult result) {
        if (mediaFileId == null || result == null) {
            return;
        }

        mediaFileRepository.findById(mediaFileId).ifPresent(latest -> {
            latest.setMatchStatus(MatchStatus.MATCHED);
            latest.setEpisodeId(result.getEpisodeId());
            latest.setAnimeId(result.getAnimeId());
            latest.setAnimeTitle(result.getAnimeTitle());
            latest.setEpisodeTitle(result.getEpisodeTitle());
            mediaFileRepository.save(latest);
            
            // 异步通知追番用户
            episodeUpdateNotificationService.notifyFollowingUsersAsync(latest);
        });
    }
}
