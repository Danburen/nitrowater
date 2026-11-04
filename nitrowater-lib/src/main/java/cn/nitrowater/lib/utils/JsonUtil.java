package cn.nitrowater.lib.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared JSON helper (Jackson 3 — {@code tools.jackson}). Internally used by the caching/IO layers
 * and by the business modules for their JSON columns; keeping one mapper here keeps the JSON
 * behaviour consistent across modules.
 *
 * <p>Public signatures expose Jackson types, so this module exports {@code jackson-databind} as an
 * {@code api} dependency.</p>
 */
public final class JsonUtil {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    private static final JsonMapper SNAKE_MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    private JsonUtil() {
    }

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize to JSON", ex);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (StringUtil.isBlank(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parseList JSON", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        if (StringUtil.isBlank(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parseList JSON", e);
        }
    }

    public static <T> T fromJsonSnakeCase(String json, Class<T> clazz) {
        if (json == null) {
            return null;
        }
        try {
            return SNAKE_MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    public static <T> List<T> parseList(String json, TypeReference<List<T>> type) {
        List<T> value = fromJson(json, type);
        return value == null ? List.of() : value;
    }

    public static <T> String mergeJson(String stored, List<T> extra,
                                       TypeReference<List<T>> type, java.util.function.Function<T, String> key) {
        List<T> merged = new ArrayList<>(parseList(stored, type));
        Set<String> seen = new LinkedHashSet<>();
        for (T entry : merged) {
            seen.add(key.apply(entry));
        }
        if (extra != null) {
            for (T entry : extra) {
                if (seen.add(key.apply(entry))) {
                    merged.add(entry);
                }
            }
        }
        return merged.isEmpty() ? null : toJson(merged);
    }
}
