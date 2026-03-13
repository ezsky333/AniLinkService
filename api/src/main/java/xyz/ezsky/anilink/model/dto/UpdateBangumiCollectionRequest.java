package xyz.ezsky.anilink.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBangumiCollectionRequest {
    private Integer type;
    private Integer rate;
    private String comment;

    @JsonProperty("private")
    private Boolean privateCollection;
}