package xyz.ezsky.anilink.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.ezsky.anilink.entity.MediaLibrary;

public interface MediaLibraryRepository extends JpaRepository<MediaLibrary, Long> {
}
