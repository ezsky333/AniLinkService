package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.dto.MediaFileDTO;
import xyz.ezsky.anilink.model.dto.UpdateMediaFileRequest;
import xyz.ezsky.anilink.model.entity.MatchStatus;
import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.model.entity.MediaLibrary;
import xyz.ezsky.anilink.model.vo.MatchProgressVO;
import xyz.ezsky.anilink.model.vo.MetadataProgressVO;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.repository.MediaFileRepository;
import xyz.ezsky.anilink.repository.MediaLibraryRepository;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 媒体文件业务服务
 * 
 * 负责处理媒体文件的查询、更新、删除等操作
 */
@Log4j2
@Service
public class MediaFileService {

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private MediaLibraryRepository mediaLibraryRepository;

    @Autowired
    private MediaMetadataEnricher mediaMetadataEnricher;

    @Autowired
    private MediaMetadataQueueManager metadataQueueManager;

    @Autowired
    private MediaSubtitleService mediaSubtitleService;

    @Autowired
    private MediaMatchQueueManager mediaMatchQueueManager;

    /**
     * 分页查询媒体文件
     * 
     * @param libraryId 媒体库ID（可选，为null时查询所有）
     * @param page 页码（从0开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageVO<MediaFileDTO> getMediaFiles(Long libraryId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize);
        Page<MediaFile> result;

        if (libraryId != null) {
            // 特定媒体库的媒体文件
            List<MediaFile> files = mediaFileRepository.findByLibraryId(libraryId);
            int start = page * pageSize;
            int end = Math.min((page + 1) * pageSize, files.size());
            List<MediaFile> pageContent = files.subList(start, Math.min(end, files.size()));
            
            return PageVO.<MediaFileDTO>builder()
                    .content(pageContent.stream().map(this::toDTO).collect(Collectors.toList()))
                    .totalElements(files.size())
                    .totalPages((files.size() + pageSize - 1) / pageSize)
                    .currentPage(page)
                    .pageSize(pageSize)
                    .hasNext(end < files.size())
                    .hasPrevious(page > 0)
                    .build();
        } else {
            // 查询所有媒体文件
            result = mediaFileRepository.findAll(pageable);
            return PageVO.<MediaFileDTO>builder()
                    .content(result.getContent().stream().map(this::toDTO).collect(Collectors.toList()))
                    .totalElements(result.getTotalElements())
                    .totalPages(result.getTotalPages())
                    .currentPage(result.getNumber())
                    .pageSize(result.getSize())
                    .hasNext(result.hasNext())
                    .hasPrevious(result.hasPrevious())
                    .build();
        }
    }

    /**
     * 获取媒体文件详情
     * 
     * @param fileId 文件ID
     * @return 文件详情DTO
     */
    public MediaFileDTO getMediaFileDetail(Long fileId) {
        return mediaFileRepository.findById(fileId)
                .map(this::toDTO)
                .orElse(null);
    }

    /**
     * 获取媒体文件实体
     * 
     * @param fileId 文件ID
     * @return 媒体文件实体
     */
    public MediaFile getMediaFileById(Long fileId) {
        return mediaFileRepository.findById(fileId).orElse(null);
    }

    /**
     * 批量重新获取指定媒体库的元数据（异步）
     * 
     * @param libraryId 媒体库ID
     * @return CompletableFuture<Void>
     */
    @Async
    public CompletableFuture<Void> reprocessMetadataForLibrary(Long libraryId) {
        List<MediaFile> files = mediaFileRepository.findByLibraryId(libraryId);
        
        for (MediaFile file : files) {
            // 重置元数据标志
            file.setMetadataFetched(false);
            mediaFileRepository.save(file);
            
            // 提交到异步队列重新处理
            Path filePath = Paths.get(file.getFilePath());
            mediaMetadataEnricher.enrichMediaFileAsync(file, metadataQueueManager);
        }
        
        log.info("Submitted {} files from library {} for metadata reprocessing", files.size(), libraryId);
        return CompletableFuture.completedFuture(null);
    }

    public MetadataProgressVO getMetadataProgress(Long libraryId) {
        long totalFiles = libraryId == null ? mediaFileRepository.count() : mediaFileRepository.countByLibraryId(libraryId);
        long metadataFetched = libraryId == null
                ? mediaFileRepository.countByMetadataFetchedTrue()
                : mediaFileRepository.countByLibraryIdAndMetadataFetchedTrue(libraryId);
        
        // 库级别的待处理数 = 总文件数 - 已获取元数据的文件数
        long libraryPendingMetadata = Math.max(0, totalFiles - metadataFetched);

        return MetadataProgressVO.builder()
                .libraryId(libraryId)
                .totalFiles(totalFiles)
                .metadataFetched(metadataFetched)
                .pendingMetadata(libraryPendingMetadata)
            // queuePending 表示真实队列长度（不含正在执行中的线程）
            .queuePending(metadataQueueManager.getQueueSize())
                .activeThreads(metadataQueueManager.getActiveThreadCount())
                .maxPoolSize(metadataQueueManager.getMaxPoolSize())
                .totalSubmitted(metadataQueueManager.getTotalSubmitted())
                .totalProcessed(metadataQueueManager.getTotalProcessed())
                .failedTasks(metadataQueueManager.getTotalFailed())
                .build();
    }

