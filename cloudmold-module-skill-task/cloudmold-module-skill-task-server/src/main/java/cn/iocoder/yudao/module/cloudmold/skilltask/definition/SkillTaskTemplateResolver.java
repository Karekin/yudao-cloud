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
        JsonNode resolved = resolveValue(template, input, stepResults, taskId, taskId, stepIdempotencyKey);
        if (!resolved.isArray()) {
            throw new IllegalArgumentException("Resolved step arguments are not an array");
        }
        return (ArrayNode) resolved;
    }

    public JsonNode resolveValue(JsonNode template, JsonNode input, Map<String, JsonNode> stepResults,
                                 String taskId, String runId, String stepIdempotencyKey) {
        if (template == null) {
            throw new IllegalArgumentException("Step template is required");
        }
        return resolveNode(template, input, stepResults, taskId, runId, stepIdempotencyKey);
    }

    private JsonNode resolveNode(JsonNode value, JsonNode input, Map<String, JsonNode> stepResults,
                                 String taskId, String runId, String stepIdempotencyKey) {
        if (value.isObject() && value.has("$object") && value.has("$overrides") && value.size() == 2) {
            JsonNode base = resolveNode(value.get("$object"), input, stepResults, taskId, runId,
                    stepIdempotencyKey);
            JsonNode overrides = value.get("$overrides");
            if (!base.isObject() || !overrides.isObject()) {
                throw new IllegalArgumentException("$object template requires object base and overrides");
            }
            ObjectNode result = (ObjectNode) base.deepCopy();
            overrides.fields().forEachRemaining(entry -> {
                JsonNode resolved = resolveNode(entry.getValue(), input, stepResults, taskId, runId,
                        stepIdempotencyKey);
                if (entry.getKey().startsWith("/")) {
                    replaceAtPointer(result, entry.getKey(), resolved);
                } else {
                    result.set(entry.getKey(), resolved);
                }
            });
            return result;
        }
        if (value.isTextual()) {
            String text = value.asText();
            if ("$task.id".equals(text)) {
                return objectMapper.getNodeFactory().textNode(taskId);
            }
            if ("$task.runId".equals(text)) {
                return objectMapper.getNodeFactory().textNode(runId);
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
            value.forEach(item -> result.add(resolveNode(item, input, stepResults, taskId, runId,
                    stepIdempotencyKey)));
            return result;
        }
        if (value.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            fields.forEachRemaining(entry -> result.set(entry.getKey(),
                    resolveNode(entry.getValue(), input, stepResults, taskId, runId, stepIdempotencyKey)));
            return result;
        }
        return value.deepCopy();
    }

    private static JsonNode requirePath(JsonNode root, String dottedPath, String expression) {
        JsonNode current = root;
        for (String segment : dottedPath.split("\\.")) {
            current = current.isArray() && segment.matches("0|[1-9][0-9]*")
                    ? current.path(Integer.parseInt(segment)) : current.path(segment);
            if (current.isMissingNode()) {
                throw new IllegalArgumentException("Template value is missing: " + expression);
            }
        }
        return current.deepCopy();
    }

    private static void replaceAtPointer(ObjectNode root, String pointer, JsonNode value) {
        String[] rawSegments = pointer.substring(1).split("/", -1);
        if (rawSegments.length == 0) {
            throw new IllegalArgumentException("$overrides JSON Pointer cannot replace the root object");
        }
        JsonNode parent = root;
        for (int index = 0; index < rawSegments.length - 1; index++) {
            String segment = decodePointerSegment(rawSegments[index]);
            parent = arrayIndex(parent, segment, pointer);
            if (parent == null || parent.isMissingNode()) {
                throw new IllegalArgumentException("$overrides path is missing: " + pointer);
            }
        }
        String leaf = decodePointerSegment(rawSegments[rawSegments.length - 1]);
        if (parent.isObject()) {
            if (!parent.has(leaf)) {
                throw new IllegalArgumentException("$overrides path is missing: " + pointer);
            }
            ((ObjectNode) parent).set(leaf, value);
        } else if (parent.isArray() && leaf.matches("0|[1-9][0-9]*")) {
            int arrayIndex = Integer.parseInt(leaf);
            if (arrayIndex >= parent.size()) {
                throw new IllegalArgumentException("$overrides array index is outside the base object: " + pointer);
            }
            ((ArrayNode) parent).set(arrayIndex, value);
        } else {
            throw new IllegalArgumentException("$overrides path is not replaceable: " + pointer);
        }
    }

    private static JsonNode arrayIndex(JsonNode parent, String segment, String pointer) {
        if (parent.isArray() && segment.matches("0|[1-9][0-9]*")) {
            int index = Integer.parseInt(segment);
            if (index >= parent.size()) {
                throw new IllegalArgumentException("$overrides array index is outside the base object: " + pointer);
            }
            return parent.path(index);
        }
        return parent.path(segment);
    }

    private static String decodePointerSegment(String value) {
        return value.replace("~1", "/").replace("~0", "~");
    }
}
