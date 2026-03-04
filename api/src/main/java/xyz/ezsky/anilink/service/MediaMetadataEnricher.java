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

    @Autowired
    private DandanMatchService dandanMatchService;

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

            // 第四步：预留钩子方法供外部调用
            enrichExternalMetadata(mediaFile);

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

    /**
     * 钩子方法：供外部接口调用以获取动漫信息
     * 
     * 该方法在元数据提取完成后调用，允许外部通过 API 查询弹幕库或动漫数据库，
     * 从而填充 episodeId、animeId、animeTitle、episodeTitle 等字段。
     * 
     * 当前实现为空，由用户在需要时调用外部接口并更新这些字段。
     * 
     * 使用示例：
     * <pre>
     * AnimeInfo animeInfo = externalAnimeService.queryByHash(mediaFile.getHash());
     * if (animeInfo != null) {
     *     mediaFile.setEpisodeId(animeInfo.getEpisodeId());
     *     mediaFile.setAnimeId(animeInfo.getAnimeId());
     *     mediaFile.setAnimeTitle(animeInfo.getAnimeTitle());
     *     mediaFile.setEpisodeTitle(animeInfo.getEpisodeTitle());
     * }
     * </pre>
     * 
     * @param mediaFile 已包含技术元数据的 MediaFile 实体
     */
    protected void enrichExternalMetadata(MediaFile mediaFile) {
        // 该方法可被子类重写以实现自定义逻辑
        // 或由 Controller 层在需要时调用外部接口
        
        // 示例钩子点（当前未实现）：
        // 1. 根据 mediaFile.getHash() 查询弹幕库 API
        // 2. 根据文件名或其他信息推断动漫信息
        // 3. 缓存查询结果以避免重复请求
        
        log.debug("Hook point for external metadata enrichment: {}", mediaFile.getFilePath());

        try {
            // 优先使用已计算的文件哈希进行匹配
            String hash = mediaFile.getHash();
            String fileName = mediaFile.getFileName();
            Long fileSize = mediaFile.getSize();

            if (hash == null && (fileName == null || fileName.isEmpty())) {
                // 没有可用信息，跳过
                return;
            }

            // 调用弹弹匹配服务
            xyz.ezsky.anilink.model.dto.AnimeInfo animeInfo = dandanMatchService.queryByFile(fileName, hash, fileSize);
            if (animeInfo != null) {
                mediaFile.setEpisodeId(animeInfo.getEpisodeId());
                mediaFile.setAnimeId(animeInfo.getAnimeId());
                mediaFile.setAnimeTitle(animeInfo.getAnimeTitle());
                mediaFile.setEpisodeTitle(animeInfo.getEpisodeTitle());

                // 立刻持久化匹配结果，避免丢失
                try {
                    mediaFileRepository.save(mediaFile);
                    log.info("External match found and saved for file {} -> episodeId={}", mediaFile.getFilePath(), animeInfo.getEpisodeId());
                } catch (Exception e) {
                    log.error("Failed to save media file after external match for {}", mediaFile.getFilePath(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error while enriching external metadata for {}", mediaFile.getFilePath(), e);
        }
    }

    /**
     * 提供方法供外部 API 调用，用来更新动漫相关字段
     * 
     * 该方法可以被 Controller 或其他服务调用，用来设置从外部接口获取的动漫信息。
     * 
     * @param mediaFileId 媒体文件 ID
     * @param episodeId   弹幕库编号（可能为 null）
     * @param animeId     动漫编号
     * @param animeTitle  动漫主标题
     * @param episodeTitle 剧集子标题
     */
    public void updateAnimeMetadata(Long mediaFileId, String episodeId, Long animeId, 
                                   String animeTitle, String episodeTitle) {
        try {
            mediaFileRepository.findById(mediaFileId).ifPresentOrElse(
                    mediaFile -> {
                        mediaFile.setEpisodeId(episodeId);
                        mediaFile.setAnimeId(animeId);
                        mediaFile.setAnimeTitle(animeTitle);
                        mediaFile.setEpisodeTitle(episodeTitle);
                        mediaFileRepository.save(mediaFile);
                        log.info("Updated anime metadata for media file id: {}", mediaFileId);
                    },
                    () -> log.warn("Media file not found with id: {}", mediaFileId)
            );
        } catch (Exception e) {
            log.error("Error updating anime metadata for media file id: {}", mediaFileId, e);
        }
    }
}
