package xyz.ezsky.anilink.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Timestamp;

@Entity
@Data
@Table(name = "media_subtitle", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"media_file_id", "stream_index"})
})
public class MediaSubtitle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_file_id", nullable = false)
    private MediaFile mediaFile;

    @Column(name = "stream_index", nullable = false)
    private Integer streamIndex;

    @Column(name = "track_name", nullable = false, length = 255)
    private String trackName;

    @Column(name = "language", length = 32)
    private String language;

    @Column(name = "codec_name", length = 64)
    private String codecName;

    @Column(name = "subtitle_format", nullable = false, length = 16)
    private String subtitleFormat;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 2048)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Timestamp updatedAt;
}
