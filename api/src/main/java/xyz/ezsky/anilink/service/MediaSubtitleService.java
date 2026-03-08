package xyz.ezsky.anilink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.dto.MediaSubtitleDTO;
import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.model.entity.MediaSubtitle;
import xyz.ezsky.anilink.repository.MediaSubtitleRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Log4j2
@Service
public class MediaSubtitleService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MediaSubtitleRepository mediaSubtitleRepository;

    @Value("${media.subtitle.output-dir:./data/subtitles}")
    private String subtitleOutputDir;

    public List<MediaSubtitleDTO> listByMediaFileId(Long mediaFileId) {
        return mediaSubtitleRepository.findByMediaFileIdOrderByStreamIndexAsc(mediaFileId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public void extractSubtitlesIfMkv(MediaFile mediaFile) {
        if (mediaFile == null || mediaFile.getId() == null || mediaFile.getFilePath() == null) {
            return;
        }

        Path sourcePath = Paths.get(mediaFile.getFilePath());
        if (!Files.exists(sourcePath)) {
            log.warn("Skip subtitle extraction because source file does not exist: {}", sourcePath);
            cleanupByMediaFileId(mediaFile.getId());
            return;
        }

        String lowerName = sourcePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".mkv")) {
            cleanupByMediaFileId(mediaFile.getId());
            return;
        }

        cleanupByMediaFileId(mediaFile.getId());

        List<SubtitleStreamInfo> subtitleStreams = probeSubtitleStreams(sourcePath);
        if (subtitleStreams.isEmpty()) {
            log.debug("No subtitle stream found in MKV file: {}", sourcePath);
            return;
        }

        Path outputDir = Paths.get(subtitleOutputDir);
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            log.error("Failed to create subtitle output directory: {}", outputDir, e);
            return;
        }

        for (SubtitleStreamInfo streamInfo : subtitleStreams) {
            String safeTrackName = sanitizeTrackName(streamInfo.trackName());
            String fileExt = resolveSubtitleExtension(streamInfo.codecName());
            String fileName = mediaFile.getId() + "_" + safeTrackName + "_" + streamInfo.streamIndex() + "." + fileExt;
            Path outputPath = outputDir.resolve(fileName);

            if (!extractSubtitleStream(sourcePath, streamInfo.streamIndex(), outputPath)) {
                continue;
            }

            try {
                MediaSubtitle subtitle = mediaSubtitleRepository
                        .findByMediaFileIdAndStreamIndex(mediaFile.getId(), streamInfo.streamIndex())
                        .orElseGet(MediaSubtitle::new);

                subtitle.setMediaFile(mediaFile);
                subtitle.setStreamIndex(streamInfo.streamIndex());
                subtitle.setTrackName(streamInfo.trackName());
                subtitle.setLanguage(streamInfo.language());
                subtitle.setCodecName(streamInfo.codecName());
                subtitle.setSubtitleFormat(fileExt);
                subtitle.setFileName(fileName);
                subtitle.setFilePath(outputPath.toAbsolutePath().toString());
                subtitle.setFileSize(Files.size(outputPath));
                mediaSubtitleRepository.save(subtitle);
            } catch (DataIntegrityViolationException e) {
                // 并发场景下可能已有同 streamIndex 记录被其他线程写入，回退为更新确保幂等。
                mediaSubtitleRepository.findByMediaFileIdAndStreamIndex(mediaFile.getId(), streamInfo.streamIndex())
                        .ifPresent(existing -> {
                            existing.setTrackName(streamInfo.trackName());
                            existing.setLanguage(streamInfo.language());
                            existing.setCodecName(streamInfo.codecName());
                            existing.setSubtitleFormat(fileExt);
                            existing.setFileName(fileName);
                            existing.setFilePath(outputPath.toAbsolutePath().toString());
                            try {
                                existing.setFileSize(Files.size(outputPath));
                            } catch (IOException ioException) {
                                log.warn("Failed to read subtitle file size for {}", outputPath, ioException);
                            }
                            mediaSubtitleRepository.save(existing);
                        });
            } catch (Exception e) {
                log.error("Failed to persist subtitle metadata for stream {} in file {}",
                        streamInfo.streamIndex(), sourcePath, e);
            }
        }
    }

    public void cleanupByMediaFileId(Long mediaFileId) {
        List<MediaSubtitle> subtitles = mediaSubtitleRepository.findByMediaFileIdOrderByStreamIndexAsc(mediaFileId);
        for (MediaSubtitle subtitle : subtitles) {
            if (subtitle.getFilePath() == null || subtitle.getFilePath().isEmpty()) {
                continue;
            }
            try {
                Files.deleteIfExists(Paths.get(subtitle.getFilePath()));
            } catch (Exception e) {
                log.warn("Failed to delete subtitle file: {}", subtitle.getFilePath(), e);
            }
        }
        mediaSubtitleRepository.deleteByMediaFileId(mediaFileId);
    }

    private List<SubtitleStreamInfo> probeSubtitleStreams(Path sourcePath) {
        ProcessBuilder pb = new ProcessBuilder(
                "ffprobe",
                "-v", "quiet",
                "-print_format", "json",
                "-show_streams",
                sourcePath.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            String output = readProcessOutput(process);
            int exitCode = process.waitFor();
            if (exitCode != 0 || output.isEmpty()) {
                log.warn("FFprobe failed to read subtitle streams from file: {} (exitCode={})", sourcePath, exitCode);
                return List.of();
            }

            JsonNode root = OBJECT_MAPPER.readTree(output);
            JsonNode streamsNode = root.get("streams");
            if (streamsNode == null || !streamsNode.isArray()) {
                return List.of();
            }

            List<SubtitleStreamInfo> result = new ArrayList<>();
            for (JsonNode stream : streamsNode) {
                String codecType = stream.path("codec_type").asText("");
                if (!Objects.equals(codecType, "subtitle")) {
                    continue;
                }

                int streamIndex = stream.path("index").asInt(-1);
                if (streamIndex < 0) {
                    continue;
                }

                String codecName = stream.path("codec_name").asText("subrip");
                JsonNode tagsNode = stream.path("tags");
                String language = tagsNode.path("language").asText("");
                if (language.isBlank()) {
                    language = null;
                }

                String title = tagsNode.path("title").asText("");
                String trackName = title.isBlank() ? null : title;
                if (trackName == null) {
                    trackName = language != null ? language : "track";
                }

                result.add(new SubtitleStreamInfo(streamIndex, trackName, language, codecName));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to probe subtitle streams for file: {}", sourcePath, e);
            return List.of();
        }
    }

    private boolean extractSubtitleStream(Path sourcePath, int streamIndex, Path outputPath) {
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i", sourcePath.toAbsolutePath().toString(),
                "-map", "0:" + streamIndex,
                "-c:s", "copy",
                outputPath.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            String output = readProcessOutput(process);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("FFmpeg failed to extract subtitle stream {} from {} (exitCode={}): {}",
                        streamIndex, sourcePath, exitCode, output);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to extract subtitle stream {} from {}", streamIndex, sourcePath, e);
            return false;
        }
    }

    private String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        return output.toString();
    }

    private String sanitizeTrackName(String trackName) {
        String normalized = (trackName == null || trackName.isBlank()) ? "track" : trackName;
        String sanitized = normalized.replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_")
                .trim();

        if (sanitized.isBlank()) {
            return "track";
        }
        if (sanitized.length() > 80) {
            return sanitized.substring(0, 80);
        }
        return sanitized;
    }

    private String resolveSubtitleExtension(String codecName) {
        String codec = codecName == null ? "" : codecName.toLowerCase(Locale.ROOT);
        return switch (codec) {
            case "subrip" -> "srt";
            case "ass", "ssa" -> "ass";
            case "webvtt" -> "vtt";
            case "hdmv_pgs_subtitle" -> "sup";
            default -> "sub";
        };
    }

    private MediaSubtitleDTO toDTO(MediaSubtitle entity) {
        return MediaSubtitleDTO.builder()
                .id(entity.getId())
                .mediaFileId(entity.getMediaFile() != null ? entity.getMediaFile().getId() : null)
                .streamIndex(entity.getStreamIndex())
                .trackName(entity.getTrackName())
                .language(entity.getLanguage())
                .codecName(entity.getCodecName())
                .subtitleFormat(entity.getSubtitleFormat())
                .fileName(entity.getFileName())
                .filePath(entity.getFilePath())
                .fileSize(entity.getFileSize())
                .build();
    }

    private record SubtitleStreamInfo(int streamIndex, String trackName, String language, String codecName) {
    }
}
