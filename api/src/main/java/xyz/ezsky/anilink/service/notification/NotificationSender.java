package xyz.ezsky.anilink.service.notification;

import xyz.ezsky.anilink.model.vo.MessageVO;

/**
 * 消息通知发送者接口
 * 定义抽象的消息发送能力，支持插件化的扩展（如企微、钉钉、邮件等）
 */
public interface NotificationSender {
    
    /**
     * 获取该通知发送者的名称/类型
     */
    String getName();
    
    /**
     * 发送消息通知
     * 
     * @param userId 用户ID
     * @param message 消息VO
     * @return 是否发送成功
     */
    boolean send(Long userId, MessageVO message);
    
    /**
     * 检查该发送者是否启用
     */
    boolean isEnabled();
}
