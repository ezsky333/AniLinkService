package xyz.ezsky.anilink.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送注册邮箱验证码请求 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "发送注册邮箱验证码请求")
public class SendRegisterEmailCodeRequest {

    @Schema(description = "邮箱", example = "alice@example.com")
    private String email;

    @Schema(description = "图形验证码ID")
    private String captchaId;

    @Schema(description = "图形验证码", example = "a7k2")
    private String captchaCode;
}
