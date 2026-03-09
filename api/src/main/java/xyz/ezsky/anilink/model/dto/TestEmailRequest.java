package xyz.ezsky.anilink.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 测试邮件请求 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "测试邮件请求")
public class TestEmailRequest {

    @Schema(description = "测试收件邮箱", example = "tester@example.com")
    private String toEmail;
}