    public MatchProgressVO getMatchProgress(Long libraryId) {
        long totalFiles = libraryId == null ? mediaFileRepository.count() : mediaFileRepository.countByLibraryId(libraryId);
        long matched = libraryId == null
                ? mediaFileRepository.countByMatchStatus(MatchStatus.MATCHED)
                : mediaFileRepository.countByLibraryIdAndMatchStatus(libraryId, MatchStatus.MATCHED);
        long noMatch = libraryId == null
                ? mediaFileRepository.countByMatchStatus(MatchStatus.NO_MATCH_FOUND)
                : mediaFileRepository.countByLibraryIdAndMatchStatus(libraryId, MatchStatus.NO_MATCH_FOUND);
        
        // 库级别的待处理匹配数 = 总文件数 - 已匹配的 - 无匹配的
        long libraryPendingMatch = Math.max(0, totalFiles - matched - noMatch);

        return MatchProgressVO.builder()
                .libraryId(libraryId)
                .totalFiles(totalFiles)
                .matched(matched)
                .noMatch(noMatch)
                .pendingMatch(libraryPendingMatch)
            // queuePending 表示真实队列长度（当前为全局匹配队列）
            .queuePending(mediaMatchQueueManager.getQueueSize())
                .activeBatches(mediaMatchQueueManager.getActiveBatches())
                .batchSize(mediaMatchQueueManager.getBatchSize())
                .queueIntervalSeconds(mediaMatchQueueManager.getQueueIntervalSeconds())
                .totalEnqueued(mediaMatchQueueManager.getTotalEnqueued())
                .totalProcessed(mediaMatchQueueManager.getTotalProcessed())
                .totalMatched(mediaMatchQueueManager.getTotalMatched())
                .totalNoMatch(mediaMatchQueueManager.getTotalNoMatch())
                .failedTasks(mediaMatchQueueManager.getTotalFailed())
                .build();
    }

    /**
     * 删除媒体文件记录
     * 
     * @param fileId 文件ID
     * @param deletePhysicalFile 是否同时删除硬盘上的文件
     */
    public void deleteMediaFile(Long fileId, boolean deletePhysicalFile) {
        mediaFileRepository.findById(fileId).ifPresentOrElse(
                mediaFile -> {
                    String filePath = mediaFile.getFilePath();

                    // 删除抽取的字幕文件和字幕记录
                    mediaSubtitleService.cleanupByMediaFileId(mediaFile.getId());
                    
                    // 删除数据库记录
                    mediaFileRepository.delete(mediaFile);
                    log.info("Deleted media file record: {}", filePath);
                    
                    // 可选：删除硬盘上的文件
                    if (deletePhysicalFile) {
                        try {
                            File file = new File(filePath);
                            if (file.exists()) {
                                if (file.delete()) {
                                    log.info("Deleted physical file: {}", filePath);
                                } else {
                                    log.warn("Failed to delete physical file: {}", filePath);
                                }
                            }
                        } catch (Exception e) {
                            log.error("Error deleting physical file: {}", filePath, e);
                        }
                    }
                },
                () -> log.warn("Media file not found with id: {}", fileId)
        );
    }

    /**
     * 更新媒体文件信息
     * 
     * @param fileId 文件ID
     * @param request 更新请求DTO
     */
    public void updateMediaFile(Long fileId, UpdateMediaFileRequest request) {
        mediaFileRepository.findById(fileId).ifPresentOrElse(
                mediaFile -> {
                    if (request.getEpisodeId() != null) {
                        mediaFile.setEpisodeId(request.getEpisodeId());
                    }
                    if (request.getAnimeId() != null) {
                        mediaFile.setAnimeId(request.getAnimeId());
                    }
                    if (request.getAnimeTitle() != null) {
                        mediaFile.setAnimeTitle(request.getAnimeTitle());
                    }
                    if (request.getEpisodeTitle() != null) {
                        mediaFile.setEpisodeTitle(request.getEpisodeTitle());
                    }
                    mediaFileRepository.save(mediaFile);
                    log.info("Updated media file: {}", fileId);
                },
                () -> log.warn("Media file not found with id: {}", fileId)
        );
    }

    /**
     * Entity to DTO 转换
     */
    private MediaFileDTO toDTO(MediaFile entity) {
        return MediaFileDTO.builder()
                .id(entity.getId())
                .libraryId(entity.getLibrary() != null ? entity.getLibrary().getId() : null)
                .fileName(entity.getFileName())
                .filePath(entity.getFilePath())
                .size(entity.getSize())
                .lastModified(entity.getLastModified())
                .episodeId(entity.getEpisodeId())
                .animeId(entity.getAnimeId())
                .animeTitle(entity.getAnimeTitle())
                .episodeTitle(entity.getEpisodeTitle())
                .duration(entity.getDuration())
                .hash(entity.getHash())
                .width(entity.getWidth())
                .height(entity.getHeight())
                .aspectRatio(entity.getAspectRatio())
                .colorDepth(entity.getColorDepth())
                .hdrType(entity.getHdrType())
                .colorSpace(entity.getColorSpace())
                .colorPrimaries(entity.getColorPrimaries())
                .videoBitrate(entity.getVideoBitrate())
                .fps(entity.getFps())
                .containerFormat(entity.getContainerFormat())
                .videoCodec(entity.getVideoCodec())
                .audioCodec(entity.getAudioCodec())
                .metadataFetched(entity.getMetadataFetched())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
