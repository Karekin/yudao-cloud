package cn.iocoder.yudao.module.cloudmold.mcp;

import cn.iocoder.yudao.module.cloudmold.executor.CloudMoldCapabilityExecutor;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcCallContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CloudMoldSkillTaskMcpToolsTest {

    private static final String SUBMIT_CAPABILITY =
            "capability.cloudmold.skilltask.skill-task-command.submit.v1";
    private static final String RETRY_CAPABILITY =
            "capability.cloudmold.skilltask.skill-task-command.retry.v1";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final CloudMoldCapabilityExecutor executor = mock(CloudMoldCapabilityExecutor.class);
    private final CloudMoldSkillTaskMcpTools tools = new CloudMoldSkillTaskMcpTools(executor, objectMapper);

    @Test
    void exposesDurableTaskToolsWithAccurateSafetyHints() {
        assertThat(List.of(tools.submitTool().name(), tools.getTool().name(),
                tools.getByRequestKeyTool().name(), tools.listStepsTool().name(), tools.retryTool().name(),
                tools.fullChainSubmitTool().name(), tools.fullChainRetryTool().name()))
                .containsExactly(CloudMoldSkillTaskMcpTools.SUBMIT_TOOL_NAME,
                        CloudMoldSkillTaskMcpTools.GET_TOOL_NAME,
                        CloudMoldSkillTaskMcpTools.GET_BY_REQUEST_KEY_TOOL_NAME,
                        CloudMoldSkillTaskMcpTools.LIST_STEPS_TOOL_NAME,
                        CloudMoldSkillTaskMcpTools.RETRY_TOOL_NAME,
                        CloudMoldSkillTaskMcpTools.FULL_CHAIN_SUBMIT_TOOL_NAME,
                        CloudMoldSkillTaskMcpTools.FULL_CHAIN_RETRY_TOOL_NAME);
        assertThat(tools.submitTool().annotations().readOnlyHint()).isFalse();
        assertThat(tools.submitTool().annotations().idempotentHint()).isTrue();
        assertThat(tools.retryTool().annotations().readOnlyHint()).isFalse();
        assertThat(tools.retryTool().annotations().idempotentHint()).isFalse();
        assertThat(tools.getTool().annotations().readOnlyHint()).isTrue();
        assertThat(tools.fullChainSubmitTool().inputSchema().toString())
                .doesNotContain("skillId", "skillVersion", "riskLevel")
                .contains("approvalRef", "clientRunId");
    }

    @Test
    void submitsOnlyAnR1TaskThroughTheGovernedDubboCapability() throws Exception {
        when(executor.execute(eq(SUBMIT_CAPABILITY), any(), any(), eq(true)))
                .thenReturn(objectMapper.readTree("{\"taskId\":\"task-1\",\"status\":\"QUEUED\"}"));

        McpSchema.CallToolResult result = tools.submit(null, McpSchema.CallToolRequest
                .builder(CloudMoldSkillTaskMcpTools.SUBMIT_TOOL_NAME)
                .arguments(submitArguments())
                .build());

        assertThat(result.isError()).isFalse();
        assertThat(result.structuredContent().toString()).contains("task-1", "SUCCEEDED");
        ArgumentCaptor<JsonNode> argumentCaptor = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<CloudMoldRpcCallContext> contextCaptor =
                ArgumentCaptor.forClass(CloudMoldRpcCallContext.class);
        verify(executor).execute(eq(SUBMIT_CAPABILITY), argumentCaptor.capture(), contextCaptor.capture(), eq(true));
        JsonNode command = argumentCaptor.getValue().get(0);
        assertThat(command.get("riskLevel").asText()).isEqualTo("R1");
        assertThat(command.get("approvalRef")).isNull();
        assertThat(objectMapper.readTree(command.get("inputJson").asText()).get("skuId").asText())
                .isEqualTo("sku-1");
        assertThat(contextCaptor.getValue().tenantId()).isEqualTo(1L);
        assertThat(contextCaptor.getValue().operatorId()).isEqualTo(2L);
        assertThat(contextCaptor.getValue().skillId()).isEqualTo("skill.cloudmold.platform.mcp-task-control.v1");
        assertThat(contextCaptor.getValue().runId()).isEqualTo("control-run-1");
    }

    @Test
    void retriesWithOptimisticVersionThroughTheGovernedDubboCapability() throws Exception {
        when(executor.execute(eq("capability.cloudmold.skilltask.skill-task-query.get.v1"), any(), any(), eq(false)))
                .thenReturn(objectMapper.readTree("{\"taskId\":\"task-1\",\"riskLevel\":\"R1\"}"));
        when(executor.execute(eq(RETRY_CAPABILITY), any(), any(), eq(true)))
                .thenReturn(objectMapper.readTree("{\"taskId\":\"task-1\",\"status\":\"QUEUED\"}"));
        Map<String, Object> arguments = contextArguments();
        arguments.put("taskId", "task-1");
        arguments.put("expectedVersion", 5L);
        arguments.put("reason", "dependency recovered");

        McpSchema.CallToolResult result = tools.retry(null, McpSchema.CallToolRequest
                .builder(CloudMoldSkillTaskMcpTools.RETRY_TOOL_NAME)
                .arguments(arguments)
                .build());

        assertThat(result.isError()).isFalse();
        ArgumentCaptor<JsonNode> argumentCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(executor).execute(eq(RETRY_CAPABILITY), argumentCaptor.capture(), any(), eq(true));
        assertThat(argumentCaptor.getValue().get(0).get("expectedVersion").asLong()).isEqualTo(5L);
        assertThat(argumentCaptor.getValue().get(0).get("reason").asText()).isEqualTo("dependency recovered");
    }

    @Test
    void rejectsMissingTenantBeforeAnyDubboInvocation() {
        Map<String, Object> arguments = submitArguments();
        arguments.remove("tenantId");

        McpSchema.CallToolResult result = tools.submit(null, McpSchema.CallToolRequest
                .builder(CloudMoldSkillTaskMcpTools.SUBMIT_TOOL_NAME)
                .arguments(arguments)
                .build());

        assertThat(result.isError()).isTrue();
        assertThat(result.structuredContent().toString()).contains("tenantId is required");
        verifyNoInteractions(executor);
    }

    @Test
    void submitsOnlyTheFixedR3FullChainWithApprovalAndClientRunId() throws Exception {
        when(executor.execute(eq(SUBMIT_CAPABILITY), any(), any(), eq(true)))
                .thenReturn(objectMapper.readTree("{\"taskId\":\"r3-task\",\"status\":\"QUEUED\"}"));
        Map<String, Object> arguments = contextArguments();
        arguments.put("clientRunId", "deerflow-run-1");
        arguments.put("clientRequestKey", "full-chain-1");
        arguments.put("input", Map.of("scenario", "commerce"));
        arguments.put("approvalRef", "cma1:approval1:9999999999:signature");

        McpSchema.CallToolResult result = tools.fullChainSubmit(null, McpSchema.CallToolRequest
                .builder(CloudMoldSkillTaskMcpTools.FULL_CHAIN_SUBMIT_TOOL_NAME).arguments(arguments).build());

        assertThat(result.isError()).isFalse();
        ArgumentCaptor<JsonNode> command = ArgumentCaptor.forClass(JsonNode.class);
        verify(executor).execute(eq(SUBMIT_CAPABILITY), command.capture(), any(), eq(true));
        JsonNode value = command.getValue().get(0);
        assertThat(value.get("skillId").asText()).isEqualTo(CloudMoldSkillTaskMcpTools.FULL_CHAIN_SKILL_ID);
        assertThat(value.get("skillVersion").asText()).isEqualTo(CloudMoldSkillTaskMcpTools.FULL_CHAIN_SKILL_VERSION);
        assertThat(value.get("riskLevel").asText()).isEqualTo("R3");
        assertThat(value.get("runId").asText()).isEqualTo("deerflow-run-1");
        assertThat(value.get("approvalRef").asText()).startsWith("cma1:");
    }

    @Test
    void rejectsR3RetryWhenPersistedTaskIsOutsideTheFixedWhitelist() throws Exception {
        when(executor.execute(eq("capability.cloudmold.skilltask.skill-task-query.get.v1"), any(), any(), eq(false)))
                .thenReturn(objectMapper.readTree("{\"skillId\":\"skill.other\",\"skillVersion\":\"1.0.0\"}"));
        Map<String, Object> arguments = contextArguments();
        arguments.put("taskId", "other-task");
        arguments.put("expectedVersion", 2L);
        arguments.put("reason", "retry");
        arguments.put("approvalRef", "cma1:approval1:9999999999:signature");

        McpSchema.CallToolResult result = tools.fullChainRetry(null, McpSchema.CallToolRequest
                .builder(CloudMoldSkillTaskMcpTools.FULL_CHAIN_RETRY_TOOL_NAME).arguments(arguments).build());

        assertThat(result.isError()).isTrue();
        assertThat(result.structuredContent().toString()).contains("restricted");
        verify(executor).execute(eq("capability.cloudmold.skilltask.skill-task-query.get.v1"), any(), any(), eq(false));
    }

    private static Map<String, Object> submitArguments() {
        Map<String, Object> arguments = contextArguments();
        arguments.put("skillId", "skill.cloudmold.catalog.inspect.v1");
        arguments.put("skillVersion", "1.0.0");
        arguments.put("clientRequestKey", "catalog-inspect-1");
        arguments.put("input", Map.of("skuId", "sku-1"));
        return arguments;
    }

    private static Map<String, Object> contextArguments() {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("tenantId", 1L);
        arguments.put("operatorId", 2L);
        arguments.put("operatorType", 1);
        arguments.put("controlRunId", "control-run-1");
        return arguments;
    }
}
