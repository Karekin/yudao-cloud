package cn.iocoder.yudao.module.cloudmold.mcp;

import cn.iocoder.yudao.module.cloudmold.executor.CloudMoldCapabilityExecutor;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcCallContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Durable R1 Skill Task control tools for Agent orchestrators.
 *
 * <p>MCP remains the control protocol. Every operation is executed through the
 * same typed, signed and tenant-aware Dubbo capability plane as other Skills;
 * MCP never calls a domain service or database directly.</p>
 */
@Component
public class CloudMoldSkillTaskMcpTools {

    static final String SUBMIT_TOOL_NAME = "cloudmold_skill_task_submit_r1";
    static final String GET_TOOL_NAME = "cloudmold_skill_task_get";
    static final String GET_BY_REQUEST_KEY_TOOL_NAME = "cloudmold_skill_task_get_by_request_key";
    static final String LIST_STEPS_TOOL_NAME = "cloudmold_skill_task_list_steps";
    static final String RETRY_TOOL_NAME = "cloudmold_skill_task_retry_r1";
    static final String FULL_CHAIN_SUBMIT_TOOL_NAME = "cloudmold_skill_task_submit_commerce_full_chain_r3";
    static final String FULL_CHAIN_RETRY_TOOL_NAME = "cloudmold_skill_task_retry_commerce_full_chain_r3";

    private static final String SUBMIT_CAPABILITY =
            "capability.cloudmold.skilltask.skill-task-command.submit.v1";
    private static final String RETRY_CAPABILITY =
            "capability.cloudmold.skilltask.skill-task-command.retry.v1";
    private static final String GET_CAPABILITY =
            "capability.cloudmold.skilltask.skill-task-query.get.v1";
    private static final String GET_BY_REQUEST_KEY_CAPABILITY =
            "capability.cloudmold.skilltask.skill-task-query.get-by-request-key.v1";
    private static final String LIST_STEPS_CAPABILITY =
            "capability.cloudmold.skilltask.skill-task-query.list-steps.v1";
    private static final String CONTROL_SKILL_ID = "skill.cloudmold.platform.mcp-task-control.v1";
    static final String FULL_CHAIN_SKILL_ID = "skill.cloudmold.commerce.full-chain-hsf.v1";
    static final String FULL_CHAIN_SKILL_VERSION = "1.2.0";

    private final CloudMoldCapabilityExecutor executor;
    private final ObjectMapper objectMapper;

