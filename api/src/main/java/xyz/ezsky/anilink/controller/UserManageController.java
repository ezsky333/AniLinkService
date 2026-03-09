package xyz.ezsky.anilink.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xyz.ezsky.anilink.model.dto.UpdateUserManageRequest;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.model.vo.RoleOptionVO;
import xyz.ezsky.anilink.model.vo.UserManageVO;
import xyz.ezsky.anilink.service.UserService;

import java.util.List;

/**
 * 管理端用户管理接口
 */
@RestController
@RequestMapping("/api/users")
@SaCheckRole("super-admin")
@Tag(name = "用户管理", description = "管理端用户信息与权限管理")
public class UserManageController {

    @Autowired
    private UserService userService;

    @GetMapping
    @Operation(summary = "分页查询用户", description = "按用户名/邮箱关键字筛选并分页返回用户列表")
    public ApiResponseVO<PageVO<UserManageVO>> getUsers(
            @Parameter(description = "页码，从0开始")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "用户名或邮箱关键字")
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponseVO.success(userService.getUsersPage(page, pageSize, keyword));
    }

    @GetMapping("/roles")
    @Operation(summary = "获取可分配角色", description = "返回当前可用的角色列表")
    public ApiResponseVO<List<RoleOptionVO>> getRoleOptions() {
        return ApiResponseVO.success(userService.getActiveRoleOptions());
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新用户", description = "更新用户基础信息、角色与启用状态")
    public ApiResponseVO<String> updateUser(
            @Parameter(description = "用户ID")
            @PathVariable Long id,
            @RequestBody UpdateUserManageRequest request
    ) {
        Long operatorUserId = Long.valueOf(StpUtil.getLoginId().toString());
        userService.updateUserByAdmin(id, request, operatorUserId);
        return ApiResponseVO.success("ok", "更新用户成功");
    }
}
