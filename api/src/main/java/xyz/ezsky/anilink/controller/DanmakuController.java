package xyz.ezsky.anilink.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.service.DanmakuService;

/**
 * 弹幕管理接口
 */
@Tag(name = "弹幕管理", description = "用于获取弹幕数据")
@RestController
@RequestMapping("/api/v2")
public class DanmakuController {

    @Autowired
    private DanmakuService danmakuService;

    /**
     * 获取指定弹幕库的所有弹幕（带30分钟缓存）
     *
     * @param episodeId 弹幕库ID
     * @param withRelated 是否包含第三方关联网址的弹幕
     * @return 弹幕数据
     */
    @Operation(
        summary = "获取弹幕", 
        description = "代理弹弹play /api/v2/comment/{episodeId} 接口，使用30分钟数据库缓存。" +
                      "当 withRelated 参数为 true 时，将返回此弹幕库对应的所有第三方关联网址的弹幕。"
    )
    @GetMapping("/comment/{episodeId}")
    public ApiResponseVO<Object> getComment(
            @Parameter(description = "弹幕库ID", required = true)
            @PathVariable Long episodeId,
            @Parameter(description = "是否包含第三方关联弹幕", required = false)
            @RequestParam(required = false, defaultValue = "false") Boolean withRelated) {
        
        String rawJson = danmakuService.getCommentByEpisodeId(episodeId, withRelated);
        
        if (rawJson == null) {
            return ApiResponseVO.fail(404, "弹幕数据不存在");
        }
        
        try {
            // 将 JSON 字符串转换为对象返回
            Object json = new ObjectMapper().readValue(rawJson, Object.class);
            return ApiResponseVO.success(json);
        } catch (JsonProcessingException e) {
            return ApiResponseVO.fail(500, "弹幕数据解析失败");
        }
    }
}
