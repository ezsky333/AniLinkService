package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Bangumi.tv API 代理服务。
 * 无需鉴权直接请求公开接口。
 */
@Service
@Log4j2
public class BangumiApiService {

    private static final String BANGUMI_BASE = "https://next.bgm.tv";

    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();

    /**
     * 获取番剧吐槽箱（短评列表）。
     *
     * @param subjectId Bangumi subjectID
     * @param limit     每页数量
     * @param offset    偏移量
     * @return 原始 JSON 字符串，失败返回 null
     */
    public String getSubjectComments(Long subjectId, Integer limit, Integer offset) {
        String url = BANGUMI_BASE + "/p1/subjects/" + subjectId + "/comments"
                + "?limit=" + limit + "&offset=" + offset;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", "AniLinkService/1.0 (https://github.com/AniLink)")
                .addHeader("Accept", "application/json")
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (response.isSuccessful()) {
                return body;
            }
            log.warn("Bangumi API returned {} for subjectId={}", response.code(), subjectId);
            return null;
        } catch (IOException e) {
            log.error("Failed to fetch Bangumi comments for subjectId={}", subjectId, e);
            return null;
        }
    }
}
