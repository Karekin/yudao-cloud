package cn.iocoder.yudao.module.cloudmold.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CloudMoldAnalyticsMcpToolsTest {

    @Test
    void exposesOnlyReadOnlyAnalyticsTools() {
        CloudMoldAnalyticsMcpTools tools = tools(Path.of("/tmp/unused"));
        assertThat(tools.snapshotTool().annotations().readOnlyHint()).isTrue();
        assertThat(tools.metricValueTool().annotations().destructiveHint()).isFalse();
        assertThat(tools.metricDefinitionTool().name()).isEqualTo("cloudmold_analytics_metric_definition");
    }

    @Test
    void readsMetricValueFromGovernedFiles(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("dashboard-snapshot.json"), """
                {"snapshot_id":"s1","generated_at":"2026-07-18T00:00:00+08:00","evidence_scope":"LOCAL_TEST",
                 "tenant_label":"Tenant 1","warning":"not production","metrics":{"finance.net_revenue_yuan":{"value":398,"unit":"yuan","status":"runtime_local_test"}}}
                """);
        CloudMoldAnalyticsMcpTools tools = tools(directory);

        McpSchema.CallToolResult result = tools.metricValue(null, McpSchema.CallToolRequest.builder("metric")
                .arguments(Map.of("metricId", "finance.net_revenue_yuan")).build());

        assertThat(result.isError()).isFalse();
        assertThat(result.structuredContent().toString()).contains("398", "LOCAL_TEST", "Tenant 1");
    }

    @Test
    void rejectsUnknownMetric(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("dashboard-snapshot.json"), "{\"metrics\":{}}");
        CloudMoldAnalyticsMcpTools tools = tools(directory);

        McpSchema.CallToolResult result = tools.metricValue(null, McpSchema.CallToolRequest.builder("metric")
                .arguments(Map.of("metricId", "unknown.metric")).build());

        assertThat(result.isError()).isTrue();
        assertThat(result.structuredContent().toString()).contains("Unknown metricId");
    }

    private static CloudMoldAnalyticsMcpTools tools(Path directory) {
        CloudMoldAnalyticsProperties properties = new CloudMoldAnalyticsProperties();
        properties.setDataDirectory(directory);
        return new CloudMoldAnalyticsMcpTools(properties, new ObjectMapper().findAndRegisterModules());
    }
}
