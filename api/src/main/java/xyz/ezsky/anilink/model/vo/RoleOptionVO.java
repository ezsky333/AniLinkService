package xyz.ezsky.anilink.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色选项 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleOptionVO {

    private Long id;

    private String roleCode;

    private String roleName;

    private String description;
}