    public CloudMoldSkillTaskMcpTools(CloudMoldCapabilityExecutor executor, ObjectMapper objectMapper) {
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    McpSchema.Tool submitTool() {
        Map<String, Object> properties = contextProperties();
        properties.put("skillId", stringSchema());
        properties.put("skillVersion", stringSchema());
        properties.put("clientRequestKey", stringSchema());
        properties.put("input", Map.of("type", "object"));
        return writeTool(SUBMIT_TOOL_NAME,
                "Submit one idempotent R1 business Skill to the durable CloudMold Dubbo executor",
                objectSchema(properties, List.of("tenantId", "operatorId", "operatorType", "controlRunId",
                        "skillId", "skillVersion", "clientRequestKey", "input")), true);
    }

    McpSchema.Tool getTool() {
        Map<String, Object> properties = contextProperties();
        properties.put("taskId", stringSchema());
        return readTool(GET_TOOL_NAME, "Read one durable Skill Task by task ID",
                objectSchema(properties, List.of("tenantId", "operatorId", "operatorType", "controlRunId",
                        "taskId")));
    }

    McpSchema.Tool getByRequestKeyTool() {
        Map<String, Object> properties = contextProperties();
        properties.put("skillId", stringSchema());
        properties.put("clientRequestKey", stringSchema());
        return readTool(GET_BY_REQUEST_KEY_TOOL_NAME, "Read one durable Skill Task by its idempotency key",
                objectSchema(properties, List.of("tenantId", "operatorId", "operatorType", "controlRunId",
                        "skillId", "clientRequestKey")));
    }

    McpSchema.Tool listStepsTool() {
        Map<String, Object> properties = contextProperties();
        properties.put("taskId", stringSchema());
        return readTool(LIST_STEPS_TOOL_NAME, "List persisted checkpoints for one durable Skill Task",
                objectSchema(properties, List.of("tenantId", "operatorId", "operatorType", "controlRunId",
                        "taskId")));
    }

    McpSchema.Tool retryTool() {
        Map<String, Object> properties = contextProperties();
        properties.put("taskId", stringSchema());
        properties.put("expectedVersion", Map.of("type", "integer", "minimum", 0));
        properties.put("reason", stringSchema());
        return writeTool(RETRY_TOOL_NAME,
                "Retry one R1 Skill Task in NEEDS_REVIEW using optimistic locking",
                objectSchema(properties, List.of("tenantId", "operatorId", "operatorType", "controlRunId",
                        "taskId", "expectedVersion", "reason")), false);
    }

    McpSchema.Tool fullChainSubmitTool() {
        Map<String, Object> properties = contextProperties();
        properties.put("clientRunId", stringSchema());
        properties.put("clientRequestKey", stringSchema());
        properties.put("input", Map.of("type", "object"));
        properties.put("approvalRef", stringSchema());
        return writeTool(FULL_CHAIN_SUBMIT_TOOL_NAME,
                "Submit the fixed, approved R3 CloudMold commerce full-chain Skill to the durable executor",
                objectSchema(properties, List.of("tenantId", "operatorId", "operatorType", "controlRunId",
                        "clientRunId", "clientRequestKey", "input", "approvalRef")), true);
    }

    McpSchema.Tool fullChainRetryTool() {
        Map<String, Object> properties = contextProperties();
        properties.put("taskId", stringSchema());
        properties.put("expectedVersion", Map.of("type", "integer", "minimum", 0));
        properties.put("reason", stringSchema());
        properties.put("approvalRef", stringSchema());
        return writeTool(FULL_CHAIN_RETRY_TOOL_NAME,
                "Retry only the fixed R3 CloudMold commerce full-chain Skill after approval renewal",
                objectSchema(properties, List.of("tenantId", "operatorId", "operatorType", "controlRunId",
                        "taskId", "expectedVersion", "reason", "approvalRef")), false);
    }

    McpSchema.CallToolResult submit(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        return guarded(() -> {
            Map<String, Object> values = arguments(request);
            String skillId = requiredString(values, "skillId");
            Map<String, Object> command = new LinkedHashMap<>();
            command.put("skillId", skillId);
            command.put("skillVersion", requiredString(values, "skillVersion"));
            command.put("clientRequestKey", requiredString(values, "clientRequestKey"));
            command.put("inputJson", objectMapper.writeValueAsString(required(values, "input")));
            command.put("riskLevel", "R1");
            return invoke(SUBMIT_CAPABILITY, List.of(command), context(values), true);
        });
    }

    McpSchema.CallToolResult get(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        return query(GET_CAPABILITY, request, values -> List.of(requiredString(values, "taskId")));
    }

    McpSchema.CallToolResult getByRequestKey(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        return query(GET_BY_REQUEST_KEY_CAPABILITY, request, values -> List.of(
                requiredString(values, "skillId"), requiredString(values, "clientRequestKey")));
    }

    McpSchema.CallToolResult listSteps(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        return query(LIST_STEPS_CAPABILITY, request, values -> List.of(requiredString(values, "taskId")));
    }

    McpSchema.CallToolResult retry(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        return guarded(() -> {
            Map<String, Object> values = arguments(request);
            CloudMoldRpcCallContext context = context(values);
            String taskId = requiredString(values, "taskId");
            JsonNode task = executor.execute(GET_CAPABILITY, objectMapper.valueToTree(List.of(taskId)), context, false);
            if (!"R1".equals(task.path("riskLevel").asText())) {
                throw new SecurityException("R1 retry is restricted to persisted R1 Skill Tasks");
            }
            Map<String, Object> command = new LinkedHashMap<>();
            command.put("taskId", taskId);
            command.put("expectedVersion", nonNegativeLong(values, "expectedVersion"));
            command.put("reason", requiredString(values, "reason"));
            return invoke(RETRY_CAPABILITY, List.of(command), context, true);
        });
    }

    McpSchema.CallToolResult fullChainSubmit(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        return guarded(() -> {
            Map<String, Object> values = arguments(request);
            Map<String, Object> command = new LinkedHashMap<>();
            command.put("skillId", FULL_CHAIN_SKILL_ID);
            command.put("skillVersion", FULL_CHAIN_SKILL_VERSION);
            command.put("runId", requiredString(values, "clientRunId"));
            command.put("clientRequestKey", requiredString(values, "clientRequestKey"));
            command.put("inputJson", objectMapper.writeValueAsString(required(values, "input")));
            command.put("riskLevel", "R3");
            command.put("approvalRef", requiredString(values, "approvalRef"));
            return invoke(SUBMIT_CAPABILITY, List.of(command), context(values), true);
        });
    }

    McpSchema.CallToolResult fullChainRetry(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        return guarded(() -> {
            Map<String, Object> values = arguments(request);
            CloudMoldRpcCallContext context = context(values);
            String taskId = requiredString(values, "taskId");
            JsonNode task = executor.execute(GET_CAPABILITY, objectMapper.valueToTree(List.of(taskId)), context, false);
            if (!FULL_CHAIN_SKILL_ID.equals(task.path("skillId").asText())
                    || !FULL_CHAIN_SKILL_VERSION.equals(task.path("skillVersion").asText())) {
                throw new SecurityException("R3 retry is restricted to the fixed commerce full-chain Skill");
            }
            Map<String, Object> command = new LinkedHashMap<>();
            command.put("taskId", taskId);
            command.put("expectedVersion", nonNegativeLong(values, "expectedVersion"));
            command.put("reason", requiredString(values, "reason"));
            command.put("approvalRef", requiredString(values, "approvalRef"));
            return invoke(RETRY_CAPABILITY, List.of(command), context, true);
        });
    }

    private McpSchema.CallToolResult query(String capabilityId, McpSchema.CallToolRequest request,
                                           QueryArguments queryArguments) {
        return guarded(() -> {
            Map<String, Object> values = arguments(request);
            return invoke(capabilityId, queryArguments.create(values), context(values), false);
        });
    }

    private Map<String, Object> invoke(String capabilityId, List<?> arguments, CloudMoldRpcCallContext context,
                                       boolean writeApproved) {
        ArrayNode argumentArray = objectMapper.valueToTree(arguments);
        JsonNode result = executor.execute(capabilityId, argumentArray, context, writeApproved);
        return Map.of("capabilityId", capabilityId, "controlRunId", context.runId(), "status", "SUCCEEDED",
                "result", objectMapper.convertValue(result, Object.class));
    }

    private CloudMoldRpcCallContext context(Map<String, Object> values) {
        return new CloudMoldRpcCallContext(positiveLong(values, "tenantId"), positiveLong(values, "operatorId"),
                Math.toIntExact(positiveLong(values, "operatorType")), CONTROL_SKILL_ID,
                requiredString(values, "controlRunId"));
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
            return error(new IllegalStateException("Skill Task service is unavailable", ex));
        } catch (JsonProcessingException ex) {
            return error(new IllegalStateException("Cannot serialize Skill Task MCP result", ex));
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
                    .content(List.of(McpSchema.TextContent.builder("MCP Skill Task request failed").build()))
                    .isError(true)
                    .build();
        }
    }

