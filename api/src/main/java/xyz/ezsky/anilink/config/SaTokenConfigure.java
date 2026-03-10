package xyz.ezsky.anilink.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import cn.dev33.satoken.interceptor.SaInterceptor;
import xyz.ezsky.anilink.config.interceptor.RemoteAccessInterceptor;

@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Autowired
    private RemoteAccessInterceptor remoteAccessInterceptor;

    // 注册 Sa-Token 拦截器，打开注解式鉴权功能 
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，打开注解式鉴权功能 
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
        registry.addInterceptor(remoteAccessInterceptor).addPathPatterns("/api/v1/**");
    }
}
