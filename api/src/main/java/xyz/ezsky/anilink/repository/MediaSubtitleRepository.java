package xyz.ezsky.anilink.repository;

import xyz.ezsky.anilink.model.entity.MediaSubtitle;
import xyz.ezsky.anilink.repository.base.BaseRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface MediaSubtitleRepository extends BaseRepository<MediaSubtitle, Long> {
    List<MediaSubtitle> findByMediaFileIdOrderByStreamIndexAsc(Long mediaFileId);

    Optional<MediaSubtitle> findByMediaFileIdAndStreamIndex(Long mediaFileId, Integer streamIndex);

    @Transactional
    void deleteByMediaFileId(Long mediaFileId);

    @Transactional
    void deleteByMediaFileLibraryId(Long libraryId);
}
