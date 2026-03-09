package xyz.ezsky.anilink.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册请求 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户注册请求")
public class RegisterRequest {

    @Schema(description = "用户名", example = "alice")
    private String username;

    @Schema(description = "邮箱", example = "alice@example.com")
    private String email;

    @Schema(description = "密码", example = "P@ssw0rd")
    private String password;

    @Schema(description = "邮箱验证码", example = "123456")
    private String emailCode;
}
