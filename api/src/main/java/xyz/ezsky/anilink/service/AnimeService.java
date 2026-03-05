package xyz.ezsky.anilink.service;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.entity.Anime;
import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.model.vo.AnimeVO;
import xyz.ezsky.anilink.model.vo.EpisodeVO;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.repository.AnimeRepository;
import xyz.ezsky.anilink.repository.MediaFileRepository;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * 动漫管理服务。
 * 
 * <p>提供动漫查询以及根据动漫获取视频库中的剧集功能。</p>
 */
@Service
public class AnimeService {

    @Autowired
    private AnimeRepository animeRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    /**
     * 获取所有动漫列表
     *
     * @return 动漫信息列表
     */
    public List<AnimeVO> getAllAnimes() {
        List<Anime> animes = animeRepository.findAll();
        return animes.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 获取分页的动漫列表
     *
     * @param page 页码（从1开始）
     * @param pageSize 每页大小
     * @param keyword 搜索关键词（可选，为null时查询所有）
     * @return 分页结果VO
     */
    public PageVO<AnimeVO> getAnimesPage(int page, int pageSize, String keyword) {
        // 创建分页请求（Spring Data JPA中page从0开始）
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        
        // 根据是否有关键词选择查询方法
        Page<Anime> animePage;
        if (keyword != null && !keyword.trim().isEmpty()) {
            animePage = animeRepository.findByTitleContainingIgnoreCase(keyword.trim(), pageable);
        } else {
            animePage = animeRepository.findAll(pageable);
        }
        
        // 将实体转换为VO
        List<AnimeVO> data = animePage.getContent().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        
        // 构建返回结果
        return PageVO.<AnimeVO>builder()
                .content(data)
                .totalElements(animePage.getTotalElements())
                .totalPages(animePage.getTotalPages())
                .currentPage(page)
                .pageSize(pageSize)
                .hasNext(animePage.hasNext())
                .hasPrevious(animePage.hasPrevious())
                .build();
    }


    /**
     * 根据动漫ID获取动漫详情
     *
     * @param animeId 弹幕库动漫ID
     * @return 动漫信息
     */
    public AnimeVO getAnimeById(Long animeId) {
        return animeRepository.findByAnimeId(animeId)
                .map(this::convertToVO)
                .orElse(null);
    }

    /**
     * 根据动漫ID获取视频库中该动漫的所有剧集
     *
     * @param animeId 弹幕库动漫ID
     * @return 该动漫的剧集列表
     */
    public List<EpisodeVO> getEpisodesByAnimeId(Long animeId) {
        // 首先获取所有媒体文件，然后筛选出属于该动漫的文件
        List<MediaFile> allMediaFiles = mediaFileRepository.findAll();
        
        return allMediaFiles.stream()
                .filter(file -> file.getAnimeId() != null && file.getAnimeId().equals(animeId))
                .map(this::convertToEpisodeVO)
                .collect(Collectors.toList());
    }

    /**
     * 根据数据库ID获取动漫详情
     *
     * @param id 数据库ID
     * @return 动漫信息
     */
    public AnimeVO getAnimeByDbId(Long id) {
        return animeRepository.findById(id)
                .map(this::convertToVO)
                .orElse(null);
    }

    /**
     * 将 Anime 实体转换为 AnimeVO
     *
     * @param anime 动漫实体
     * @return 动漫视图对象
     */
    private AnimeVO convertToVO(Anime anime) {
        AnimeVO animeVO = new AnimeVO();
        BeanUtils.copyProperties(anime, animeVO);
        return animeVO;
    }

    /**
     * 将 MediaFile 实体转换为 EpisodeVO
     *
     * @param mediaFile 媒体文件实体
     * @return 剧集视图对象
     */
    private EpisodeVO convertToEpisodeVO(MediaFile mediaFile) {
        EpisodeVO episodeVO = new EpisodeVO();
        BeanUtils.copyProperties(mediaFile, episodeVO);
        return episodeVO;
    }
}
