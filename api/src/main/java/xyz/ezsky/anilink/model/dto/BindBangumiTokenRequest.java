package xyz.ezsky.anilink.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BindBangumiTokenRequest {
    private String accessToken;
}