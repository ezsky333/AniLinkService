package xyz.ezsky.anilink.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xyz.ezsky.anilink.entity.MediaLibrary;
import xyz.ezsky.anilink.repository.MediaLibraryRepository;
import xyz.ezsky.anilink.service.MediaScannerService;

import java.util.List;

@RestController
@RequestMapping("/api/media-library")
public class MediaLibraryController {

    @Autowired
    private MediaLibraryRepository mediaLibraryRepository;

    @Autowired
    private MediaScannerService mediaScannerService;

    @PostMapping
    public MediaLibrary addLibrary(@RequestBody MediaLibrary mediaLibrary) {
        MediaLibrary savedLibrary = mediaLibraryRepository.save(mediaLibrary);
        mediaScannerService.scanLibrary(savedLibrary);
        return savedLibrary;
    }

    @GetMapping
    public List<MediaLibrary> getAllLibraries() {
        return mediaLibraryRepository.findAll();
    }

    @DeleteMapping("/{id}")
    public void deleteLibrary(@PathVariable Long id) {
        mediaScannerService.stopWatching(id);
        mediaLibraryRepository.deleteById(id);
    }

    @PostMapping("/scan/{id}")
    public void scanLibrary(@PathVariable Long id) {
        mediaLibraryRepository.findById(id).ifPresent(mediaScannerService::scanLibrary);
    }

    @PostMapping("/scan-all")
    public void scanAllLibraries() {
        mediaScannerService.scanAllLibraries();
    }
}
