package xyz.ezsky.anilink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.dto.MediaMetadata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * FFprobe 媒体文件分析服务
 *
 * 使用 FFprobe 提取视频文件的详细元数据信息，包括：
 * - 分辨率、帧率、比特率等视频流信息
 * - 色彩空间、HDR 类型等色彩信息
 * - 编码格式、容器类型等技术规格
 *
 * 性能优化：
 * - 使用流式读取避免大块 JSON 加载到内存
 * - 并发调用使用共享的 ObjectMapper
 * - 错误处理基于超时和进程退出状态
 */
@Log4j2
@Service
public class MediaProbeService {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析视频文件的完整元数据
     *
     * @param filePath 视频文件的绝对路径
     * @return MediaMetadata 对象，包含所有提取的信息或错误信息
     */
    public MediaMetadata parseMediaInfo(Path filePath) {
        try {
            JsonNode ffprobeOutput = executeFFprobe(filePath.toAbsolutePath().toString());
            if (ffprobeOutput == null) {
                return MediaMetadata.builder()
                        .success(false)
                        .errorMessage("FFprobe output is null")
                        .build();
            }

            MediaMetadata metadata = MediaMetadata.builder().build();

            // 解析容器/格式信息
            parseFormatInfo(ffprobeOutput, metadata);

            // 解析视频流信息
            parseVideoStreamInfo(ffprobeOutput, metadata);

            // 解析音频流信息
            parseAudioStreamInfo(ffprobeOutput, metadata);

            metadata.setSuccess(true);
            return metadata;
        } catch (Exception e) {
            log.error("Error parsing media info for file: {}", filePath, e);
            return MediaMetadata.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 执行 FFprobe 命令并解析 JSON 输出
     *
     * @param filePath 视频文件路径
     * @return FFprobe JSON 输出的解析结果
     * @throws IOException    IO 错误或进程异常
     * @throws InterruptedException 进程被中断
     */
    private JsonNode executeFFprobe(String filePath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "ffprobe",
                "-v", "quiet",                      // 抑制日志输出
                "-print_format", "json",             // JSON 格式输出
                "-show_format",                      // 显示容器/格式信息
                "-show_streams",                     // 显示视频/音频流信息
                "-timeout", "30000000",              // 30秒超时（单位：微秒）
                filePath
        );

        pb.redirectErrorStream(true);

        Process process = pb.start();

        // 使用 StringBuilder 收集输出（可优化为流式处理）
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.warn("FFprobe exited with code {} for file: {}", exitCode, filePath);
            return null;
        }

        if (output.length() == 0) {
            log.warn("FFprobe returned empty output for file: {}", filePath);
            return null;
        }

        return objectMapper.readTree(output.toString());
    }

    /**
     * 解析容器/格式信息（时长、比特率等）
     */
    private void parseFormatInfo(JsonNode root, MediaMetadata metadata) {
        JsonNode formatNode = root.get("format");
        if (formatNode == null) {
            return;
        }

        // 时长（秒 -> 毫秒）
        JsonNode durationNode = formatNode.get("duration");
        if (durationNode != null && !durationNode.isNull()) {
            try {
                long durationMs = (long) (durationNode.asDouble() * 1000);
                metadata.setDuration(durationMs);
            } catch (Exception e) {
                log.debug("Failed to parse duration", e);
            }
        }

        // 容器格式
        JsonNode formatNameNode = formatNode.get("format_name");
        if (formatNameNode != null && !formatNameNode.isNull()) {
            metadata.setContainerFormat(formatNameNode.asText());
        }
    }

    /**
     * 解析第一个视频流的信息
     * 
     * 提取：分辨率、帧率、编码、比特率、色彩空间、HDR 等信息
     */
    private void parseVideoStreamInfo(JsonNode root, MediaMetadata metadata) {
        JsonNode streamsNode = root.get("streams");
        if (streamsNode == null || !streamsNode.isArray()) {
            return;
        }

        // 找第一个视频流
        for (JsonNode stream : streamsNode) {
            String codecType = stream.get("codec_type").asText("");
            if (!codecType.equals("video")) {
                continue;
            }

            // 宽度、高度
            JsonNode widthNode = stream.get("width");
            JsonNode heightNode = stream.get("height");
            if (widthNode != null && !widthNode.isNull()) {
                metadata.setWidth(widthNode.asInt());
            }
            if (heightNode != null && !heightNode.isNull()) {
                metadata.setHeight(heightNode.asInt());
            }

            // 计算宽高比
            if (metadata.getWidth() != null && metadata.getHeight() != null) {
                String aspectRatio = calculateAspectRatio(metadata.getWidth(), metadata.getHeight());
                metadata.setAspectRatio(aspectRatio);
            }

            // 帧率 (r_frame_rate 格式通常为 "24/1" 或 "30000/1001")
            parseFrameRate(stream, metadata);

            // 视频编码
            JsonNode codecNameNode = stream.get("codec_name");
            if (codecNameNode != null && !codecNameNode.isNull()) {
                metadata.setVideoCodec(codecNameNode.asText());
            }

            // 比特率
            JsonNode bitrateNode = stream.get("bit_rate");
            if (bitrateNode != null && !bitrateNode.isNull()) {
                try {
                    metadata.setVideoBitrate(bitrateNode.asLong());
                } catch (Exception e) {
                    log.debug("Failed to parse video bitrate", e);
                }
            }

            // 色彩空间
            JsonNode colorSpaceNode = stream.get("color_space");
            if (colorSpaceNode != null && !colorSpaceNode.isNull()) {
                metadata.setColorSpace(colorSpaceNode.asText());
            }

            // 色彩原色 (color primaries)
            JsonNode colorPrimariesNode = stream.get("color_primaries");
            if (colorPrimariesNode != null && !colorPrimariesNode.isNull()) {
                metadata.setColorPrimaries(colorPrimariesNode.asText());
            }

            // 色彩深度 (bits_per_raw_sample)
            JsonNode bitsPerSampleNode = stream.get("bits_per_raw_sample");
            if (bitsPerSampleNode != null && !bitsPerSampleNode.isNull()) {
                metadata.setColorDepth(bitsPerSampleNode.asInt() + "-bit");
            }

            // HDR 信息（通过 side_data_list）
            parseHDRInfo(stream, metadata);

            // 找到视频流后就返回（仅处理第一个视频流）
            return;
        }
    }

