package xyz.ezsky.anilink.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端用户信息 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserManageVO {

    private Long id;

    private String username;

    private String email;

    private Boolean isActive;

    private List<String> roleCodeList;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
