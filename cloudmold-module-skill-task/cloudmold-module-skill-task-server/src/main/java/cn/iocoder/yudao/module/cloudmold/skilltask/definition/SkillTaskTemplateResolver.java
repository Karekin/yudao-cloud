package cn.iocoder.yudao.module.cloudmold.skilltask.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;

@Component
public class SkillTaskTemplateResolver {

    private static final String INPUT_PREFIX = "$input.";
    private static final String STEPS_PREFIX = "$steps.";

    private final ObjectMapper objectMapper;

    public SkillTaskTemplateResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ArrayNode resolve(JsonNode template, JsonNode input, Map<String, JsonNode> stepResults,
                             String taskId, String stepIdempotencyKey) {
        JsonNode resolved = resolveNode(template, input, stepResults, taskId, stepIdempotencyKey);
        if (!resolved.isArray()) {
            throw new IllegalArgumentException("Resolved step arguments are not an array");
        }
        return (ArrayNode) resolved;
    }

    private JsonNode resolveNode(JsonNode value, JsonNode input, Map<String, JsonNode> stepResults,
                                 String taskId, String stepIdempotencyKey) {
        if (value.isTextual()) {
            String text = value.asText();
            if ("$task.id".equals(text) || "$task.runId".equals(text)) {
                return objectMapper.getNodeFactory().textNode(taskId);
            }
            if ("$task.stepIdempotencyKey".equals(text)) {
                return objectMapper.getNodeFactory().textNode(stepIdempotencyKey);
            }
            if (text.startsWith(INPUT_PREFIX)) {
                return requirePath(input, text.substring(INPUT_PREFIX.length()), text);
            }
            if (text.startsWith(STEPS_PREFIX)) {
                String expression = text.substring(STEPS_PREFIX.length());
                int separator = expression.indexOf(".result");
                if (separator <= 0) {
                    throw new IllegalArgumentException("Invalid step result expression: " + text);
                }
                String stepCode = expression.substring(0, separator);
                JsonNode result = stepResults.get(stepCode);
                if (result == null) {
                    throw new IllegalArgumentException("Step result is unavailable: " + stepCode);
                }
                String path = expression.substring(separator + ".result".length());
                return path.isEmpty() ? result.deepCopy() : requirePath(result, path.substring(1), text);
            }
            return value.deepCopy();
        }
        if (value.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            value.forEach(item -> result.add(resolveNode(item, input, stepResults, taskId, stepIdempotencyKey)));
            return result;
        }
        if (value.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            fields.forEachRemaining(entry -> result.set(entry.getKey(),
                    resolveNode(entry.getValue(), input, stepResults, taskId, stepIdempotencyKey)));
            return result;
        }
        return value.deepCopy();
    }

    private static JsonNode requirePath(JsonNode root, String dottedPath, String expression) {
        JsonNode current = root;
        for (String segment : dottedPath.split("\\.")) {
            current = current.path(segment);
            if (current.isMissingNode()) {
                throw new IllegalArgumentException("Template value is missing: " + expression);
            }
        }
        return current.deepCopy();
    }
}
