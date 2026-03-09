package xyz.ezsky.anilink.service.notification;

import xyz.ezsky.anilink.model.vo.MessageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通知发送管理器
 * 管理多个通知发送者，并支持动态注册新的发送者
 */
@Service
@Log4j2
public class NotificationSenderManager {
    
    /**
     * 注册的通知发送者映射
     */
    private final Map<String, NotificationSender> senders = new ConcurrentHashMap<>();
    
    @Autowired
    private DefaultMemoryNotificationSender defaultMemoryNotificationSender;
    
    /**
     * 初始化时注册默认的通知发送者
     */
    public NotificationSenderManager() {
    }
    
    /**
     * 初始化，注册默认的通知发送者
     */
    @Autowired
    public void init(DefaultMemoryNotificationSender defaultMemoryNotificationSender) {
        registerSender(defaultMemoryNotificationSender);
    }
    
    /**
     * 注册一个新的通知发送者
     * 
     * @param sender 通知发送者
     */
    public void registerSender(NotificationSender sender) {
        if (sender != null) {
            senders.put(sender.getName(), sender);
            log.info("Registered notification sender: {}", sender.getName());
        }
    }
    
    /**
     * 注销一个通知发送者
     * 
     * @param senderName 发送者名称
     */
    public void unregisterSender(String senderName) {
        senders.remove(senderName);
        log.info("Unregistered notification sender: {}", senderName);
    }
    
    /**
     * 获取已注册的所有通知发送者
     */
    public List<NotificationSender> getAllSenders() {
        return new ArrayList<>(senders.values());
    }
    
    /**
     * 获取已启用的通知发送者
     */
    public List<NotificationSender> getEnabledSenders() {
        return senders.values().stream()
            .filter(NotificationSender::isEnabled)
            .toList();
    }
    
    /**
     * 通过所有已启用的通知发送者发送消息
     * 
     * @param userId 用户ID
     * @param message 消息
     * @return 是否至少有一个发送者成功发送
     */
    public boolean sendNotification(Long userId, MessageVO message) {
        List<NotificationSender> enabledSenders = getEnabledSenders();
        if (enabledSenders.isEmpty()) {
            log.warn("No enabled notification sender available");
            return false;
        }
        
        boolean anySuccess = false;
        for (NotificationSender sender : enabledSenders) {
            try {
                if (sender.send(userId, message)) {
                    anySuccess = true;
                }
            } catch (Exception e) {
                log.error("Error sending notification with sender {}: {}", sender.getName(), e.getMessage(), e);
            }
        }
        return anySuccess;
    }
    
    /**
     * 使用指定的通知发送者发送消息
     * 
     * @param senderName 发送者名称
     * @param userId 用户ID
     * @param message 消息
     * @return 是否发送成功
     */
    public boolean sendNotificationWithSender(String senderName, Long userId, MessageVO message) {
        NotificationSender sender = senders.get(senderName);
        if (sender == null) {
            log.warn("Notification sender not found: {}", senderName);
            return false;
        }
        
        if (!sender.isEnabled()) {
            log.warn("Notification sender is disabled: {}", senderName);
            return false;
        }
        
        try {
            return sender.send(userId, message);
        } catch (Exception e) {
            log.error("Error sending notification with sender {}: {}", senderName, e.getMessage(), e);
            return false;
        }
    }
}
