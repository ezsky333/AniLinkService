package xyz.ezsky.anilink.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaSubtitleDTO {
    private Long id;
    private Long mediaFileId;
    private Integer streamIndex;
    private String trackName;
    private String language;
    private String codecName;
    private String subtitleFormat;
    private String fileName;
    private String filePath;
    private Long fileSize;
}
