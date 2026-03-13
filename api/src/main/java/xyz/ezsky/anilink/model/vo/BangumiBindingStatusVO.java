package xyz.ezsky.anilink.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BangumiBindingStatusVO {
    private Boolean bound;
    private Boolean tokenValid;
    private Boolean tokenExpired;
    private Long bangumiUserId;
    private String bangumiUsername;
    private String bangumiNickname;
    private Object profile;
    private String statusMessage;
}