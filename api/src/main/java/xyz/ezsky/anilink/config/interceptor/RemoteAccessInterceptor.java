package xyz.ezsky.anilink.config.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import xyz.ezsky.anilink.model.entity.User;
import xyz.ezsky.anilink.repository.UserRepository;
import xyz.ezsky.anilink.service.RoleInterfaceImpl;
import xyz.ezsky.anilink.service.SiteConfigService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class RemoteAccessInterceptor implements HandlerInterceptor {

    private static final Map<String, Integer> ROLE_LEVEL = Map.of(
            "user", 1,
            "admin", 2,
            "super-admin", 3
    );

    @Autowired
    private SiteConfigService siteConfigService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleInterfaceImpl roleInterfaceImpl;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!siteConfigService.isRemoteAccessEnabled()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Remote access is disabled");
            return false;
        }

        if (!siteConfigService.isRemoteAccessTokenRequired()) {
            return true;
        }

        String token = resolveRemoteAccessToken(request);
        if (token == null || token.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Remote access token is required");
            return false;
        }

        Optional<User> userOpt = userRepository.findByRemoteAccessToken(token.trim());
        if (userOpt.isEmpty() || !Boolean.TRUE.equals(userOpt.get().getIsActive())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid remote access token");
            return false;
        }

        String requiredRole = siteConfigService.getRemoteAccessRequiredRole();
        List<String> roleCodes = roleInterfaceImpl.getRoleList(userOpt.get().getId(), "login");
        if (!hasSufficientRole(roleCodes, requiredRole)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Insufficient role for remote access");
            return false;
        }

        request.setAttribute("remoteAccessUserId", userOpt.get().getId());
        return true;
    }

    private String resolveRemoteAccessToken(HttpServletRequest request) {
        String token = request.getHeader("X-Remote-Token");
        if (token != null && !token.isBlank()) {
            return token;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        token = request.getParameter("remoteAccessToken");
        if (token != null && !token.isBlank()) {
            return token;
        }

        token = request.getParameter("token");
        if (token != null && !token.isBlank()) {
            return token;
        }

        return null;
    }

    private boolean hasSufficientRole(List<String> userRoles, String requiredRole) {
        if (userRoles == null || userRoles.isEmpty()) {
            return false;
        }

        String normalizedRequiredRole = requiredRole == null || requiredRole.isBlank() ? "user" : requiredRole.trim();
        Integer requiredLevel = ROLE_LEVEL.get(normalizedRequiredRole);

        // 未知角色配置时回退为精确匹配
        if (requiredLevel == null) {
            return userRoles.contains(normalizedRequiredRole);
        }

        for (String role : userRoles) {
            Integer level = ROLE_LEVEL.get(role);
            if (level != null && level >= requiredLevel) {
                return true;
            }
            if (normalizedRequiredRole.equals(role)) {
                return true;
            }
        }
        return false;
    }
}
