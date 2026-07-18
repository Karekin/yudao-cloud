package cn.iocoder.yudao.module.cloudmold.mcp;

import cn.iocoder.yudao.module.cloudmold.executor.CapabilityContract;
import cn.iocoder.yudao.module.cloudmold.executor.CapabilityContractDescriber;
import cn.iocoder.yudao.module.cloudmold.executor.CapabilityDescriptor;
import cn.iocoder.yudao.module.cloudmold.executor.CapabilityOperationType;
import cn.iocoder.yudao.module.cloudmold.executor.CloudMoldCapabilityCatalog;
import cn.iocoder.yudao.module.cloudmold.executor.CloudMoldCapabilityExecutor;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcCallContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class CloudMoldMcpTools {

    static final String LIST_TOOL_NAME = "cloudmold_capability_list";
    static final String DESCRIBE_TOOL_NAME = "cloudmold_capability_describe";
    static final String INVOKE_READ_TOOL_NAME = "cloudmold_capability_read_invoke";

    private final CloudMoldCapabilityCatalog catalog;
    private final CapabilityContractDescriber contractDescriber;
    private final CloudMoldCapabilityExecutor executor;
    private final ObjectMapper objectMapper;

    public CloudMoldMcpTools(CloudMoldCapabilityCatalog catalog, CapabilityContractDescriber contractDescriber,
                             CloudMoldCapabilityExecutor executor, ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.contractDescriber = contractDescriber;
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    McpSchema.Tool listTool() {
        return readOnlyTool(LIST_TOOL_NAME, "List governed CloudMold Dubbo capabilities",
                objectSchema(Map.of(
                        "operationType", Map.of("type", "string", "enum", List.of("READ", "WRITE"))
                ), List.of()));
    }

    McpSchema.Tool describeTool() {
        return readOnlyTool(DESCRIBE_TOOL_NAME, "Describe one governed capability and its JSON schemas",
                objectSchema(Map.of("capabilityId", Map.of("type", "string", "minLength", 1)),
                        List.of("capabilityId")));
    }

    McpSchema.Tool invokeReadTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("capabilityId", Map.of("type", "string", "minLength", 1));
        properties.put("arguments", Map.of("type", "array"));
        properties.put("tenantId", Map.of("type", "integer", "minimum", 1));
        properties.put("operatorId", Map.of("type", "integer", "minimum", 1));
        properties.put("operatorType", Map.of("type", "integer", "minimum", 1));
        properties.put("skillId", Map.of("type", "string", "minLength", 1));
        properties.put("runId", Map.of("type", "string", "minLength", 1));
        return readOnlyTool(INVOKE_READ_TOOL_NAME,
                "Invoke one allowlisted READ capability through signed, tenant-aware Dubbo",
                objectSchema(properties, List.of("capabilityId", "arguments", "tenantId", "operatorId",
                        "operatorType", "skillId", "runId")));
    }

    McpSchema.CallToolResult list(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        return guarded(() -> {
            String requestedType = optionalString(request.arguments(), "operationType");
            CapabilityOperationType filter = requestedType == null ? null
                    : CapabilityOperationType.valueOf(requestedType.toUpperCase(Locale.ROOT));
            List<Map<String, Object>> capabilities = catalog.all().stream()
                    .filter(value -> filter == null || value.operationType() == filter)
                    .map(CloudMoldMcpTools::capabilitySummary)
                    .toList();
            return Map.of("count", capabilities.size(), "capabilities", capabilities);
        });
    }

    McpSchema.CallToolResult describe(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        return guarded(() -> asStructured(contractDescriber.describe(
                requiredString(request.arguments(), "capabilityId"))));
    }

    McpSchema.CallToolResult invokeRead(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        return guarded(() -> {
            Map<String, Object> arguments = request.arguments();
            String capabilityId = requiredString(arguments, "capabilityId");
            CapabilityDescriptor descriptor = catalog.require(capabilityId);
            if (descriptor.operationType() != CapabilityOperationType.READ) {
                throw new SecurityException("MCP direct invocation is restricted to READ capabilities: "
                        + capabilityId);
            }
            CloudMoldRpcCallContext context = new CloudMoldRpcCallContext(
                    positiveLong(arguments, "tenantId"),
                    positiveLong(arguments, "operatorId"),
                    Math.toIntExact(positiveLong(arguments, "operatorType")),
                    requiredString(arguments, "skillId"),
                    requiredString(arguments, "runId"));
            JsonNode argumentArray = objectMapper.valueToTree(required(arguments, "arguments"));
            JsonNode result = executor.execute(capabilityId, argumentArray, context, false);
            return Map.of("capabilityId", capabilityId, "runId", context.runId(), "status", "SUCCEEDED",
                    "result", asStructured(result));
        });
    }

    private McpSchema.CallToolResult guarded(StructuredCall call) {
        try {
            Object structured = call.execute();
            return McpSchema.CallToolResult.builder()
                    .content(List.of(McpSchema.TextContent.builder(objectMapper.writeValueAsString(structured)).build()))
                    .structuredContent(structured)
                    .isError(false)
                    .build();
        } catch (IllegalArgumentException | SecurityException ex) {
            return error(ex);
        } catch (RuntimeException ex) {
            return error(new IllegalStateException("Capability service is unavailable", ex));
        } catch (JsonProcessingException ex) {
            return error(new IllegalStateException("Cannot serialize MCP result", ex));
        }
    }

    private McpSchema.CallToolResult error(Exception error) {
        Map<String, Object> structured = Map.of(
                "status", "FAILED",
                "errorType", error.getClass().getSimpleName(),
                "message", error.getMessage() == null ? "Request failed" : error.getMessage());
        try {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(McpSchema.TextContent.builder(objectMapper.writeValueAsString(structured)).build()))
                    .structuredContent(structured)
                    .isError(true)
                    .build();
        } catch (JsonProcessingException serializationError) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(McpSchema.TextContent.builder("MCP request failed").build()))
                    .isError(true)
                    .build();
        }
    }

    private Object asStructured(Object value) {
        return objectMapper.convertValue(value, Object.class);
    }

    private static Map<String, Object> capabilitySummary(CapabilityDescriptor value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capabilityId", value.capabilityId());
        result.put("interfaceName", value.interfaceName());
        result.put("methodName", value.methodName());
        result.put("parameterTypes", value.parameterTypes());
        result.put("returnType", value.returnType());
        result.put("operationType", value.operationType().name());
        result.put("group", value.group());
        result.put("version", value.version());
        result.put("timeoutMillis", value.timeoutMillis());
        return result;
    }

    private static McpSchema.Tool readOnlyTool(String name, String description, Map<String, Object> schema) {
        return McpSchema.Tool.builder(name, schema)
                .description(description)
                .annotations(McpSchema.ToolAnnotations.builder()
                        .readOnlyHint(true)
                        .destructiveHint(false)
                        .idempotentHint(true)
                        .openWorldHint(false)
                        .build())
                .build();
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private static Object required(Map<String, Object> arguments, String name) {
        Object value = arguments == null ? null : arguments.get(name);
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String requiredString(Map<String, Object> arguments, String name) {
        Object value = required(arguments, name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-blank string");
        }
        return text.trim();
    }

    private static String optionalString(Map<String, Object> arguments, String name) {
        Object value = arguments == null ? null : arguments.get(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-blank string");
        }
        return text.trim();
    }

    private static long positiveLong(Map<String, Object> arguments, String name) {
        Object value = required(arguments, name);
        if (!(value instanceof Number number) || number.longValue() <= 0) {
            throw new IllegalArgumentException(name + " must be a positive integer");
        }
        return number.longValue();
    }

    @FunctionalInterface
    private interface StructuredCall {
        Object execute();
    }
}
