package xyz.ezsky.anilink.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.service.BangumiApiService;

/**
 * Bangumi.tv API 代理接口
 */
@Tag(name = "Bangumi API", description = "Bangumi.tv 公开接口代理")
@RestController
@RequestMapping("/api/bangumi")
@Log4j2
public class BangumiController {

    @Autowired
    private BangumiApiService bangumiApiService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取番剧吐槽箱（短评）
     *
     * @param subjectId Bangumi subjectID（来自番剧详情页 bangumiUrl 中的数字）
     * @param limit     每页数量（默认 20，最大 50）
     * @param offset    偏移量（默认 0）
     * @return 评论列表
     */
    @Operation(summary = "获取番剧评论", description = "代理 Bangumi API 获取番剧吐槽箱内容")
    @GetMapping("/subjects/{subjectId}/comments")
    public ApiResponseVO<Object> getSubjectComments(
            @Parameter(description = "Bangumi subjectID", required = true)
            @PathVariable Long subjectId,
            @Parameter(description = "每页数量")
            @RequestParam(defaultValue = "20") Integer limit,
            @Parameter(description = "偏移量")
            @RequestParam(defaultValue = "0") Integer offset) {

        if (limit < 1 || limit > 50) {
            limit = 20;
        }
        if (offset < 0) {
            offset = 0;
        }

        String result = bangumiApiService.getSubjectComments(subjectId, limit, offset);
        if (!StringUtils.hasText(result)) {
            return ApiResponseVO.fail(502, "获取评论失败");
        }
        try {
            Object json = objectMapper.readValue(result, Object.class);
            return ApiResponseVO.success(json);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Bangumi comments JSON for subjectId={}", subjectId, e);
            return ApiResponseVO.fail(500, "评论数据解析失败");
        }
    }
}
