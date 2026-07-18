package cn.iocoder.yudao.module.cloudmold.dreamplant.service.worker;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;

import java.util.*;

final class DreamPlantJsonCanonicalizer {
    private DreamPlantJsonCanonicalizer() {
    }

    static String canonicalize(Object value) {
        return JsonUtils.toJsonString(normalize(value));
    }

    static Object parse(String json) {
        return normalize(JsonUtils.parseObject(json, Object.class));
    }

    @SuppressWarnings("unchecked")
    private static Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new TreeMap<>();
            map.forEach((key, child) -> normalized.put(String.valueOf(key), normalize(child)));
            return normalized;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object child : list) {
                normalized.add(normalize(child));
            }
            return normalized;
        }
        if (value instanceof Number || value instanceof Boolean || value == null) {
            return value;
        }
        return String.valueOf(value);
    }

}