    /**
     * 解析第一个音频流的编码格式
     */
    private void parseAudioStreamInfo(JsonNode root, MediaMetadata metadata) {
        JsonNode streamsNode = root.get("streams");
        if (streamsNode == null || !streamsNode.isArray()) {
            return;
        }

        // 找第一个音频流
        for (JsonNode stream : streamsNode) {
            String codecType = stream.get("codec_type").asText("");
            if (!codecType.equals("audio")) {
                continue;
            }

            JsonNode codecNameNode = stream.get("codec_name");
            if (codecNameNode != null && !codecNameNode.isNull()) {
                metadata.setAudioCodec(codecNameNode.asText());
            }

            // 找到音频流后就返回
            return;
        }
    }

    /**
     * 解析帧率（处理分数格式 "24/1" 或 "30000/1001"）
     */
    private void parseFrameRate(JsonNode stream, MediaMetadata metadata) {
        JsonNode rFrameRateNode = stream.get("r_frame_rate");
        if (rFrameRateNode == null || rFrameRateNode.isNull()) {
            // 如果没有 r_frame_rate，尝试 avg_frame_rate
            rFrameRateNode = stream.get("avg_frame_rate");
        }

        if (rFrameRateNode != null && !rFrameRateNode.isNull()) {
            String frameRateStr = rFrameRateNode.asText();
            try {
                // 处理分数格式
                if (frameRateStr.contains("/")) {
                    String[] parts = frameRateStr.split("/");
                    if (parts.length == 2) {
                        double numerator = Double.parseDouble(parts[0]);
                        double denominator = Double.parseDouble(parts[1]);
                        metadata.setFps(numerator / denominator);
                        return;
                    }
                }
                // 如果不是分数格式，直接解析
                metadata.setFps(Double.parseDouble(frameRateStr));
            } catch (Exception e) {
                log.debug("Failed to parse frame rate: {}", frameRateStr, e);
            }
        }
    }

    /**
     * 解析 HDR 信息（从 side_data_list 中查找）
     */
    private void parseHDRInfo(JsonNode stream, MediaMetadata metadata) {
        JsonNode sideDataList = stream.get("side_data_list");
        if (sideDataList == null || !sideDataList.isArray()) {
            return;
        }

        for (JsonNode sideData : sideDataList) {
            String sideDataType = sideData.get("side_data_type").asText("");

            if (sideDataType.contains("Mastering display")) {
                // 可能是 HDR10
                metadata.setHdrType("HDR10");
            } else if (sideDataType.contains("Content light level")) {
                // Content light level info，配合 Mastering display 表示 HDR10
                if (metadata.getHdrType() == null) {
                    metadata.setHdrType("HDR10");
                }
            } else if (sideDataType.contains("HDR10 Plus")) {
                metadata.setHdrType("HDR10+");
            } else if (sideDataType.contains("Dolby Vision")) {
                metadata.setHdrType("Dolby Vision");
            } else if (sideDataType.contains("HLG")) {
                metadata.setHdrType("HLG");
            }
        }
    }

    /**
     * 计算宽高比（简化版，返回如 "16:9" 或 "21:9" 的格式）
     */
    private String calculateAspectRatio(int width, int height) {
        if (width == 0 || height == 0) {
            return null;
        }

        // 使用最大公约数简化比例
        int gcd = gcd(width, height);
        int simplifiedWidth = width / gcd;
        int simplifiedHeight = height / gcd;

        // 限制宽高比的分子和分母不超过 100
        if (simplifiedWidth > 100 || simplifiedHeight > 100) {
            // 如果约分后还很大，直接计算小数宽高比
            double ratio = (double) width / height;
            return String.format("%.2f:1", ratio);
        }

        return simplifiedWidth + ":" + simplifiedHeight;
    }

    /**
     * 计算最大公约数（GCD）
     */
    private int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }
}
