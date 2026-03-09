package xyz.ezsky.anilink.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.SecureUtil;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 注册验证服务（邮箱验证码、防刷限制）
 */
@Service
public class AuthVerificationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final String CODE_SALT = "ani-link-email-code-v1";

    private final SiteConfigService siteConfigService;
    private final CaptchaService captchaService;
    private final EmailService emailService;
    private final UserService userService;

    private final Map<String, EmailCodeRecord> emailCodeStore = new ConcurrentHashMap<>();
    private final Map<String, DailyCounter> emailDailyCounter = new ConcurrentHashMap<>();
    private final Map<String, DailyCounter> ipDailySendCounter = new ConcurrentHashMap<>();
    private final Map<String, DailyCounter> ipDailyRegisterCounter = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> emailCooldown = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> ipCooldown = new ConcurrentHashMap<>();

    public AuthVerificationService(
            SiteConfigService siteConfigService,
            CaptchaService captchaService,
            EmailService emailService,
            UserService userService
    ) {
        this.siteConfigService = siteConfigService;
        this.captchaService = captchaService;
        this.emailService = emailService;
        this.userService = userService;
    }

    public void sendRegisterEmailCode(String email, String captchaId, String captchaCode, String ip) {
        if (!siteConfigService.isRegisterEnabled()) {
            throw new RuntimeException("站点当前未开放注册");
        }
        if (!isValidEmail(email)) {
            throw new RuntimeException("邮箱格式不正确");
        }

        if (!captchaService.verifyAndConsume(captchaId, captchaCode)) {
            throw new RuntimeException("图形验证码错误或已过期");
        }

        Optional<User> existingUser = userService.findByEmail(email);
        if (existingUser.isPresent()) {
            throw new RuntimeException("该邮箱已被注册");
        }

        int cooldownSeconds = siteConfigService.getMailSendCooldownSeconds();
        int dailyLimitPerIp = siteConfigService.getMailSendDailyLimitPerIp();
        int dailyLimitPerEmail = siteConfigService.getMailSendDailyLimitPerEmail();
        int expireSeconds = siteConfigService.getMailCodeExpireSeconds();

        enforceCooldown(ipCooldown, normalizeIp(ip), cooldownSeconds, "请求过于频繁，请稍后再试");
        enforceCooldown(emailCooldown, email.toLowerCase(), cooldownSeconds, "验证码发送过于频繁，请稍后再试");

        increaseDailyCounter(ipDailySendCounter, normalizeIp(ip), dailyLimitPerIp, "当前IP今日发送次数已达上限");
        increaseDailyCounter(emailDailyCounter, email.toLowerCase(), dailyLimitPerEmail, "当前邮箱今日发送次数已达上限");

        String code = RandomUtil.randomNumbers(6);
        emailService.sendRegisterCode(email, code, expireSeconds);

        EmailCodeRecord record = new EmailCodeRecord(
                hashCode(email, code),
                LocalDateTime.now().plusSeconds(expireSeconds),
                0,
                null
        );
        emailCodeStore.put(buildRegisterEmailKey(email), record);
    }

    public void verifyRegisterCode(String email, String code) {
        String key = buildRegisterEmailKey(email);
        EmailCodeRecord record = emailCodeStore.get(key);
        if (record == null) {
            throw new RuntimeException("验证码不存在或已失效");
        }
        if (record.lockUntil != null && LocalDateTime.now().isBefore(record.lockUntil)) {
            throw new RuntimeException("验证码校验失败次数过多，请稍后再试");
        }
        if (LocalDateTime.now().isAfter(record.expireAt)) {
            emailCodeStore.remove(key);
            throw new RuntimeException("验证码已过期");
        }

        String expectedHash = hashCode(email, code);
        if (!expectedHash.equals(record.codeHash)) {
            int nextFail = record.failCount + 1;
            LocalDateTime lockUntil = nextFail >= 5 ? LocalDateTime.now().plusMinutes(15) : null;
            emailCodeStore.put(key, new EmailCodeRecord(record.codeHash, record.expireAt, nextFail, lockUntil));
            throw new RuntimeException("验证码错误");
        }

        // 一次性消费
        emailCodeStore.remove(key);
    }

    public void enforceRegisterIpLimit(String ip) {
        if (!siteConfigService.isRegisterEnabled()) {
            throw new RuntimeException("站点当前未开放注册");
        }
        increaseDailyCounter(
                ipDailyRegisterCounter,
                normalizeIp(ip),
                siteConfigService.getRegisterDailyLimitPerIp(),
                "当前IP今日注册次数已达上限"
        );
    }

    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    private String hashCode(String email, String code) {
        return SecureUtil.sha256(email.toLowerCase() + ":" + code + ":" + CODE_SALT);
    }

    private String buildRegisterEmailKey(String email) {
        return "register:" + email.toLowerCase();
    }

    private String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        return ip.trim();
    }

    private void enforceCooldown(Map<String, LocalDateTime> store, String key, int cooldownSeconds, String msg) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime allowedTime = store.get(key);
        if (allowedTime != null && now.isBefore(allowedTime)) {
            throw new RuntimeException(msg);
        }
        store.put(key, now.plusSeconds(cooldownSeconds));
    }

    private void increaseDailyCounter(Map<String, DailyCounter> store, String key, int limit, String msg) {
        LocalDate today = LocalDate.now();
        DailyCounter counter = store.get(key);
        if (counter == null || !today.equals(counter.day)) {
            counter = new DailyCounter(today, 0);
        }
        if (counter.count >= limit) {
            throw new RuntimeException(msg);
        }
        store.put(key, new DailyCounter(today, counter.count + 1));
    }

    private record EmailCodeRecord(
            String codeHash,
            LocalDateTime expireAt,
            int failCount,
            LocalDateTime lockUntil
    ) {
    }

    private record DailyCounter(LocalDate day, int count) {
    }
}
