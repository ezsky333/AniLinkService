package xyz.ezsky.anilink.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemoteAccessCredentialVO {
    private Boolean remoteAccessEnabled;
    private Boolean tokenRequired;
    private String requiredRole;
    private String currentUser;
    private String remoteAccessToken;
}
