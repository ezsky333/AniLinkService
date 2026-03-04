package xyz.ezsky.anilink.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 媒体文件元数据 DTO
 * 
 * 包含从 FFprobe 提取的视频/音频信息和计算的哈希值
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaMetadata {

    // FFprobe 提取的字段
    private Long duration;           // 视频长度（毫秒）
    private String hash;             // 文件前16MB的MD5哈希
    
    // 视频流信息
    private Integer width;            // 分辨率：宽度
    private Integer height;           // 分辨率：高度
    private String aspectRatio;       // 宽高比（如 16:9）
    private String colorDepth;        // 色彩深度（8-bit、10-bit 等）
    private String hdrType;           // HDR 类型（HDR10、HDR10+、Dolby Vision 等）
    private String colorSpace;        // 色彩空间（如 Rec.709、Rec.2020）
    private String colorPrimaries;    // 色彩原色（如 PQ、HLG）
    private Double fps;               // 帧率（如 23.976、24、25、29.97 等）
    private Long videoBitrate;        // 视频码率（bps）
    private String videoCodec;        // 视频编码（如 h264、hevc、av1）

    // 音频流信息
    private String audioCodec;        // 音频编码（如 aac、flac、opus）

    // 容器信息
    private String containerFormat;   // 容器格式（MKV、MP4、AVI 等）

    // 标记该记录是否成功获取元数据
    private boolean success;
    private String errorMessage;      // 获取失败时的错误信息
}
