package xyz.ezsky.anilink.util;

import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import xyz.ezsky.anilink.service.SiteConfigService;

import java.util.Date;
import java.time.Instant;

import lombok.extern.log4j.Log4j2;

/**
 * 通用 Dandan 客户端工具类，负责签名并发起请求。
 * 会从 `SiteConfigService` 获取缓存的 `dandanAppId` 与 `dandanAppSecret`。
 */
@Component
@Log4j2
public class DandanClientUtil {

    private final RestTemplate restTemplate = new RestTemplate();
    private final SiteConfigService siteConfigService;

    public DandanClientUtil(SiteConfigService siteConfigService) {
        this.siteConfigService = siteConfigService;
    }

    private HttpHeaders buildHeaders(String path) {
        String appId = siteConfigService.getDandanAppId();
        String appSecret = siteConfigService.getDandanAppSecret();
        long timestamp = new Date().getTime() / 1000;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (appId != null && appSecret != null) {
            String signature = DandanSignatureGenerator.generateSignature(appId, timestamp, path, appSecret);
            headers.set("X-AppId", appId);
            headers.set("X-Timestamp", String.valueOf(timestamp));
            headers.set("X-Signature", signature);
        }
        return headers;
    }

    public ResponseEntity<String> get(String baseUrl, String path) {
        String url = combine(baseUrl, path);
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders(path));
        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        log.debug("GET {} returned {}", url, resp.getStatusCode());
        return resp;
    }

    public ResponseEntity<String> post(String baseUrl, String path, Object body) {
        log.debug("Preparing POST request to {} with body: {}", path, body);
        String url = combine(baseUrl, path);
        HttpEntity<Object> entity = new HttpEntity<>(body, buildHeaders(path));
        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        log.debug("POST {} returned {}", url, resp.getStatusCode());
        return resp;
    }

    private String combine(String base, String path) {
        if (base == null) return path;
        if (base.endsWith("/") && path.startsWith("/")) return base + path.substring(1);
        if (!base.endsWith("/") && !path.startsWith("/")) return base + "/" + path;
        return base + path;
    }
}
