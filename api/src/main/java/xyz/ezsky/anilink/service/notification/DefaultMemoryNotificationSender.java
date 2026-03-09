package xyz.ezsky.anilink.service.notification;

import xyz.ezsky.anilink.model.vo.MessageVO;
import org.springframework.stereotype.Component;
import lombok.extern.log4j.Log4j2;

/**
 * 默认的内存通知实现
 * 简单记录日志，作为内置的通知实现
 */
@Component
@Log4j2
public class DefaultMemoryNotificationSender implements NotificationSender {
    
    @Override
    public String getName() {
        return "default-memory";
    }
    
    @Override
    public boolean send(Long userId, MessageVO message) {
        try {
            log.info("Send notification to user {}: type={}, title={}, animeId={}", 
                userId, message.getType(), message.getTitle(), message.getAnimeId());
            return true;
        } catch (Exception e) {
            log.error("Failed to send notification to user: {}", userId, e);
            return false;
        }
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
}
