package xyz.ezsky.anilink.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;

/**
 * 单页应用（SPA）过滤器
 * 将所有不存在的静态资源请求转发到 index.html，
 * 使前端路由能够处理所有路由请求。
 */
@Component
public class SinglePageApplicationFilter implements Filter {

    private static final UrlPathHelper URL_PATH_HELPER = new UrlPathHelper();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String requestPath = URL_PATH_HELPER.getRequestUri(httpRequest);

        // 排除 API 请求
        if (requestPath.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        // 排除 actuator 端点
        if (requestPath.startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }

        // 排除 swagger 和 springdoc 文档相关路由
        if (requestPath.startsWith("/swagger-ui") ||
            requestPath.startsWith("/v3/api-docs") ||
            requestPath.startsWith("/webjars") ||
            requestPath.startsWith("/swagger-resources")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            // 检查资源是否在 static 目录中存在
            if (isResourceExists(requestPath)) {
                chain.doFilter(request, response);
                return;
            }

            // 如果是文件请求（有扩展名），直接通过，让 Spring 返回 404
            if (hasFileExtension(requestPath)) {
                chain.doFilter(request, response);
                return;
            }

            // 路由路径不存在的资源，转发到 index.html
            httpRequest.getRequestDispatcher("/index.html").forward(request, response);
        } catch (Exception e) {
            chain.doFilter(request, response);
        }
    }

    /**
     * 检查资源是否存在
     */
    private boolean isResourceExists(String requestPath) {
        try {
            // 检查 static 目录
            ClassPathResource resource = new ClassPathResource("static" + requestPath);
            return resource.exists();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断是否是文件请求（有扩展名）
     */
    private boolean hasFileExtension(String requestPath) {
        // 获取最后一个 / 之后的内容
        int lastSlash = requestPath.lastIndexOf('/');
        String lastPart = lastSlash >= 0 ? requestPath.substring(lastSlash) : requestPath;

        // 检查是否包含点号（文件扩展名）
        return lastPart.contains(".");
    }
}

