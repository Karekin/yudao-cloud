package cn.iocoder.yudao.module.cloudmold.skilltask.service;

import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class SkillTaskJson {

    private final ObjectMapper objectMapper;
    private final SkillTaskProperties properties;

    public SkillTaskJson(ObjectMapper objectMapper, SkillTaskProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public JsonNode parse(String json, String field) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(field + " must contain valid JSON", ex);
        }
    }

    public ObjectNode parseObject(String json, String field) {
        JsonNode value = parse(json, field);
        if (!value.isObject()) {
            throw new IllegalArgumentException(field + " must be a JSON object");
        }
        return (ObjectNode) value;
    }

    public ObjectNode objectNode() {
        return objectMapper.createObjectNode();
    }

    public String canonical(JsonNode value) {
        try {
            String result = objectMapper.writeValueAsString(sort(value));
            if (result.getBytes(StandardCharsets.UTF_8).length > properties.getMaxPayloadBytes()) {
                throw new IllegalArgumentException("JSON payload exceeds " + properties.getMaxPayloadBytes() + " bytes");
            }
            return result;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Cannot serialize JSON payload", ex);
        }
    }

    public String sha256(String canonicalJson) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private JsonNode sort(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            List<String> names = new ArrayList<>();
            value.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> result.set(name, sort(value.get(name))));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            value.forEach(item -> result.add(sort(item)));
            return result;
        }
        return value.deepCopy();
    }
}
