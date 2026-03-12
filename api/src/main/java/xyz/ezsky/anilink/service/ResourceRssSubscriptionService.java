package xyz.ezsky.anilink.service;

import com.frostwire.jlibtorrent.TorrentInfo;
import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import xyz.ezsky.anilink.model.dto.ResourceRssSubscriptionRequest;
import xyz.ezsky.anilink.model.dto.ResourceSearchDownloadRequest;
import xyz.ezsky.anilink.model.entity.MediaLibrary;
import xyz.ezsky.anilink.model.entity.ResourceRssSubscription;
import xyz.ezsky.anilink.model.vo.ResourceSearchVO;
import xyz.ezsky.anilink.repository.MediaLibraryRepository;
import xyz.ezsky.anilink.repository.ResourceDownloadTaskRepository;
import xyz.ezsky.anilink.repository.ResourceRssSubscriptionRepository;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
@Service
public class ResourceRssSubscriptionService {

    private static final Pattern MAGNET_PATTERN = Pattern.compile("(magnet:\\?xt=urn:btih:[A-Za-z0-9]+[^\\s\"'<>]*)", Pattern.CASE_INSENSITIVE);
    private static final int MAX_FETCHED_CONTENT_LENGTH = 200_000;
    private static final int MAX_ENTRY_LINES = 200;

    @Autowired
    private ResourceRssSubscriptionRepository rssRepository;

    @Autowired
    private MediaLibraryRepository mediaLibraryRepository;

    @Autowired
    private ResourceDownloadTaskRepository taskRepository;

    @Autowired
    private ResourceDownloadService resourceDownloadService;

    @Autowired
    private SiteConfigService siteConfigService;

    private final OkHttpClient baseClient = new OkHttpClient();

    public List<ResourceSearchVO.RssSubscriptionItem> listSubscriptions() {
        List<ResourceSearchVO.RssSubscriptionItem> list = new ArrayList<>();
        for (ResourceRssSubscription item : rssRepository.findAllByOrderByCreatedAtDesc()) {
            list.add(toVO(item));
        }
        return list;
    }

    public ResourceSearchVO.RssSubscriptionItem createSubscription(ResourceRssSubscriptionRequest request) {
        ResourceRssSubscription entity = new ResourceRssSubscription();
        applyRequest(entity, request);
        return toVO(rssRepository.save(entity));
    }

    public ResourceSearchVO.RssSubscriptionItem updateSubscription(Long id, ResourceRssSubscriptionRequest request) {
        ResourceRssSubscription entity = rssRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅不存在"));
        applyRequest(entity, request);
        return toVO(rssRepository.save(entity));
    }

    public void deleteSubscription(Long id) {
        ResourceRssSubscription entity = rssRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅不存在"));
        rssRepository.delete(entity);
    }

    public void triggerNow(Long id) {
        ResourceRssSubscription entity = rssRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅不存在"));
        pollSubscription(entity);
    }

        public ResourceSearchVO.RssFetchedContent getLastFetchedContent(Long id) {
        ResourceRssSubscription entity = rssRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅不存在"));
        return ResourceSearchVO.RssFetchedContent.builder()
            .id(entity.getId())
            .name(entity.getName())
            .lastCheckedAt(entity.getLastCheckedAt())
            .lastFetchedContent(entity.getLastFetchedContent())
            .build();
        }

    @Scheduled(fixedDelay = 60000)
    public void pollEnabledSubscriptions() {
        List<ResourceRssSubscription> enabled = rssRepository.findByEnabledTrueOrderByCreatedAtAsc();
        for (ResourceRssSubscription item : enabled) {
            if (!isDue(item)) {
                continue;
            }
            pollSubscription(item);
        }
    }

    private boolean isDue(ResourceRssSubscription item) {
        Timestamp lastChecked = item.getLastCheckedAt();
        if (lastChecked == null) {
            return true;
        }
        long intervalMinutes = Math.max(1, item.getIntervalMinutes() == null ? 30 : item.getIntervalMinutes());
        long elapsedMs = System.currentTimeMillis() - lastChecked.getTime();
        return elapsedMs >= intervalMinutes * 60_000L;
    }

