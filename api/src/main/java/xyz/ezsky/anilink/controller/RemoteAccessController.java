package xyz.ezsky.anilink.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.RemoteAccessCredentialVO;
import xyz.ezsky.anilink.model.vo.UserInfoVO;
import xyz.ezsky.anilink.service.SiteConfigService;
import xyz.ezsky.anilink.service.UserService;

@RestController
@RequestMapping("/api/remote-access/")
@Tag(name = "远程访问", description = "远程访问配置和当前用户密钥")
public class RemoteAccessController {

    @Autowired
    private SiteConfigService siteConfigService;

    @Autowired
    private UserService userService;

    @GetMapping("credential")
    @Operation(summary = "获取当前用户远程访问凭证", description = "返回远程访问开关、授权要求及当前登录用户的远程访问密钥")
    public ApiResponseVO<RemoteAccessCredentialVO> getCurrentUserCredential() {
        Long userId = null;
        String currentUser = "游客";
        String token = null;

        if (StpUtil.isLogin()) {
            userId = Long.valueOf(StpUtil.getLoginId().toString());
            UserInfoVO userInfo = userService.getUserInfoById(userId);
            if (userInfo != null) {
                currentUser = userInfo.getUsername();
            }
        }

        if (siteConfigService.isRemoteAccessTokenRequired()) {
            if (userId == null) {
                return ApiResponseVO.fail(401, "当前配置要求授权，请先登录");
            }
            token = userService.getOrCreateRemoteAccessToken(userId);
        }

        RemoteAccessCredentialVO vo = new RemoteAccessCredentialVO(
                siteConfigService.isRemoteAccessEnabled(),
                siteConfigService.isRemoteAccessTokenRequired(),
                siteConfigService.getRemoteAccessRequiredRole(),
                currentUser,
                token
        );
        return ApiResponseVO.success(vo, "获取成功");
    }

    @PostMapping("credential/regenerate")
    @SaCheckLogin
    @Operation(summary = "重置当前用户远程访问密钥", description = "重置并返回新的远程访问密钥")
    public ApiResponseVO<String> regenerateCurrentUserToken() {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        return ApiResponseVO.success(userService.regenerateRemoteAccessToken(userId), "已重置远程访问密钥");
    }
}
