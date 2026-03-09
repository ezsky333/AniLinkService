package xyz.ezsky.anilink.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import xyz.ezsky.anilink.model.entity.Message;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    
    /**
     * 根据用户ID查询消息（分页，按创建时间倒序）
     */
    Page<Message> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    /**
     * 根据用户ID查询未读消息
     */
    List<Message> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId);
    
    /**
     * 根据用户ID查询所有消息
     */
    List<Message> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    /**
     * 统计用户的未读消息数
     */
    long countByUserIdAndIsReadFalse(Long userId);
    
    /**
     * 查询特定类型的消息
     */
    Page<Message> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, String type, Pageable pageable);
    
    /**
     * 删除用户的所有消息
     */
    void deleteByUserId(Long userId);
}
