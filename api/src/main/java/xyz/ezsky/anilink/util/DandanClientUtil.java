package xyz.ezsky.anilink.util;

import com.alibaba.fastjson.JSON;
import lombok.extern.log4j.Log4j2;
import okhttp3.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import xyz.ezsky.anilink.service.SiteConfigService;

import java.util.Date;
import java.util.Map;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 通用 Dandan 客户端工具类，负责签名并发起请求。
 * 会从 `SiteConfigService` 获取缓存的 `dandanAppId` 与 `dandanAppSecret`。
 */
@Component
@Log4j2
public class DandanClientUtil {

    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    private final SiteConfigService siteConfigService;

    public DandanClientUtil(SiteConfigService siteConfigService) {
        this.siteConfigService = siteConfigService;
    }

    private Headers buildHeaders(String path) {
        String appId = siteConfigService.getDandanAppId();
        String appSecret = siteConfigService.getDandanAppSecret();
        long timestamp = new Date().getTime() / 1000;

        Headers.Builder headersBuilder = new Headers.Builder();
        headersBuilder.add("Content-Type", "application/json");
        if (appId != null && appSecret != null) {
            String signature = DandanSignatureGenerator.generateSignature(appId, timestamp, path, appSecret);
            headersBuilder.add("X-AppId", appId);
            headersBuilder.add("X-Timestamp", String.valueOf(timestamp));
            headersBuilder.add("X-Signature", signature);
        }
        return headersBuilder.build();
    }

    public ResponseEntity<String> get(String baseUrl, String path) {
        return get(baseUrl, path, null);
    }

    public ResponseEntity<String> get(String baseUrl, String path, Map<String, String> queryParams) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(combine(baseUrl, path)).newBuilder();
        if (queryParams != null) {
            queryParams.forEach(urlBuilder::addQueryParameter);
        }
        String url = urlBuilder.build().toString();
        
        Request request = new Request.Builder()
                .url(url)
                .headers(buildHeaders(path))
                .get()
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            log.debug("GET {} returned {}", url, response.code());
            return new ResponseEntity<>(body, HttpStatus.valueOf(response.code()));
        } catch (IOException e) {
            log.error("GET request failed for {}", url, e);
            throw new RuntimeException(e);
        }
    }

    public ResponseEntity<String> post(String baseUrl, String path, Object body) {
        log.debug("Preparing POST request to {} with body: {}", path, body);
        String url = combine(baseUrl, path);
        String jsonBody = JSON.toJSONString(body);
        RequestBody requestBody = RequestBody.create(jsonBody, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .headers(buildHeaders(path))
                .post(requestBody)
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            log.debug("POST {} returned {}", url, response.code());
            return new ResponseEntity<>(responseBody, HttpStatus.valueOf(response.code()));
        } catch (IOException e) {
            log.error("POST request failed for {}", url, e);
            throw new RuntimeException(e);
        }
    }

    private String combine(String base, String path) {
        if (base == null) return path;
        if (base.endsWith("/") && path.startsWith("/")) return base + path.substring(1);
        if (!base.endsWith("/") && !path.startsWith("/")) return base + "/" + path;
        return base + path;
    }
}
