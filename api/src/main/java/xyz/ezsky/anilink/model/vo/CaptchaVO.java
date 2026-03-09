package xyz.ezsky.anilink.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图形验证码响应 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "图形验证码响应")
public class CaptchaVO {

    @Schema(description = "验证码ID")
    private String captchaId;

    @Schema(description = "Base64 图片内容（不带 data:image 前缀）")
    private String imageBase64;
}
