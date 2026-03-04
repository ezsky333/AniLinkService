package xyz.ezsky.anilink.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xyz.ezsky.anilink.model.vo.AnimeVO;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.EpisodeVO;
import xyz.ezsky.anilink.service.AnimeService;

import java.util.List;

/**
 * 动漫管理接口
 */
@Tag(name = "动漫管理", description = "用于获取动漫信息和剧集列表")
@RestController
@RequestMapping("/api/animes")
public class AnimeController {

    @Autowired
    private AnimeService animeService;

    /**
     * 获取所有动漫列表
     *
     * @return 动漫列表
     */
    @Operation(summary = "获取所有动漫", description = "获取系统中已记录的所有动漫列表")
    @GetMapping
    public ApiResponseVO<List<AnimeVO>> getAllAnimes() {
        List<AnimeVO> animes = animeService.getAllAnimes();
        return ApiResponseVO.success(animes);
    }

    /**
     * 根据弹幕库动漫ID获取动漫详情
     *
     * @param animeId 弹幕库动漫ID
     * @return 动漫信息
     */
    @Operation(summary = "获取动漫详情", description = "根据弹幕库动漫ID获取动漫的详细信息")
    @GetMapping("/{animeId}")
    public ApiResponseVO<AnimeVO> getAnimeById(
            @Parameter(description = "弹幕库动漫ID", required = true)
            @PathVariable Long animeId) {
        AnimeVO anime = animeService.getAnimeById(animeId);
        if (anime == null) {
            return ApiResponseVO.fail(404, "动漫不存在");
        }
        return ApiResponseVO.success(anime);
    }

    /**
     * 根据动漫ID获取视频库中该动漫的所有剧集
     *
     * @param animeId 弹幕库动漫ID
     * @return 该动漫的剧集列表
     */
    @Operation(summary = "获取动漫的剧集列表", description = "根据弹幕库动漫ID获取当前视频库中该动漫的所有剧集")
    @GetMapping("/{animeId}/episodes")
    public ApiResponseVO<List<EpisodeVO>> getEpisodesByAnimeId(
            @Parameter(description = "弹幕库动漫ID", required = true)
            @PathVariable Long animeId) {
        List<EpisodeVO> episodes = animeService.getEpisodesByAnimeId(animeId);
        return ApiResponseVO.success(episodes);
    }

    /**
     * 获取动漫详情（使用数据库ID）
     *
     * @param id 数据库ID
     * @return 动漫信息
     */
    @Operation(summary = "根据数据库ID获取动漫", description = "使用数据库内部ID获取动漫详情")
    @GetMapping("/db/{id}")
    public ApiResponseVO<AnimeVO> getAnimeByDbId(
            @Parameter(description = "数据库ID", required = true)
            @PathVariable Long id) {
        AnimeVO anime = animeService.getAnimeByDbId(id);
        if (anime == null) {
            return ApiResponseVO.fail(404, "动漫不存在");
        }
        return ApiResponseVO.success(anime);
    }
}
