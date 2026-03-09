package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.dto.MatchResult;
import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.model.entity.MatchStatus;
import xyz.ezsky.anilink.repository.MediaFileRepository;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 媒体库级别的批量匹配服务
 * 
 * 负责：
 * 1. 在扫描完成后，触发媒体库的批量匹配
 * 2. 按50个文件一批调用弹弹play的批量匹配API
 * 3. 更新文件的匹配状态和匹配结果
 */
@Log4j2
@Service
public class MediaMatchBatchService {

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private DandanMatchService dandanMatchService;

    @Autowired
    private MediaHashService mediaHashService;
    
    @Autowired
    private xyz.ezsky.anilink.service.notification.EpisodeUpdateNotificationService episodeUpdateNotificationService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private static final int BATCH_SIZE = 20;

    /**
     * 对整个媒体库进行批量匹配
     * 在扫描完成后调用
     * 
     * @param libraryId 媒体库 ID
     */
    public void matchLibraryAsync(Long libraryId) {
        executorService.submit(() -> {
            try {
                matchLibrarySync(libraryId);
            } catch (Exception e) {
                log.error("Error matching library {}", libraryId, e);
            }
        });
    }

    /**
     * 同步地对媒体库进行批量匹配
     * 
     * @param libraryId 媒体库 ID
     */
    public void matchLibrarySync(Long libraryId) {
        log.info("Starting batch match for library: {}", libraryId);
        
        try {
            // 获取该库中未匹配或匹配失败的文件
            List<MediaFile> unmatchedFiles = mediaFileRepository.findByLibraryIdAndMatchStatus(
                libraryId, 
                new MatchStatus[]{MatchStatus.UNMATCHED, MatchStatus.NO_MATCH_FOUND}
            );

            if (unmatchedFiles.isEmpty()) {
                log.info("No unmatched files in library: {}", libraryId);
                return;
            }

            log.info("Found {} unmatched files in library: {}", unmatchedFiles.size(), libraryId);

            // 按批次处理
            for (int i = 0; i < unmatchedFiles.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, unmatchedFiles.size());
                List<MediaFile> batch = unmatchedFiles.subList(i, end);
                
                processBatch(batch);
                
                log.info("Processed batch {}-{} of {}", i, end, unmatchedFiles.size());
            }

            log.info("Completed batch match for library: {}", libraryId);

        } catch (Exception e) {
            log.error("Error during library batch match: {}", libraryId, e);
        }
    }

    /**
     * 处理单个匹配批次
     */
    private void processBatch(List<MediaFile> batch) {
        List<Map<String, Object>> fileInfos = new ArrayList<>();
        Map<Integer, MediaFile> indexToFile = new HashMap<>();

        for (int i = 0; i < batch.size(); i++) {
            MediaFile mediaFile = batch.get(i);
            
            // 对于NO_MATCH_FOUND状态，需要重新生成哈希
            if (mediaFile.getMatchStatus() == MatchStatus.NO_MATCH_FOUND) {
                try {
                    if (!Files.exists(Paths.get(mediaFile.getFilePath()))) {
                        log.warn("File no longer exists: {}", mediaFile.getFilePath());
                        continue;
                    }
                    
                    // 重新计算hash
                    String hash = mediaHashService.calculateHash(Paths.get(mediaFile.getFilePath()));
                    if (hash != null) {
                        mediaFile.setHash(hash);
                        mediaFileRepository.save(mediaFile);
                        log.debug("Recalculated hash for file: {}", mediaFile.getFilePath());
                    }
                } catch (Exception e) {
                    log.warn("Failed to recalculate hash for {}: {}", mediaFile.getFilePath(), e.getMessage());
                }
            }

            // 准备匹配信息
            Map<String, Object> fileInfo = DandanMatchService.createFileInfo(
                mediaFile.getFileName(),
                mediaFile.getHash(),
                mediaFile.getSize()
            );
            fileInfos.add(fileInfo);
            indexToFile.put(i, mediaFile);
        }

        if (fileInfos.isEmpty()) {
            log.warn("No valid file info to match in batch");
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
                    log.debug("Matched file {} -> episodeId: {}", mediaFile.getFilePath(), result.getEpisodeId());
                    
                    // 保存到数据库
                    MediaFile saved = mediaFileRepository.save(mediaFile);
                    
                    // 异步通知追番用户
                    try {
                        episodeUpdateNotificationService.notifyFollowingUsersAsync(saved);
                    } catch (Exception e) {
                        log.warn("Failed to trigger notification for batch match: {}", e.getMessage());
                    }
                } else {
                    // 匹配失败
                    mediaFile.setMatchStatus(MatchStatus.NO_MATCH_FOUND);
                    log.debug("No match found for file: {}", mediaFile.getFilePath());
                    mediaFileRepository.save(mediaFile);
                }

            } catch (Exception e) {
                log.error("Error updating match result for file: {}", mediaFile.getFilePath(), e);
            }
        }
    }

    /**
     * 为单个新增的文件添加到匹配队列
     * 这个方法由外部调用，将文件的ID添加到匹配队列
     */
    public void addFileToMatchQueue(Long mediaFileId) {
        // 这在MediaMatchQueueManager中实现
        // 这里仅做记录
        log.debug("File {} added to match queue", mediaFileId);
    }
}
