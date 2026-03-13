package xyz.ezsky.anilink.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户信息响应VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoVO {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 角色代码列表
     */
    private List<String> roleCodeList;

    /**
     * 是否激活
     */
    private Boolean isActive;

    /**
     * 是否已绑定 Bangumi 账号
     */
    private Boolean bangumiBound;

    /**
     * 已绑定的 Bangumi 用户名
     */
    private String bangumiUsername;

    /**
     * 已绑定的 Bangumi 昵称
     */
    private String bangumiNickname;
}
