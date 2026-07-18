package cn.iocoder.yudao.module.cloudmold.mcp;

import cn.iocoder.yudao.module.cloudmold.executor.CapabilityContractDescriber;
import cn.iocoder.yudao.module.cloudmold.executor.CapabilityDescriptor;
import cn.iocoder.yudao.module.cloudmold.executor.CapabilityOperationType;
import cn.iocoder.yudao.module.cloudmold.executor.CloudMoldCapabilityCatalog;
import cn.iocoder.yudao.module.cloudmold.executor.CloudMoldCapabilityExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CloudMoldMcpToolsTest {

    private final CloudMoldCapabilityCatalog catalog = mock(CloudMoldCapabilityCatalog.class);
    private final CapabilityContractDescriber describer = mock(CapabilityContractDescriber.class);
    private final CloudMoldCapabilityExecutor executor = mock(CloudMoldCapabilityExecutor.class);
    private final CloudMoldMcpTools tools = new CloudMoldMcpTools(catalog, describer, executor,
            new ObjectMapper().findAndRegisterModules());

    @Test
    void exposesOnlyThreeGovernedTools() {
        assertThat(List.of(tools.listTool().name(), tools.describeTool().name(), tools.invokeReadTool().name()))
                .containsExactly(CloudMoldMcpTools.LIST_TOOL_NAME, CloudMoldMcpTools.DESCRIBE_TOOL_NAME,
                        CloudMoldMcpTools.INVOKE_READ_TOOL_NAME);
        assertThat(tools.invokeReadTool().annotations().readOnlyHint()).isTrue();
        assertThat(tools.invokeReadTool().annotations().destructiveHint()).isFalse();
    }

    @Test
    void rejectsWriteCapabilityBeforeCreatingDubboInvocation() {
        String capabilityId = "capability.cloudmold.order.command.cancel.v1";
        when(catalog.require(capabilityId)).thenReturn(new CapabilityDescriptor(
                capabilityId, "example.CommandApi", "cancel", List.of(), "void",
                CapabilityOperationType.WRITE, "cloudmold", "1.0.0", 5000, null));

        McpSchema.CallToolResult result = tools.invokeRead(null, McpSchema.CallToolRequest
                .builder(CloudMoldMcpTools.INVOKE_READ_TOOL_NAME)
                .arguments(validInvocation(capabilityId))
                .build());

        assertThat(result.isError()).isTrue();
        assertThat(result.structuredContent().toString()).contains("restricted to READ");
        verifyNoInteractions(executor);
    }

    @Test
    void rejectsMissingTenantContextBeforeDubboInvocation() {
        String capabilityId = "capability.cloudmold.catalog.query.get.v1";
        when(catalog.require(capabilityId)).thenReturn(new CapabilityDescriptor(
                capabilityId, "example.QueryApi", "get", List.of(), "java.lang.Object",
                CapabilityOperationType.READ, "cloudmold", "1.0.0", 5000, null));
        Map<String, Object> arguments = validInvocation(capabilityId);
        arguments.remove("tenantId");

        McpSchema.CallToolResult result = tools.invokeRead(null, McpSchema.CallToolRequest
                .builder(CloudMoldMcpTools.INVOKE_READ_TOOL_NAME)
                .arguments(arguments)
                .build());

        assertThat(result.isError()).isTrue();
        assertThat(result.structuredContent().toString()).contains("tenantId is required");
        verifyNoInteractions(executor);
    }

    private static Map<String, Object> validInvocation(String capabilityId) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("capabilityId", capabilityId);
        result.put("arguments", List.of());
        result.put("tenantId", 1L);
        result.put("operatorId", 2L);
        result.put("operatorType", 1);
        result.put("skillId", "skill.cloudmold.test.v1");
        result.put("runId", "run-test-1");
        return result;
    }
}