    private void pollSubscription(ResourceRssSubscription item) {
        item.setLastCheckedAt(Timestamp.from(Instant.now()));
        try {
            String xml = fetchRss(item.getFeedUrl());
            List<RssEntry> entries = parseEntries(xml);
            if (entries.isEmpty()) {
                item.setLastFetchedContent("解析结果:\n- 本次未识别到可处理条目（未提取到 magnet）");
                item.setLastError("RSS 没有解析到可处理的条目（请检查 RSS 格式或地址）");
                rssRepository.save(item);
                return;
            }
            int created = 0;
            int duplicated = 0;
            List<String> resultLines = new ArrayList<>();
            for (RssEntry entry : entries) {
                if (entry.magnet == null || entry.magnet.isBlank()) {
                    continue;
                }
                if (taskRepository.existsByMagnet(entry.magnet)) {
                    duplicated++;
                    appendEntryLine(resultLines, "已存在", entry);
                    continue;
                }
                ResourceSearchDownloadRequest req = new ResourceSearchDownloadRequest(
                        entry.title != null ? entry.title : "RSS 资源",
                        entry.magnet,
                        entry.link,
                        null,
                        null,
                        null,
                        null,
                        item.getLibrary().getId()
                );
                resourceDownloadService.startDownload(req);
                created++;
                appendEntryLine(resultLines, "已创建", entry);
            }
            item.setLastFetchedContent(buildParsedResultContent(entries.size(), created, duplicated, resultLines));
            item.setLastSuccessAt(Timestamp.from(Instant.now()));
            if (created == 0) {
                item.setLastError("本次检查未发现新磁链（可能都已存在）");
            } else {
                item.setLastError(null);
            }
            rssRepository.save(item);
            if (created > 0) {
                log.info("RSS subscription {} created {} task(s)", item.getName(), created);
            }
        } catch (Exception e) {
            item.setLastError(e.getMessage());
            rssRepository.save(item);
            log.warn("RSS subscription poll failed, id={}, name={}", item.getId(), item.getName(), e);
        }
    }

