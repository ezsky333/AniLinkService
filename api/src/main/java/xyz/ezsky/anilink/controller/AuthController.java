package xyz.ezsky.anilink.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import xyz.ezsky.anilink.model.dto.LoginRequest;
import xyz.ezsky.anilink.model.dto.RegisterRequest;
import xyz.ezsky.anilink.model.dto.SendRegisterEmailCodeRequest;
import xyz.ezsky.anilink.model.entity.User;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.CaptchaVO;
import xyz.ezsky.anilink.model.vo.UserInfoVO;
import xyz.ezsky.anilink.service.AuthVerificationService;
import xyz.ezsky.anilink.service.CaptchaService;
import xyz.ezsky.anilink.service.UserService;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth/")
@Tag(name = "认证服务", description = "进行认证操作的相关接口")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private AuthVerificationService authVerificationService;

    @PostMapping("login")
    @Operation(summary = "登录", description = "登录并返回token")
    public ApiResponseVO<SaTokenInfo> doLogin(@RequestBody LoginRequest loginRequest) {
        String account = loginRequest.getAccountOrUsername();
        if (account == null || account.isBlank()) {
            return ApiResponseVO.fail(400, "请输入用户名或邮箱");
        }
        // 验证用户登录
        Optional<User> userOpt = userService.validateLogin(account, loginRequest.getPassword());

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // 使用用户ID登录
            StpUtil.login(user.getId());
            return ApiResponseVO.success(StpUtil.getTokenInfo(), "登录成功");
        }

        return ApiResponseVO.fail(500, "用户名或密码错误");
    }

    @GetMapping("captcha")
    @Operation(summary = "获取图形验证码", description = "用于发送邮箱验证码前的人机校验")
    public ApiResponseVO<CaptchaVO> getCaptcha() {
        return ApiResponseVO.success(captchaService.generateCaptcha(), "获取成功");
    }

    @PostMapping("send-register-email-code")
    @Operation(summary = "发送注册邮箱验证码", description = "需先通过图形验证码")
    public ApiResponseVO<String> sendRegisterEmailCode(
            @RequestBody SendRegisterEmailCodeRequest request,
            HttpServletRequest httpRequest
    ) {
        authVerificationService.sendRegisterEmailCode(
                request.getEmail(),
                request.getCaptchaId(),
                request.getCaptchaCode(),
                getClientIp(httpRequest)
        );
        return ApiResponseVO.success("ok", "验证码已发送");
    }

    @PostMapping("register")
    @Operation(summary = "用户注册", description = "通过邮箱验证码完成注册，默认分配 user 角色")
    public ApiResponseVO<String> register(
            @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return ApiResponseVO.fail(400, "用户名不能为空");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            return ApiResponseVO.fail(400, "邮箱不能为空");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            return ApiResponseVO.fail(400, "密码不能为空");
        }
        if (request.getEmailCode() == null || request.getEmailCode().isBlank()) {
            return ApiResponseVO.fail(400, "邮箱验证码不能为空");
        }

        authVerificationService.enforceRegisterIpLimit(getClientIp(httpRequest));
        authVerificationService.verifyRegisterCode(request.getEmail(), request.getEmailCode());
        userService.registerUser(request.getUsername(), request.getPassword(), request.getEmail());

        return ApiResponseVO.success("ok", "注册成功");
    }

    @PostMapping("currentUser")
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的详细信息")
    public ApiResponseVO<UserInfoVO> getCurrentUserInfo() {
        // 获取当前登录用户的ID
        Object loginId = StpUtil.getLoginId();

        if (loginId == null) {
            return ApiResponseVO.fail(401, "未登录");
        }

        Long userId = Long.valueOf(loginId.toString());
        UserInfoVO userInfo = userService.getUserInfoById(userId);

        if (userInfo == null) {
            return ApiResponseVO.fail(404, "用户不存在");
        }

        return ApiResponseVO.success(userInfo, "获取成功");
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

}

