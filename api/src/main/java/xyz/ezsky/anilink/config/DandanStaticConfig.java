package xyz.ezsky.anilink.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class DandanStaticConfig implements WebMvcConfigurer {

    @Value("${dandan.image.dir:/data/dandan-images}")
    private String imageDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /images/dandan/** 映射到文件系统目录
        String path = "file:" + (imageDir.endsWith("/") ? imageDir : imageDir + "/");
        registry.addResourceHandler("/images/dandan/**")
                .addResourceLocations(path)
                .setCachePeriod(3600);
    }
}
