package xyz.ezsky.anilink.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.ezsky.anilink.entity.MediaFile;

import java.util.List;
import java.util.Optional;

public interface MediaFileRepository extends JpaRepository<MediaFile, Long> {
    List<MediaFile> findByLibraryId(Long libraryId);
    Optional<MediaFile> findByFilePath(String filePath);
}
