package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.entity.MediaFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 视频缩略图生成服务
 *
 * 在元数据扫描完成后，使用 FFmpeg 从视频中截取一帧作为缩略图，
 * 以视频 ID 为文件名保存到可挂载目录中。
 */
@Log4j2
@Service
public class MediaThumbnailService {

    /** 缩略图输出目录，可通过容器环境变量 THUMBNAIL_DIR 覆盖 */
    @Value("${media.thumbnail.output-dir:./data/thumbnails}")
    private String thumbnailOutputDir;

    /**
     * 为指定媒体文件生成缩略图（幂等：已存在则跳过）
     *
     * 截取时间点取视频时长的 20%，最少 5 秒，避免黑屏开头。
     * 若时长未知则固定取 30 秒处。
     *
     * @param mediaFile 已完成元数据提取的 MediaFile（需含 id、filePath、duration）
     */
    public void generateThumbnail(MediaFile mediaFile) {
        if (mediaFile.getId() == null || mediaFile.getFilePath() == null) {
            return;
        }

        Path outputDir = Paths.get(thumbnailOutputDir);
        Path outputFile = outputDir.resolve(mediaFile.getId() + ".jpg");

        // 已存在则跳过，避免重复处理
        if (Files.exists(outputFile)) {
            log.debug("Thumbnail already exists, skipping: {}", outputFile);
            return;
        }

        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            log.error("Failed to create thumbnail output directory: {}", outputDir, e);
            return;
        }

        String timestamp = resolveTimestamp(mediaFile.getDuration());
        String inputPath = mediaFile.getFilePath();

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-ss", timestamp,           // 快速定位（输入前 -ss 效率更高）
                    "-i", inputPath,
                    "-vframes", "1",            // 只截取一帧
                    "-q:v", "2",               // JPEG 质量（1-31，越小越好）
                    "-vf", "scale=320:-1",      // 缩放到宽 320px，高按比例
                    "-y",                       // 覆盖已有文件（CreatedDirectories 后不会存在，保留作保险）
                    outputFile.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            // 消费输出，防止进程阻塞
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            int exitCode = process.waitFor();

            if (exitCode == 0 && Files.exists(outputFile)) {
                log.info("Thumbnail generated for media [{}]: {}", mediaFile.getId(), outputFile);
            } else {
                log.warn("FFmpeg exited with code {} when generating thumbnail for media [{}]: {}",
                        exitCode, mediaFile.getId(), inputPath);
                // 删除可能产生的不完整文件
                Files.deleteIfExists(outputFile);
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error generating thumbnail for media [{}]: {}", mediaFile.getId(), inputPath, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 根据视频时长计算截图时间戳字符串（HH:MM:SS 格式）
     *
     * - 有时长时取 20%，最少 5 秒
     * - 无时长时固定取 30 秒
     */
    private String resolveTimestamp(Long durationMs) {
        long seekSeconds;
        if (durationMs != null && durationMs > 0) {
            seekSeconds = Math.max(5L, durationMs / 1000 / 5);
        } else {
            seekSeconds = 30L;
        }
        long h = seekSeconds / 3600;
        long m = (seekSeconds % 3600) / 60;
        long s = seekSeconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}
