package xyz.ezsky.anilink.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaSubtitleAdminDTO {
    private Long id;
    private Long mediaFileId;
    private String trackName;
    private String language;
    private String subtitleFormat;
    private String fileName;
    private Long fileSize;
    private Long timeOffset;
    private Boolean isExternal;
    private String sourceType;
    private String videoFileName;
    private String videoFilePath;
    private Long animeId;
    private String animeTitle;
    private String episodeTitle;
    private Long libraryId;
    private String libraryName;
}
