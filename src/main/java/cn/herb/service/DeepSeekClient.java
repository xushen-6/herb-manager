package cn.herb.service;

import cn.herb.model.Medicinal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * DeepSeek OpenAI-compatible chat client.
 *
 * <p>The API key is supplied at runtime only. It is never written to source,
 * logs, or the database. The caller can keep the client disabled by passing
 * an empty key, which is useful for the local knowledge-base fallback.</p>
 */
public final class DeepSeekClient {
    public static final String DEFAULT_ENDPOINT = "https://api.deepseek.com/chat/completions";
    public static final String DEFAULT_MODEL = "deepseek-v4-flash";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String endpoint;
    private final String model;

    public DeepSeekClient(String apiKey, String endpoint, String model) {
        this.apiKey = clean(apiKey);
        this.endpoint = normalizeEndpoint(endpoint);
        this.model = clean(model).isEmpty() ? DEFAULT_MODEL : clean(model);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean enabled() {
        return !apiKey.isEmpty();
    }

    /** Ask DeepSeek with a compact, local medicinal context. */
    public String answer(String question, List<Medicinal> context) {
        if (!enabled()) throw new IllegalStateException("DeepSeek API 未配置");
        String system = "你是本草中药材智能管理平台的仓储与知识助手。"
                + "请使用简体中文回答，优先依据给定药材档案和库存上下文。"
                + "涉及疾病诊断、处方、剂量或治疗时，必须提醒用户咨询执业医师或药师，"
                + "不能把课程资料当作医疗建议。回答清晰、简洁，不要编造库存数据。";
        String user = buildUserPrompt(question, context);
        String body = "{\"model\":\"" + jsonEscape(model) + "\","
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + jsonEscape(system) + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + jsonEscape(user) + "\"}"
                + "],\"stream\":false}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("DeepSeek 服务暂时不可用（HTTP " + response.statusCode() + "）");
            }
            String content = jsonString(response.body(), "content");
            if (content.isBlank()) throw new IllegalStateException("DeepSeek 未返回有效回答");
            return content.trim();
        } catch (IOException e) {
            throw new IllegalStateException("DeepSeek 服务连接失败，请检查网络或 API 配置");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DeepSeek 请求被中断");
        }
    }

    private static String buildUserPrompt(String question, List<Medicinal> context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("用户问题：\n").append(clean(question)).append("\n\n");
        prompt.append("药材档案与库存上下文（仅将其作为参考）：\n");
        if (context == null || context.isEmpty()) {
            prompt.append("暂无匹配药材档案。\n");
        } else {
            int limit = Math.min(12, context.size());
            for (int i = 0; i < limit; i++) {
                Medicinal item = context.get(i);
                prompt.append("- ").append(item.name()).append("（编号 ").append(item.code()).append("）")
                        .append("；分类：").append(item.category())
                        .append("；当前库存：").append(item.stock()).append(" 克")
                        .append("；库存基准：").append(item.threshold()).append(" 克")
                        .append("；功效：").append(item.efficacy())
                        .append("；用法：").append(item.usage())
                        .append("；储存：").append(item.storage()).append("。\n");
            }
        }
        return prompt.toString();
    }

    private static String normalizeEndpoint(String raw) {
        String value = clean(raw);
        if (value.isEmpty()) return DEFAULT_ENDPOINT;
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.endsWith("/chat/completions")) return value;
        if (value.endsWith("/v1")) return value + "/chat/completions";
        return value + "/chat/completions";
    }

    private static String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) escaped.append(String.format("\\u%04x", (int) c));
                    else escaped.append(c);
                }
            }
        }
        return escaped.toString();
    }

    /** Minimal JSON string reader; avoids adding a new JSON framework to the desktop app. */
    private static String jsonString(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyStart = json.indexOf(marker);
        if (keyStart < 0) return "";
        int colon = json.indexOf(':', keyStart + marker.length());
        if (colon < 0) return "";
        int quote = colon + 1;
        while (quote < json.length() && Character.isWhitespace(json.charAt(quote))) quote++;
        if (quote >= json.length() || json.charAt(quote) != '"') return "";
        StringBuilder result = new StringBuilder();
        for (int i = quote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') return result.toString();
            if (c != '\\') {
                result.append(c);
                continue;
            }
            if (++i >= json.length()) break;
            char escaped = json.charAt(i);
            switch (escaped) {
                case '"' -> result.append('"');
                case '\\' -> result.append('\\');
                case '/' -> result.append('/');
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    if (i + 4 >= json.length()) return result.toString();
                    String hex = json.substring(i + 1, i + 5);
                    try {
                        result.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    } catch (NumberFormatException ignored) {
                        result.append('?');
                    }
                }
                default -> result.append(escaped);
            }
        }
        return result.toString();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
