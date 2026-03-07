package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 媒体匹配队列管理器
 * 
 * 负责：
 * 1. 接收单个新增文件的匹配请求
 * 2. 将文件ID添加到队列
 * 3. 每30秒取出最多20个文件进行批量匹配
 */
@Log4j2
@Service
public class MediaMatchQueueManager {

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private DandanMatchService dandanMatchService;

    @Autowired
    private MediaHashService mediaHashService;

    private final Set<Long> matchQueue = new HashSet<>();
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> scheduledTask;
    private static final int BATCH_SIZE = 20;
    private static final int QUEUE_INTERVAL_SECONDS = 30;

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
        synchronized (matchQueue) {
            matchQueue.add(mediaFileId);
            log.debug("Added file {} to match queue, current size: {}", mediaFileId, matchQueue.size());
        }
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
            QUEUE_INTERVAL_SECONDS,
            QUEUE_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        log.info("Queue processor started with interval: {}s", QUEUE_INTERVAL_SECONDS);
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
        List<Long> filesToProcess = new ArrayList<>();

        synchronized (matchQueue) {
            if (matchQueue.isEmpty()) {
                return;
            }

            // 获取最多50个文件ID
            int count = 0;
            for (Long fileId : matchQueue) {
                if (count >= BATCH_SIZE) {
                    break;
                }
                filesToProcess.add(fileId);
                count++;
            }

            // 从队列中移除已取出的文件
            filesToProcess.forEach(matchQueue::remove);
        }

        if (filesToProcess.isEmpty()) {
            return;
        }

        log.info("Processing {} files from match queue", filesToProcess.size());

        try {
            // 获取这些文件的完整信息
            List<MediaFile> mediaFiles = new ArrayList<>();
            for (Long fileId : filesToProcess) {
                var mediaFile = mediaFileRepository.findById(fileId);
                if (mediaFile.isPresent()) {
                    mediaFiles.add(mediaFile.get());
                }
            }

            if (mediaFiles.isEmpty()) {
                log.warn("No media files found for queue processing");
                return;
            }

            // 处理批次
            processBatch(mediaFiles);

        } catch (Exception e) {
            log.error("Error processing match queue", e);
        }
    }

    /**
     * 处理单个批次的文件
     */
    private void processBatch(List<MediaFile> batch) {
        List<Map<String, Object>> fileInfos = new ArrayList<>();

        for (MediaFile mediaFile : batch) {
            // 检查文件是否存在
            if (!Files.exists(Paths.get(mediaFile.getFilePath()))) {
                log.warn("File no longer exists: {}", mediaFile.getFilePath());
                mediaFile.setMatchStatus(MatchStatus.NO_MATCH_FOUND);
                mediaFileRepository.save(mediaFile);
                continue;
            }

            // 确保hash已计算
            if (mediaFile.getHash() == null || mediaFile.getHash().isEmpty()) {
                try {
                    String hash = mediaHashService.calculateHash(Paths.get(mediaFile.getFilePath()));
                    if (hash != null) {
                        mediaFile.setHash(hash);
                        mediaFileRepository.save(mediaFile);
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
        }

        if (fileInfos.isEmpty()) {
            log.warn("No valid file info to match in queue batch");
            return;
        }

        // 调用批量匹配接口
        List<MatchResult> matchResults = dandanMatchService.batchMatch(fileInfos);

        // 更新匹配结果
        for (int i = 0; i < matchResults.size() && i < batch.size(); i++) {
            MatchResult result = matchResults.get(i);
            MediaFile mediaFile = batch.get(i);

            try {
                if (result.getSuccess() != null && result.getSuccess()) {
                    // 匹配成功
                    mediaFile.setMatchStatus(MatchStatus.MATCHED);
                    mediaFile.setEpisodeId(result.getEpisodeId());
                    mediaFile.setAnimeId(result.getAnimeId());
                    mediaFile.setAnimeTitle(result.getAnimeTitle());
                    mediaFile.setEpisodeTitle(result.getEpisodeTitle());
                    log.info("Matched file {} -> episodeId: {}", mediaFile.getFilePath(), result.getEpisodeId());
                } else {
                    // 匹配失败
                    mediaFile.setMatchStatus(MatchStatus.NO_MATCH_FOUND);
                    log.debug("No match found for file: {}", mediaFile.getFilePath());
                }

                mediaFileRepository.save(mediaFile);

            } catch (Exception e) {
                log.error("Error updating match result for file: {}", mediaFile.getFilePath(), e);
            }
        }
    }

    /**
     * 获取队列中当前的文件数量（用于监控）
     */
    public int getQueueSize() {
        synchronized (matchQueue) {
            return matchQueue.size();
        }
    }
}
