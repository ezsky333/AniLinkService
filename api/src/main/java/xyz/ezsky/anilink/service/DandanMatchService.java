package xyz.ezsky.anilink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import xyz.ezsky.anilink.model.dto.AnimeInfo;
import xyz.ezsky.anilink.model.entity.Anime;
import xyz.ezsky.anilink.repository.AnimeRepository;
import xyz.ezsky.anilink.util.DandanClientUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class DandanMatchService {

    private final DandanClientUtil client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 默认弹弹 play API 地址
    private static final String DANDAN_BASE = "https://api.dandanplay.net";

    @Value("${dandan.image.dir:/data/dandan-images}")
    private String imageDir;

    private final AnimeRepository animeRepository;
    private final RestTemplate rest = new RestTemplate();

    public DandanMatchService(DandanClientUtil client, AnimeRepository animeRepository) {
        this.client = client;
        this.animeRepository = animeRepository;
    }

    /**
     * 使用 /api/v2/match/batch 接口尝试根据文件信息匹配动漫信息。
     * 返回第一个可用的匹配信息，若未匹配返回 null。
     */
    public AnimeInfo queryByFile(String fileName, String hash, Long fileSize) {
        try {
            Map<String, Object> item = new HashMap<>();
            if (fileName != null) item.put("fileName", fileName);
            if (hash != null) item.put("fileHash", hash);
            if (fileSize != null) item.put("fileSize", fileSize);
            item.put("matchMode","hashAndFileName");
            log.debug("Querying Dandan with: {}", item);

            ResponseEntity<String> resp;
            try {
                resp = client.post(DANDAN_BASE, "/api/v2/match", item);
            } catch (Exception e) {
                log.error("Dandan batch match call failed", e);
                return null;
            }
            log.debug("Dandan match response: {} - {}", resp.getStatusCode(), resp.getBody());
            if (!resp.getStatusCode().is2xxSuccessful()) return null;

            String json = resp.getBody();
            if (json == null || json.isEmpty()) return null;

            JsonNode root = objectMapper.readTree(json);

            // 递归查找包含动漫字段的节点
            AnimeInfo info = findAnimeInfo(root);

            // 如果找到了 animeId，则尝试保证 Anime 表中存在详细数据
            if (info != null && info.getAnimeId() != null) {
                Long aid = info.getAnimeId();
                try {
                    Optional<Anime> exist = animeRepository.findByAnimeId(aid);
                    
                    if (exist.isEmpty()) {
                        // 拉取详情并保存
                        ResponseEntity<String> detailResp = client.get(DANDAN_BASE, "/api/v2/bangumi/" + aid);
                        System.out.println("Checking local anime for animeId " + aid + ": " + detailResp.getStatusCode());
                        if (detailResp.getStatusCode().is2xxSuccessful()) {
                            String detailJson = detailResp.getBody();
                            System.out.println("Fetching detail for animeId " + aid + ": " + detailJson);
                            if (detailJson != null && !detailJson.isEmpty()) {
                                Anime anime = new Anime();
                                anime.setAnimeId(aid);
                                anime.setRawJson(detailJson);

                                try {
                                    JsonNode detailNode = objectMapper.readTree(detailJson);
                                    JsonNode bangumi = detailNode.get("bangumi");
                                    if (bangumi != null && !bangumi.isNull()) {
                                        // 提取标题：从 titles 数组中获取
                                        String mainTitle = null;
                                        String altTitle = null;
                                        JsonNode titles = bangumi.get("titles");
                                        if (titles != null && titles.isArray()) {
                                            for (JsonNode titleObj : titles) {
                                                String lang = titleObj.get("language") != null ? titleObj.get("language").asText() : "";
                                                String title = titleObj.get("title") != null ? titleObj.get("title").asText() : "";
                                                if (lang.equals("主标题") && mainTitle == null) {
                                                    mainTitle = title;
                                                } else if ((lang.contains("简体中文") || lang.contains("中文")) && altTitle == null) {
                                                    altTitle = title;
                                                }
                                            }
                                        }
                                        anime.setTitle(mainTitle != null ? mainTitle : extractFirstText(bangumi, "animeTitle", "name"));
                                        if (altTitle != null) {
                                            anime.setAltTitle(altTitle);
                                        }
                                        
                                        // 提取集数：从 episodes 数组的长度
                                        JsonNode episodes = bangumi.get("episodes");
                                        if (episodes != null && episodes.isArray()) {
                                            anime.setEpisodes(episodes.size());
                                        }
                                        
                                        // 提取年份和其他信息：从 metadata 数组中解析
                                        JsonNode metadata = bangumi.get("metadata");
                                        if (metadata != null && metadata.isArray()) {
                                            for (JsonNode meta : metadata) {
                                                if (meta.isTextual()) {
                                                    String metaStr = meta.asText();
                                                    // 提取年份（保存原始内容）
                                                    if (metaStr.startsWith("上映年度:") && anime.getYear() == null) {
                                                        anime.setYear(metaStr.replaceAll("上映年度:", "").trim());
                                                    }
                                                    // 提取片长
                                                    if ((metaStr.startsWith("片长:") || metaStr.startsWith("时长:")) && anime.getDuration() == null) {
                                                        anime.setDuration(metaStr.replaceAll("片长:|时长:", "").trim());
                                                    }
                                                }
                                            }
                                        }
                                        
                                        anime.setType(extractFirstText(bangumi, "typeDescription", "type"));
                                        
                                        // 提取简介
                                        JsonNode summary = bangumi.get("summary");
                                        if (summary != null && summary.isTextual()) {
                                            anime.setSummary(summary.asText());
                                        }
                                        
                                        // 提取标签（最多前10个）
                                        JsonNode tags = bangumi.get("tags");
                                        if (tags != null && tags.isArray()) {
                                            StringBuilder tagStr = new StringBuilder();
                                            int count = 0;
                                            for (JsonNode tag : tags) {
                                                if (count >= 10) break;
                                                if (tagStr.length() > 0) tagStr.append(",");
                                                tagStr.append(extractFirstText(tag, "name", "title"));
                                                count++;
                                            }
                                            if (tagStr.length() > 0) {
                                                anime.setTags(tagStr.toString());
                                            }
                                        }
                                        
                                        // 提取评分
                                        JsonNode ratingDetails = bangumi.get("ratingDetails");
                                        if (ratingDetails != null && ratingDetails.isObject()) {
                                            JsonNode rating = ratingDetails.get("弹弹play完结后评分");
                                            if (rating == null) rating = ratingDetails.get("弹弹play连载中评分");
                                            if (rating != null && rating.isNumber()) {
                                                anime.setRating(rating.asDouble());
                                            }
                                        }
                                        
                                        String imageUrl = extractFirstText(bangumi, "imageUrl", "image", "cover", "poster", "coverImage", "images");
                                        anime.setImageUrl(imageUrl);
                                    } else {
                                        // 如果没有 bangumi 节点，直接从顶层提取
                                        anime.setTitle(extractFirstText(detailNode, "title", "name"));
                                        anime.setAltTitle(extractFirstText(detailNode, "altTitle", "alias", "names"));
                                        anime.setYear(extractFirstText(detailNode, "year", "publishYear"));
                                        anime.setEpisodes(parseInteger(extractFirstText(detailNode, "episodes", "epCount", "episodeCount")));
                                        String imageUrl = extractFirstText(detailNode, "imageUrl", "image", "cover", "poster", "coverImage", "images");
                                        anime.setImageUrl(imageUrl);
                                    }

                                    // 确保图片目录存在
                                    if (StringUtils.hasText(anime.getImageUrl())) {
                                        try {
                                            Files.createDirectories(Paths.get(imageDir));
                                            // 下载图片
                                            ResponseEntity<byte[]> imgResp = rest.getForEntity(new URI(anime.getImageUrl()), byte[].class);
                                            if (imgResp.getStatusCode().is2xxSuccessful() && imgResp.getBody() != null) {
                                                String ext = guessExtension(anime.getImageUrl(), imgResp.getHeaders().getContentType() != null ? imgResp.getHeaders().getContentType().getSubtype() : null);
                                                String imageFileName = "dandan_" + aid + (ext != null ? ext : "");
                                                File out = Paths.get(imageDir, imageFileName).toFile();
                                                try (FileOutputStream fos = new FileOutputStream(out)) {
                                                    fos.write(imgResp.getBody());
                                                }
                                                anime.setLocalImagePath(out.getName());
                                            }
                                        } catch (Exception ignored) {
                                        }
                                    }
                                } catch (Exception ignored) {
                                }

                                try {
                                    animeRepository.save(anime);
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            return info;
        } catch (Exception e) {
            log.error("Unexpected error querying Dandan for fileName={} hash={} size={}", fileName, hash, fileSize, e);
            return null;
        }
    }

    private Integer parseInteger(String s) {
        if (s == null) return null;
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private String extractFirstText(JsonNode node, String... keys) {
        if (node == null) return null;
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v == null) continue;
            if (v.isTextual()) return v.asText();
            if (v.isNumber()) return v.asText();
            if (v.isArray() && v.size() > 0) {
                JsonNode first = v.get(0);
                if (first.isTextual()) return first.asText();
                else return first.toString();
            }
            if (v.isObject()) {
                JsonNode title = v.get("title");
                if (title != null && title.isTextual()) return title.asText();
            }
        }
        // 递归 search: try children
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                String found = extractFirstText(child, keys);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String guessExtension(String url, String contentSubtype) {
        try {
            String path = URI.create(url).getPath();
            int idx = path.lastIndexOf('.');
            if (idx > 0) return path.substring(idx);
        } catch (Exception ignored) {}
        if (contentSubtype != null) {
            if (contentSubtype.contains("jpeg") || contentSubtype.contains("jpg")) return ".jpg";
            if (contentSubtype.contains("png")) return ".png";
            if (contentSubtype.contains("gif")) return ".gif";
        }
        return null;
    }

    private AnimeInfo findAnimeInfo(JsonNode node) {
        if (node == null) return null;

        if (node.isObject()) {
            JsonNode epId = node.get("episodeId");
            JsonNode anId = node.get("animeId");
            JsonNode anTitle = node.get("animeTitle");
            JsonNode epTitle = node.get("episodeTitle");

            if ((epId != null && !epId.isNull()) || (anId != null && !anId.isNull())
                    || (anTitle != null && !anTitle.isNull()) || (epTitle != null && !epTitle.isNull())) {
                AnimeInfo info = new AnimeInfo();
                if (epId != null && !epId.isNull()) info.setEpisodeId(epId.asText(null));
                if (anId != null && !anId.isNull()) {
                    try {
                        info.setAnimeId(anId.isNumber() ? anId.longValue() : Long.parseLong(anId.asText()));
                    } catch (Exception ignored) {}
                }
                if (anTitle != null && !anTitle.isNull()) info.setAnimeTitle(anTitle.asText(null));
                if (epTitle != null && !epTitle.isNull()) info.setEpisodeTitle(epTitle.asText(null));
                return info;
            }
        }

        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                AnimeInfo found = findAnimeInfo(child);
                if (found != null) return found;
            }
        }

        return null;
    }
}
