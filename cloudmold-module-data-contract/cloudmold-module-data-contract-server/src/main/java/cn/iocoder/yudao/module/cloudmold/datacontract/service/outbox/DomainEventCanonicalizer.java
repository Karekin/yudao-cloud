package cn.iocoder.yudao.module.cloudmold.datacontract.service.outbox;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class DomainEventCanonicalizer {

    private DomainEventCanonicalizer() {
    }

    static CanonicalPayload canonicalize(Map<String, Object> payload) {
        JsonNode tree = JsonUtils.getObjectMapper().valueToTree(payload);
        String json = JsonUtils.toJsonString(sort(tree));
        return new CanonicalPayload(json, DigestUtil.sha256Hex(json));
    }

    private static JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            names.forEach(name -> result.set(name, sort(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            node.forEach(child -> result.add(sort(child)));
            return result;
        }
        return node;
    }

    record CanonicalPayload(String json, String sha256) {
    }

}
