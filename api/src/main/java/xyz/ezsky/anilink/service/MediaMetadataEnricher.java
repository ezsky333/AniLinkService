package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.dto.MediaMetadata;
import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.repository.MediaFileRepository;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 媒体文件元数据合并服务
 * 
 * 负责：
 * 1. 调用 FFprobe 提取视频技术信息（分辨率、编码等）
 * 2. 计算文件 MD5 哈希值
 * 3. 合并所有信息到 MediaFile 实体
 * 4. 提供钩子方法供外部调用接口获取动漫信息
 * 
 * 使用异步队列支持大规模并发处理，避免阻塞主扫描线程。
 */
@Log4j2
@Service
public class MediaMetadataEnricher {

    @Autowired
    private MediaProbeService mediaProbeService;

    @Autowired
    private MediaHashService mediaHashService;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    /**
     * 异步提取并补充媒体文件的完整元数据
     * 
     * 该方法不阻塞调用者，而是将任务提交到后台队列。
     * 
     * @param mediaFile 待补充元数据的 MediaFile 实体（必须已有 filePath）
     * @param queueManager 异步队列管理器
     */
    public void enrichMediaFileAsync(MediaFile mediaFile, MediaMetadataQueueManager queueManager) {
        Path filePath = Paths.get(mediaFile.getFilePath());

        // 提交到异步队列
        queueManager.submitMetadataExtraction(mediaFile, filePath, updatedFile -> {
            try {
                enrichMediaFileSync(updatedFile);
                // 在数据库中保存更新
                mediaFileRepository.save(updatedFile);
                log.info("Successfully enriched metadata for file: {}", updatedFile.getFilePath());
            } catch (Exception e) {
                log.error("Error enriching metadata for file: {}", mediaFile.getFilePath(), e);
            }
        });
    }

    /**
     * 同步提取和补充媒体文件的完整元数据
     * 
     * 用于首次扫描时快速提取元数据，或外部调用需要同步获取结果时使用。
     * 
     * @param mediaFile 待补充元数据的 MediaFile 实体（必须已有 filePath）
     */
    public void enrichMediaFileSync(MediaFile mediaFile) {
        Path filePath = Paths.get(mediaFile.getFilePath());
        long startTime = System.currentTimeMillis();

        try {
            // 第一步：调用 FFprobe 提取视频技术信息
            MediaMetadata metadata = mediaProbeService.parseMediaInfo(filePath);

            if (metadata != null && metadata.isSuccess()) {
                // 将元数据合并到 MediaFile 实体
                mergeMetadataToMediaFile(mediaFile, metadata);
            } else {
                log.warn("Failed to extract metadata using FFprobe for file: {}", filePath);
            }

            // 第二步：计算文件哈希值
            String hash = mediaHashService.calculateHash(filePath);
            if (hash != null) {
                mediaFile.setHash(hash);
            } else {
                log.warn("Failed to calculate hash for file: {}", filePath);
            }

            // 第三步：标记已获取元数据
            mediaFile.setMetadataFetched(true);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Enriched metadata for file in {} ms: {}", duration, filePath);

        } catch (Exception e) {
            log.error("Error enriching metadata for file: {}", filePath, e);
            mediaFile.setMetadataFetched(true); // 标记为已尝试，避免重复处理
        }
    }

    /**
     * 将 MediaMetadata 对象的字段合并到 MediaFile 实体
     */
    private void mergeMetadataToMediaFile(MediaFile mediaFile, MediaMetadata metadata) {
        mediaFile.setDuration(metadata.getDuration());
        mediaFile.setWidth(metadata.getWidth());
        mediaFile.setHeight(metadata.getHeight());
        mediaFile.setAspectRatio(metadata.getAspectRatio());
        mediaFile.setColorDepth(metadata.getColorDepth());
        mediaFile.setHdrType(metadata.getHdrType());
        mediaFile.setColorSpace(metadata.getColorSpace());
        mediaFile.setColorPrimaries(metadata.getColorPrimaries());
        mediaFile.setVideoBitrate(metadata.getVideoBitrate());
        mediaFile.setFps(metadata.getFps());
        mediaFile.setContainerFormat(metadata.getContainerFormat());
        mediaFile.setVideoCodec(metadata.getVideoCodec());
        mediaFile.setAudioCodec(metadata.getAudioCodec());
    }
}
