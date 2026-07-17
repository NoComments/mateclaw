package vip.mate.tool.builtin;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内置工具：轻量级网页抓取（HTTP + Jsoup）。
 *
 * <p>定位：补足 {@code WebSearchTool}（只回摘要）和 {@code BrowserUseTool}（CDP 真浏览器，重）
 * 之间的空档——适合定时巡检静态/半静态网站列表页，例如政府公告、行业资讯。
 *
 * <p>已内建保护：
 * <ul>
 *   <li>统一 UA 标识 {@value #USER_AGENT}，便于目标站点识别/限速</li>
 *   <li>每域名最小间隔 {@value #MIN_INTERVAL_MS} ms，超频时调用方被动等待</li>
 *   <li>10 秒连接/读取超时，1 MB 响应上限</li>
 *   <li>HTTPS 与 HTTP 均支持；不跟踪 cookie；最多 3 跳重定向</li>
 * </ul>
 *
 * <p><b>不做</b>的事：robots.txt 解析、JS 渲染、登录态。需要这些能力请用
 * {@code browser_use}。调用方需自行确保目标站允许抓取。
 */
@Slf4j
@Component
public class WebFetchTool {

    private static final String USER_AGENT =
            "MateClawBot/1.0 (+https://github.com/qingclaws/mateclaw; harvest)";
    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final long MIN_INTERVAL_MS = 1500;
    private static final int MAX_REDIRECTS = 3;

    /** host → 上次抓取时间戳；用于 per-host 节流。 */
    private final ConcurrentHashMap<String, Long> lastFetchByHost = new ConcurrentHashMap<>();

    @Tool(description = """
            Fetch a single web page and return cleaned content.

            Use this for harvesting static / list-style pages (gov notices,
            blog indexes, press release lists). For JS-heavy pages, use
            browser_use. For search-engine queries, use search.

            Modes:
              - text  (default) : visible text only, scripts/styles stripped
              - html            : raw HTML, capped at 1 MB
              - links           : one "title<TAB>absolute_url" per line, deduped
            """)
    public String web_fetch(
            @ToolParam(description = "Absolute URL to fetch (http or https)") String url,
            @ToolParam(description = "Output mode: text | html | links. Default: text", required = false) String mode
    ) {
        if (url == null || url.isBlank()) {
            return "[web_fetch] error: url is required";
        }
        String normalizedMode = (mode == null || mode.isBlank()) ? "text" : mode.trim().toLowerCase();
        if (!normalizedMode.equals("text") && !normalizedMode.equals("html") && !normalizedMode.equals("links")) {
            return "[web_fetch] error: mode must be one of text|html|links, got: " + mode;
        }

        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return "[web_fetch] error: invalid url: " + e.getMessage();
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return "[web_fetch] error: only http/https supported, got scheme: " + scheme;
        }
        String host = uri.getHost();
        if (host == null) {
            return "[web_fetch] error: url missing host";
        }

        throttle(host);

        Document doc;
        try {
            doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_BYTES)
                    .followRedirects(true)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .ignoreContentType(false)
                    .get();
        } catch (Exception e) {
            log.warn("[web_fetch] fetch failed url={} err={}", url, e.getMessage());
            return "[web_fetch] error: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        return switch (normalizedMode) {
            case "html" -> doc.outerHtml();
            case "links" -> extractLinks(doc);
            default -> extractText(doc);
        };
    }

    private void throttle(String host) {
        long now = System.currentTimeMillis();
        Long last = lastFetchByHost.get(host);
        if (last != null) {
            long wait = MIN_INTERVAL_MS - (now - last);
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        lastFetchByHost.put(host, System.currentTimeMillis());
    }

    private String extractText(Document doc) {
        doc.select("script, style, noscript, iframe, svg").remove();
        String title = doc.title();
        String body = doc.body() != null ? doc.body().text() : "";
        StringBuilder sb = new StringBuilder();
        if (!title.isBlank()) {
            sb.append("# ").append(title).append("\n\n");
        }
        sb.append(body);
        return sb.toString();
    }

    private String extractLinks(Document doc) {
        Elements anchors = doc.select("a[href]");
        Set<String> seen = new LinkedHashSet<>();
        StringBuilder sb = new StringBuilder();
        for (Element a : anchors) {
            String href = a.absUrl("href");
            if (href.isBlank() || href.startsWith("javascript:") || href.startsWith("#")) {
                continue;
            }
            String text = a.text().trim();
            if (text.isBlank()) {
                continue;
            }
            String line = text + "\t" + href;
            if (seen.add(line)) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
