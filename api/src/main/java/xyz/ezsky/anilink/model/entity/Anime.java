package xyz.ezsky.anilink.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "anime", indexes = {
    @Index(name = "idx_anime_animeid", columnList = "anime_id")
})
@Data
public class Anime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anime_id", unique = true)
    private Long animeId;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "alt_title", length = 1000)
    private String altTitle;

    @Column(name = "release_year", length = 100)
    private String year;

    @Column(name = "episodes")
    private Integer episodes;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "local_image_path", length = 2000)
    private String localImagePath;

    @Column(name = "type", length = 100)
    private String type;

    @Column(name = "duration", length = 50)
    private String duration;

    @Lob
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Lob
    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    @Column(name = "rating")
    private Double rating;

    @Lob
    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
