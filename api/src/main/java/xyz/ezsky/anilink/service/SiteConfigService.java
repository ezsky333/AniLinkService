package xyz.ezsky.anilink.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.vo.SiteConfigVO;
import xyz.ezsky.anilink.model.dto.SetSiteConfigRequest;
import xyz.ezsky.anilink.model.dto.UpdateSiteConfigRequest;
import xyz.ezsky.anilink.model.entity.SiteConfig;
import xyz.ezsky.anilink.model.entity.User;
import xyz.ezsky.anilink.repository.SiteConfigRepository;
import xyz.ezsky.anilink.repository.UserRepository;

import java.util.Optional;

/**
 * 站点配置服务
 */
@Service
public class SiteConfigService {
    
    @Autowired
    private SiteConfigRepository siteConfigRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserService userService;
    
    // 配置键常量
    private static final String SITE_NAME = "site_name";
    private static final String SITE_DESCRIPTION = "site_description";
    private static final String SITE_URL = "site_url";
    private static final String INSTALLED = "installed";
    private static final String DANDAN_APP_ID = "dandan_app_id";
    private static final String DANDAN_APP_SECRET = "dandan_app_secret";

    // 简单内存缓存，避免每次请求都访问数据库
    private volatile String cachedDandanAppId;
    private volatile String cachedDandanAppSecret;
    
    /**
     * 获取站点配置
     */
    public SiteConfigVO getSiteConfig() {
        SiteConfigVO vo = new SiteConfigVO();
        
        // 获取站点名称
        siteConfigRepository.findByConfigKey(SITE_NAME)
            .ifPresent(config -> vo.setSiteName(config.getConfigValue()));
        
        // 获取站点描述
        siteConfigRepository.findByConfigKey(SITE_DESCRIPTION)
            .ifPresent(config -> vo.setSiteDescription(config.getConfigValue()));
        
        // 获取站点URL
        siteConfigRepository.findByConfigKey(SITE_URL)
            .ifPresent(config -> vo.setSiteUrl(config.getConfigValue()));
        
        // 检查是否已安装
        Optional<SiteConfig> installedConfig = siteConfigRepository.findByConfigKey(INSTALLED);
        vo.setInstalled(installedConfig.isPresent() && "true".equals(installedConfig.get().getConfigValue()));

        // Dandan 配置
        siteConfigRepository.findByConfigKey(DANDAN_APP_ID)
            .ifPresent(config -> vo.setDandanAppId(config.getConfigValue()));
        siteConfigRepository.findByConfigKey(DANDAN_APP_SECRET)
            .ifPresent(config -> vo.setDandanAppSecret(config.getConfigValue()));
        
        return vo;
    }
    
    /**
     * 设置站点配置和管理员账号（初始化用）
     */
    public void setSiteConfig(SetSiteConfigRequest request) {
        // 设置站点名称
        saveOrUpdateConfig(SITE_NAME, request.getSiteName(), "站点名称");
        
        // 设置站点描述
        saveOrUpdateConfig(SITE_DESCRIPTION, request.getSiteDescription(), "站点描述");
        
        // 设置站点URL
        saveOrUpdateConfig(SITE_URL, request.getSiteUrl(), "站点URL");
        
        // 设置已安装标记
        saveOrUpdateConfig(INSTALLED, "true", "安装标记");
        
        // 创建管理员账号
        userService.createAdminUser(request.getAdminUsername(), request.getAdminPassword(), "example@example.com");

        // Dandan 配置（可选）
        if (request.getDandanAppId() != null) {
            saveOrUpdateConfig(DANDAN_APP_ID, request.getDandanAppId(), "Dandan 应用 ID");
            cachedDandanAppId = request.getDandanAppId();
        }
        if (request.getDandanAppSecret() != null) {
            saveOrUpdateConfig(DANDAN_APP_SECRET, request.getDandanAppSecret(), "Dandan 应用密钥");
            cachedDandanAppSecret = request.getDandanAppSecret();
        }
    }
    
    /**
     * 更新站点配置
     */
    public void updateSiteConfig(UpdateSiteConfigRequest request) {
        if (request.getSiteName() != null) {
            saveOrUpdateConfig(SITE_NAME, request.getSiteName(), "站点名称");
        }
        if (request.getSiteDescription() != null) {
            saveOrUpdateConfig(SITE_DESCRIPTION, request.getSiteDescription(), "站点描述");
        }
        if (request.getSiteUrl() != null) {
            saveOrUpdateConfig(SITE_URL, request.getSiteUrl(), "站点URL");
        }
        if (request.getDandanAppId() != null) {
            saveOrUpdateConfig(DANDAN_APP_ID, request.getDandanAppId(), "Dandan 应用 ID");
            cachedDandanAppId = request.getDandanAppId();
        }
        if (request.getDandanAppSecret() != null) {
            saveOrUpdateConfig(DANDAN_APP_SECRET, request.getDandanAppSecret(), "Dandan 应用密钥");
            cachedDandanAppSecret = request.getDandanAppSecret();
        }
    }
    
    /**
     * 保存或更新配置
     */
    private void saveOrUpdateConfig(String key, String value, String description) {
        Optional<SiteConfig> configOpt = siteConfigRepository.findByConfigKey(key);
        
        if (configOpt.isPresent()) {
            // 更新现有配置
            SiteConfig config = configOpt.get();
            config.setConfigValue(value);
            siteConfigRepository.save(config);
        } else {
            // 创建新配置
            SiteConfig config = new SiteConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setDescription(description);
            siteConfigRepository.save(config);
        }
    }

    /**
     * 获取缓存的 Dandan AppId，如果缓存为空则从数据库加载
     */
    public String getDandanAppId() {
        if (cachedDandanAppId == null) {
            synchronized (this) {
                if (cachedDandanAppId == null) {
                    cachedDandanAppId = siteConfigRepository.findByConfigKey(DANDAN_APP_ID)
                        .map(SiteConfig::getConfigValue)
                        .orElse(null);
                }
            }
        }
        return cachedDandanAppId;
    }

    /**
     * 获取缓存的 Dandan AppSecret，如果缓存为空则从数据库加载
     */
    public String getDandanAppSecret() {
        if (cachedDandanAppSecret == null) {
            synchronized (this) {
                if (cachedDandanAppSecret == null) {
                    cachedDandanAppSecret = siteConfigRepository.findByConfigKey(DANDAN_APP_SECRET)
                        .map(SiteConfig::getConfigValue)
                        .orElse(null);
                }
            }
        }
        return cachedDandanAppSecret;
    }
    
    /**
     * 检查是否已安装
     */
    public boolean isInstalled() {
        Optional<SiteConfig> config = siteConfigRepository.findByConfigKey(INSTALLED);
        return config.isPresent() && "true".equals(config.get().getConfigValue());
    }
}
