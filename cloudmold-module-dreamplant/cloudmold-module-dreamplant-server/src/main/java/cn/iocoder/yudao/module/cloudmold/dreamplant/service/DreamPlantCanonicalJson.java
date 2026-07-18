package cn.iocoder.yudao.module.cloudmold.dreamplant.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Produces a stable DreamPlant document representation. Object fields are sorted recursively while array order,
 * which is meaningful to the world-map projection, is retained.
 */
public final class DreamPlantCanonicalJson {

    private DreamPlantCanonicalJson() {
    }

    public static CanonicalDocument canonicalize(String json) {
        JsonNode canonicalNode = sort(JsonUtils.parseTree(json));
        String canonicalJson = JsonUtils.toJsonString(canonicalNode);
        return new CanonicalDocument(canonicalJson, DigestUtil.sha256Hex(canonicalJson));
    }

    private static JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = JsonUtils.getObjectMapper().createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.stream().sorted(Comparator.naturalOrder()).forEach(name -> sorted.set(name, sort(node.get(name))));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = JsonUtils.getObjectMapper().createArrayNode();
            node.forEach(child -> sorted.add(sort(child)));
            return sorted;
        }
        return node.deepCopy();
    }

    public record CanonicalDocument(String json, String sha256) {
    }
}
