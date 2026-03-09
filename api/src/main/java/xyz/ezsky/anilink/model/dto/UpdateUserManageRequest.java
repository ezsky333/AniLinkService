package xyz.ezsky.anilink.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 管理端更新用户请求
 */
@Data
public class UpdateUserManageRequest {

    private String username;

    private String email;

    private Boolean isActive;

    private List<String> roleCodeList;
}
