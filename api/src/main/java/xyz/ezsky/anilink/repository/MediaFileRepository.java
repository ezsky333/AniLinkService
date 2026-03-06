package xyz.ezsky.anilink.repository;

import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.repository.base.BaseRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface MediaFileRepository extends BaseRepository<MediaFile, Long> {
    List<MediaFile> findByLibraryId(Long libraryId);
    Optional<MediaFile> findByFilePath(String filePath);

    // 新的分页查询，按数据库ID排序由调用方的 Pageable 决定
    Page<MediaFile> findByAnimeId(Long animeId, Pageable pageable);
}
