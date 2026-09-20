package com.deliveryweb.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * JSON 工具：请求体解析为 Map、标准 JSON 响应输出，
 * 并提供 str / num 取值辅助。LocalDateTime 统一按 "yyyy-MM-dd HH:mm:ss" 序列化。
 */
public final class JsonUtil {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        SimpleModule dateModule = new SimpleModule();
        dateModule.addSerializer(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
            @Override
            public void serialize(LocalDateTime value,
                                  com.fasterxml.jackson.core.JsonGenerator gen,
                                  SerializerProvider serializers) throws IOException {
                gen.writeString(value == null ? null : value.format(DATE_TIME_FORMATTER));
            }
        });
        MAPPER.registerModule(dateModule);
    }

    private JsonUtil() {
    }

    /**
     * 读取 JSON 请求体并解析为 Map；空请求体返回空 Map。
     */
    public static Map<String, Object> parseBody(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            throw new BizException("请求体读取失败");
        }
        if (sb.toString().trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return MAPPER.readValue(sb.toString(), new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            throw new BizException("请求 JSON 格式错误");
        }
    }

    /**
     * 输出标准 JSON 响应。
     */
    public static void writeJson(HttpServletResponse response, Result result) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        MAPPER.writeValue(response.getWriter(), result);
    }

    /**
     * 取字符串参数，自动去除首尾空格；为空时返回 null。
     */
    public static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 取整型参数。
     */
    public static Integer num(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 取长整型参数（主键 ID 统一走此方法，替代 num 结果的 longValue 拆箱模板；
     * 直解析 Long，不经 Integer 中转——与 BaseApiServlet.longParam 同口径，审查 P3-2）。
     */
    public static Long longValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 取布尔参数：兼容 true 布尔与 "true" 字符串两种形式（原 ShopServlet/AddressServlet 各自的 isTrue 归一）。
     */
    public static boolean isTrue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value));
    }

    /**
     * 文本去首尾空格，空串归一为 null（原 UserService/ShopService 各自的 trimToNull 归一）。
     */
    public static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
