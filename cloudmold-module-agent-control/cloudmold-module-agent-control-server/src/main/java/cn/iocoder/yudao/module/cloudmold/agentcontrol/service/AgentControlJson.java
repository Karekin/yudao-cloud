package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class AgentControlJson {

    private static final Set<String> FORBIDDEN_EXECUTION_KEYS = Set.of(
            "tenantid", "operatorid", "operatortype", "runid", "approvalref", "approvalscope",
            "idempotencykey", "clientrequestkey", "skilltaskid", "skillid", "skillversion");

    private AgentControlJson() {
    }

    static String canonicalBusinessObject(String value, String field) {
        try {
            JsonNode parsed = JsonUtils.getObjectMapper().readTree(value);
            if (parsed == null || !parsed.isObject()) {
                throw new IllegalArgumentException(field + " must contain a JSON object");
            }
            rejectForbiddenKeys(parsed, field);
            return JsonUtils.getObjectMapper().writeValueAsString(sort(parsed));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(field + " must contain valid JSON", exception);
        }
    }

    static String sha256(String canonicalJson) {
        return DigestUtil.sha256Hex(canonicalJson);
    }

    private static void rejectForbiddenKeys(JsonNode value, String field) {
        if (value.isObject()) {
            value.fieldNames().forEachRemaining(name -> {
                String normalized = name.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
                if (FORBIDDEN_EXECUTION_KEYS.contains(normalized)) {
                    throw new IllegalArgumentException(field + " must not contain technical execution parameters");
                }
                rejectForbiddenKeys(value.get(name), field);
            });
        } else if (value.isArray()) {
            value.forEach(item -> rejectForbiddenKeys(item, field));
        }
    }

    private static JsonNode sort(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = JsonUtils.getObjectMapper().createObjectNode();
            List<String> names = new ArrayList<>();
            value.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> result.set(name, sort(value.get(name))));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = JsonUtils.getObjectMapper().createArrayNode();
            value.forEach(item -> result.add(sort(item)));
            return result;
        }
        return value.deepCopy();
    }

}
