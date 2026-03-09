package xyz.ezsky.anilink.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * 邮件发送服务
 */
@Service
public class EmailService {

    private final SiteConfigService siteConfigService;

    public EmailService(SiteConfigService siteConfigService) {
        this.siteConfigService = siteConfigService;
    }

    public void sendRegisterCode(String toEmail, String code, int expireSeconds) {
        String subject = "AniLink 注册验证码";
        String html = "<div style=\"font-family:Arial,sans-serif;line-height:1.6\">"
                + "<h3>邮箱验证码</h3>"
                + "<p>您的验证码是：<b style=\"font-size:18px\">" + code + "</b></p>"
                + "<p>验证码将在 " + (expireSeconds / 60) + " 分钟后失效。</p>"
                + "<p>如果不是本人操作，请忽略本邮件。</p>"
                + "</div>";

        sendHtmlEmail(toEmail, subject, html);
    }

    public void sendTestEmail(String toEmail) {
        String subject = "AniLink SMTP 测试邮件";
        String html = "<div style=\"font-family:Arial,sans-serif;line-height:1.6\">"
                + "<h3>SMTP 测试成功</h3>"
                + "<p>这是一封来自 AniLink 的测试邮件。</p>"
                + "<p>如果你收到此邮件，说明当前 SMTP 配置可用。</p>"
                + "</div>";
        sendHtmlEmail(toEmail, subject, html);
    }

    private void sendHtmlEmail(String toEmail, String subject, String html) {
        SiteConfigService.SmtpSettings smtp = siteConfigService.getSmtpSettings();
        if (!smtp.isConfigured()) {
            throw new RuntimeException("SMTP 未配置完整，无法发送邮件");
        }

        try {
            JavaMailSender sender = buildSender(smtp);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(smtp.getFromEmail(), smtp.getFromName());
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new RuntimeException("邮件发送失败", e);
        }
    }

    private JavaMailSender buildSender(SiteConfigService.SmtpSettings smtp) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtp.getHost());
        sender.setPort(smtp.getPort());
        sender.setUsername(smtp.getUsername());
        sender.setPassword(smtp.getPassword());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(smtp.getStarttlsEnabled()));
        props.put("mail.smtp.ssl.enable", String.valueOf(smtp.getSslEnabled()));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return sender;
    }
}
