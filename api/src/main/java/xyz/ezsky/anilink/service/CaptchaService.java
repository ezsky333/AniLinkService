package xyz.ezsky.anilink.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.lang.UUID;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.vo.CaptchaVO;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图形验证码服务（内存实现）
 */
@Service
public class CaptchaService {

    private static final int CAPTCHA_EXPIRE_SECONDS = 180;
    private final Map<String, CaptchaRecord> captchaStore = new ConcurrentHashMap<>();

    public CaptchaVO generateCaptcha() {
        cleanupExpired();

        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(130, 42, 4, 20);
        String captchaId = UUID.fastUUID().toString(true);
        String code = captcha.getCode().toLowerCase();

        CaptchaRecord record = new CaptchaRecord(code, LocalDateTime.now().plusSeconds(CAPTCHA_EXPIRE_SECONDS));
        captchaStore.put(captchaId, record);

        return new CaptchaVO(captchaId, captcha.getImageBase64Data());
    }

    public boolean verifyAndConsume(String captchaId, String inputCode) {
        if (captchaId == null || inputCode == null) {
            return false;
        }

        CaptchaRecord record = captchaStore.remove(captchaId);
        if (record == null || LocalDateTime.now().isAfter(record.expireAt)) {
            return false;
        }

        return record.code.equals(inputCode.trim().toLowerCase());
    }

    private void cleanupExpired() {
        LocalDateTime now = LocalDateTime.now();
        captchaStore.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expireAt));
    }

    private record CaptchaRecord(String code, LocalDateTime expireAt) {
    }
}
