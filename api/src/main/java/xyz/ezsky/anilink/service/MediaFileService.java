package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.dto.MediaFileDTO;
import xyz.ezsky.anilink.model.dto.UpdateMediaFileRequest;
import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.model.entity.MediaLibrary;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.repository.MediaFileRepository;
import xyz.ezsky.anilink.repository.MediaLibraryRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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
     * 批量重新获取指定媒体库的元数据
     * 
     * @param libraryId 媒体库ID
     */
    public void reprocessMetadataForLibrary(Long libraryId) {
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