    private static Map<String, Object> arguments(McpSchema.CallToolRequest request) {
        if (request == null || request.arguments() == null) {
            throw new IllegalArgumentException("arguments are required");
        }
        return request.arguments();
    }

    private static Map<String, Object> contextProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("tenantId", Map.of("type", "integer", "minimum", 1));
        properties.put("operatorId", Map.of("type", "integer", "minimum", 1));
        properties.put("operatorType", Map.of("type", "integer", "minimum", 1));
        properties.put("controlRunId", stringSchema());
        return properties;
    }

    private static Map<String, Object> stringSchema() {
        return Map.of("type", "string", "minLength", 1);
    }

    private static McpSchema.Tool readTool(String name, String description, Map<String, Object> schema) {
        return tool(name, description, schema, true, true);
    }

    private static McpSchema.Tool writeTool(String name, String description, Map<String, Object> schema,
                                            boolean idempotent) {
        return tool(name, description, schema, false, idempotent);
    }

    private static McpSchema.Tool tool(String name, String description, Map<String, Object> schema,
                                       boolean readOnly, boolean idempotent) {
        return McpSchema.Tool.builder(name, schema)
                .description(description)
                .annotations(McpSchema.ToolAnnotations.builder()
                        .readOnlyHint(readOnly)
                        .destructiveHint(false)
                        .idempotentHint(idempotent)
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

    private static Object required(Map<String, Object> values, String name) {
        Object value = values.get(name);
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String requiredString(Map<String, Object> values, String name) {
        Object value = required(values, name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-blank string");
        }
        return text.trim();
    }

    private static long positiveLong(Map<String, Object> values, String name) {
        Object value = required(values, name);
        if (!(value instanceof Number number) || number.longValue() <= 0) {
            throw new IllegalArgumentException(name + " must be a positive integer");
        }
        return number.longValue();
    }

    private static long nonNegativeLong(Map<String, Object> values, String name) {
        Object value = required(values, name);
        if (!(value instanceof Number number) || number.longValue() < 0) {
            throw new IllegalArgumentException(name + " must be a non-negative integer");
        }
        return number.longValue();
    }

    @FunctionalInterface
    private interface StructuredCall {
        Object execute() throws JsonProcessingException;
    }

    @FunctionalInterface
    private interface QueryArguments {
        List<?> create(Map<String, Object> values);
    }
}
