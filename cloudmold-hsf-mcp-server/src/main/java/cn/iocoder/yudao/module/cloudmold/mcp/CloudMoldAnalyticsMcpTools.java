package cn.iocoder.yudao.module.cloudmold.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CloudMoldAnalyticsMcpTools {
    static final String SNAPSHOT_TOOL_NAME = "cloudmold_analytics_dashboard_snapshot";
    static final String METRIC_VALUE_TOOL_NAME = "cloudmold_analytics_metric_value";
    static final String METRIC_DEFINITION_TOOL_NAME = "cloudmold_analytics_metric_definition";

    private final CloudMoldAnalyticsProperties properties;
    private final ObjectMapper objectMapper;

    public CloudMoldAnalyticsMcpTools(CloudMoldAnalyticsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    McpSchema.Tool snapshotTool() {
        return readOnlyTool(SNAPSHOT_TOOL_NAME, "Read the governed CloudMold analytics dashboard snapshot",
                objectSchema(Map.of(), List.of()));
    }

    McpSchema.Tool metricValueTool() {
        return readOnlyTool(METRIC_VALUE_TOOL_NAME, "Read one governed CloudMold metric value and evidence metadata",
                objectSchema(Map.of("metricId", Map.of("type", "string", "minLength", 1)), List.of("metricId")));
    }

    McpSchema.Tool metricDefinitionTool() {
        return readOnlyTool(METRIC_DEFINITION_TOOL_NAME, "Read one governed CloudMold metric definition and lineage",
                objectSchema(Map.of("metricId", Map.of("type", "string", "minLength", 1)), List.of("metricId")));
    }

    McpSchema.CallToolResult snapshot(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        return guarded(() -> readJson(properties.snapshotPath()));
    }

    McpSchema.CallToolResult metricValue(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        return guarded(() -> {
            String metricId = requiredString(request.arguments(), "metricId");
            JsonNode snapshot = readJson(properties.snapshotPath());
            JsonNode metric = snapshot.path("metrics").path(metricId);
            if (metric.isMissingNode() || metric.isNull()) throw new IllegalArgumentException("Unknown metricId: " + metricId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("metricId", metricId);
            result.put("snapshotId", text(snapshot, "snapshot_id"));
            result.put("generatedAt", text(snapshot, "generated_at"));
            result.put("evidenceScope", text(snapshot, "evidence_scope"));
            result.put("tenantLabel", text(snapshot, "tenant_label"));
            result.put("reading", objectMapper.convertValue(metric, Object.class));
            result.put("warning", text(snapshot, "warning"));
            return result;
        });
    }

    McpSchema.CallToolResult metricDefinition(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        return guarded(() -> {
            String metricId = requiredString(request.arguments(), "metricId");
            JsonNode catalog = readJson(properties.catalogPath());
            for (JsonNode metric : catalog.path("metrics")) {
                if (metricId.equals(metric.path("id").asText())) return objectMapper.convertValue(metric, Object.class);
            }
            throw new IllegalArgumentException("Unknown metricId: " + metricId);
        });
    }

    private JsonNode readJson(java.nio.file.Path path) {
        try {
            if (!Files.isRegularFile(path)) throw new IllegalStateException("Analytics data file is unavailable: " + path);
            return objectMapper.readTree(Files.readString(path));
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read governed analytics data", ex);
        }
    }

    private McpSchema.CallToolResult guarded(StructuredCall call) {
        try {
            Object structured = call.execute();
            return McpSchema.CallToolResult.builder().content(List.of(McpSchema.TextContent.builder(writeJson(structured)).build()))
                    .structuredContent(structured).isError(false).build();
        } catch (RuntimeException ex) { return error(ex); }
    }

    private McpSchema.CallToolResult error(Exception error) {
        Map<String, Object> result = Map.of("status", "FAILED", "errorType", error.getClass().getSimpleName(),
                "message", error.getMessage() == null ? "Request failed" : error.getMessage());
        return McpSchema.CallToolResult.builder().content(List.of(McpSchema.TextContent.builder(writeJson(result)).build()))
                .structuredContent(result).isError(true).build();
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot serialize analytics result", ex); }
    }

    private static String requiredString(Map<String, Object> arguments, String name) {
        Object value = arguments == null ? null : arguments.get(name);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(name + " must be a non-blank string");
        return text.trim();
    }

    private static String text(JsonNode node, String field) { return node.path(field).isMissingNode() ? null : node.path(field).asText(); }

    private static McpSchema.Tool readOnlyTool(String name, String description, Map<String, Object> schema) {
        return McpSchema.Tool.builder(name, schema).description(description)
                .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(true).destructiveHint(false)
                        .idempotentHint(true).openWorldHint(false).build()).build();
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object"); schema.put("additionalProperties", false);
        schema.put("properties", properties); schema.put("required", required); return schema;
    }

    @FunctionalInterface private interface StructuredCall { Object execute(); }
}
