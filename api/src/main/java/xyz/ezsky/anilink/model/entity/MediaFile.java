package xyz.ezsky.anilink.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.sql.Timestamp;

@Entity
@Data
@SuppressWarnings("deprecation")
@Table(name = "media_file", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"library_id", "file_path"})
})
public class MediaFile {
    @Id
    @GenericGenerator(name = "snowflakeId", strategy = "xyz.ezsky.anilink.util.SnowflakeIdGenerator")
    @GeneratedValue(generator = "snowflakeId")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "library_id", nullable = false)
    private MediaLibrary library;

    @Column(name = "file_path", nullable = false, length = 2048)
    private String filePath;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "last_modified", nullable = false)
    private Long lastModified;

    @Column(nullable = false)
    private Long size;

    // 外部接口获取的字段（预留方法）
    @Column(name = "episode_id", length = 255)
    private String episodeId;

    @Column(name = "anime_id")
    private Long animeId;

    @Column(name = "anime_title", length = 500)
    private String animeTitle;

    @Column(name = "episode_title", length = 500)
    private String episodeTitle;

    // FFprobe 提取的字段
    @Column(name = "duration")
    private Long duration;

    @Column(name = "hash", length = 32)
    private String hash;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "aspect_ratio", length = 20)
    private String aspectRatio;

    @Column(name = "color_depth", length = 50)
    private String colorDepth;

    @Column(name = "hdr_type", length = 50)
    private String hdrType;

    @Column(name = "color_space", length = 50)
    private String colorSpace;

    @Column(name = "color_primaries", length = 50)
    private String colorPrimaries;

    @Column(name = "video_bitrate")
    private Long videoBitrate;

    @Column(name = "fps")
    private Double fps;

    @Column(name = "container_format", length = 50)
    private String containerFormat;

    @Column(name = "video_codec", length = 100)
    private String videoCodec;

    @Column(name = "audio_codec", length = 100)
    private String audioCodec;

    @Column(name = "metadata_fetched", nullable = false)
    private Boolean metadataFetched = false;

    // 匹配状态：0=未匹配, 1=已匹配, -1=尝试匹配但无结果
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "match_status", nullable = false)
    private MatchStatus matchStatus = MatchStatus.UNMATCHED;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Timestamp updatedAt;
}