    private String fetchRss(String feedUrl) throws Exception {
        Request request = new Request.Builder().url(feedUrl).get().build();
        OkHttpClient client = buildClientWithProxy();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("RSS 请求失败: " + response.code());
            }
            return response.body().string();
        }
    }

    private OkHttpClient buildClientWithProxy() {
        String host = siteConfigService.getRssProxyHost();
        Integer port = siteConfigService.getRssProxyPort();
        if (host == null || host.isBlank() || port == null || port <= 0) {
            return baseClient;
        }
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host.trim(), port));
        return baseClient.newBuilder().proxy(proxy).build();
    }

    private List<RssEntry> parseEntries(String xml) throws Exception {
        List<RssEntry> list = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        List<Element> entryNodes = collectEntryNodes(doc);
        for (Element item : entryNodes) {
            String title = firstNonBlank(textOf(item, "title"), textOf(item, "name"));
            String linkText = textOf(item, "link");
            String guid = textOf(item, "guid");
            String description = firstNonBlank(textOf(item, "description"), textOf(item, "content"), textOf(item, "summary"));

            List<String> candidateLinks = collectCandidateLinks(item, linkText, guid);
            String canonicalLink = candidateLinks.isEmpty() ? linkText : candidateLinks.get(0);

            String magnet = null;
            for (String value : candidateLinks) {
                magnet = firstMagnet(value);
                if (magnet != null) {
                    break;
                }
            }
            if (magnet == null) {
                magnet = firstMagnet(description);
            }
            if (magnet == null) {
                for (String link : candidateLinks) {
                    magnet = tryConvertTorrentUrlToMagnet(link);
                    if (magnet != null) {
                        break;
                    }
                }
            }

            if (magnet != null) {
                list.add(new RssEntry(title, canonicalLink, magnet));
            }
        }
        return list;
    }

    private List<Element> collectEntryNodes(Document doc) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = doc.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = node.getNodeName();
            if (matchesTag(name, "item") || matchesTag(name, "entry")) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private List<String> collectCandidateLinks(Element item, String linkText, String guid) {
        LinkedHashSet<String> links = new LinkedHashSet<>();
        addIfPresent(links, linkText);
        addIfPresent(links, guid);

        NodeList children = item.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            String nodeName = element.getNodeName();
            if (matchesTag(nodeName, "link")) {
                addIfPresent(links, element.getAttribute("href"));
                addIfPresent(links, element.getTextContent());
            }
            if (matchesTag(nodeName, "enclosure")) {
                addIfPresent(links, element.getAttribute("url"));
                addIfPresent(links, element.getAttribute("href"));
            }
        }
        return new ArrayList<>(links);
    }

    private String textOf(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (!matchesTag(child.getNodeName(), tagName)) {
                continue;
            }
            String text = child.getTextContent();
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private String firstMagnet(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = MAGNET_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String tryConvertTorrentUrlToMagnet(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        if (!looksLikeHttpUrl(url)) {
            return null;
        }

        Request request = new Request.Builder().url(url).get().build();
        OkHttpClient client = buildClientWithProxy();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            String contentType = response.header("Content-Type");
            byte[] data = response.body().bytes();
            if (data.length == 0 || data.length > 10 * 1024 * 1024) {
                return null;
            }
            if (!looksLikeTorrentContent(url, contentType, data)) {
                return null;
            }

            TorrentInfo torrentInfo = new TorrentInfo(data);
            String magnet = torrentInfo.makeMagnetUri();
            if (magnet != null && !magnet.isBlank()) {
                return magnet;
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean looksLikeHttpUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private boolean looksLikeTorrentContent(String url, String contentType, byte[] data) {
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        if (lowerUrl.contains(".torrent")) {
            return true;
        }
        if (contentType != null) {
            String lowerType = contentType.toLowerCase(Locale.ROOT);
            if (lowerType.contains("application/x-bittorrent") || lowerType.contains("application/octet-stream")) {
                return true;
            }
        }
        // bencoded torrent files usually begin with 'd'
        return data.length > 1 && data[0] == 'd';
    }

    private boolean matchesTag(String nodeName, String expected) {
        if (nodeName == null) {
            return false;
        }
        return nodeName.equalsIgnoreCase(expected) || nodeName.toLowerCase(Locale.ROOT).endsWith(":" + expected.toLowerCase(Locale.ROOT));
    }

    private void addIfPresent(LinkedHashSet<String> set, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.isEmpty()) {
            set.add(trimmed);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String buildParsedResultContent(int parsedCount, int createdCount, int duplicatedCount, List<String> lines) {
        StringBuilder builder = new StringBuilder();
        builder.append("解析结果:\n");
        builder.append("- 解析条目: ").append(parsedCount).append("\n");
        builder.append("- 新建任务: ").append(createdCount).append("\n");
        builder.append("- 已存在: ").append(duplicatedCount).append("\n");
        builder.append("\n条目明细:\n");

        int showCount = Math.min(lines.size(), MAX_ENTRY_LINES);
        for (int i = 0; i < showCount; i++) {
            builder.append(lines.get(i)).append("\n");
        }
        if (lines.size() > showCount) {
            builder.append("... 其余 ").append(lines.size() - showCount).append(" 条已省略\n");
        }

        String content = builder.toString();
        if (content.length() <= MAX_FETCHED_CONTENT_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_FETCHED_CONTENT_LENGTH) + "\n\n... [TRUNCATED]";
    }

    private void appendEntryLine(List<String> lines, String action, RssEntry entry) {
        String title = entry.title == null || entry.title.isBlank() ? "(无标题)" : entry.title;
        String magnet = entry.magnet == null ? "" : entry.magnet;
        if (magnet.length() > 80) {
            magnet = magnet.substring(0, 80) + "...";
        }
        lines.add("- [" + action + "] " + title + " | " + magnet);
    }

    private void applyRequest(ResourceRssSubscription entity, ResourceRssSubscriptionRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订阅源名称不能为空");
        }
        if (request.getFeedUrl() == null || request.getFeedUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RSS 地址不能为空");
        }
        if (request.getLibraryId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标媒体库不能为空");
        }

        MediaLibrary library = mediaLibraryRepository.findById(request.getLibraryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标媒体库不存在"));

        entity.setName(request.getName().trim());
        entity.setFeedUrl(request.getFeedUrl().trim());
        entity.setLibrary(library);
        entity.setIntervalMinutes(Math.max(1, request.getIntervalMinutes() == null ? 30 : request.getIntervalMinutes()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
    }

    private ResourceSearchVO.RssSubscriptionItem toVO(ResourceRssSubscription entity) {
        return ResourceSearchVO.RssSubscriptionItem.builder()
                .id(entity.getId())
                .name(entity.getName())
                .feedUrl(entity.getFeedUrl())
                .libraryId(entity.getLibrary() != null ? entity.getLibrary().getId() : null)
                .libraryName(entity.getLibrary() != null ? entity.getLibrary().getName() : null)
                .intervalMinutes(entity.getIntervalMinutes())
                .enabled(entity.getEnabled())
                .lastCheckedAt(entity.getLastCheckedAt())
                .lastSuccessAt(entity.getLastSuccessAt())
                .lastError(entity.getLastError())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private record RssEntry(String title, String link, String magnet) {
    }
}
