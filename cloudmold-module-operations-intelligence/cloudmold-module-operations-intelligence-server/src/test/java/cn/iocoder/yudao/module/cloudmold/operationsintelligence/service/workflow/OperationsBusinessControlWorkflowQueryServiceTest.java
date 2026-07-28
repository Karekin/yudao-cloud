package cn.iocoder.yudao.module.cloudmold.operationsintelligence.service.workflow;

import cn.iocoder.yudao.module.cloudmold.operationsintelligence.api.workflow.BusinessControlWorkflowResult;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.config.OperationsIntelligenceAnalyticsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class OperationsBusinessControlWorkflowQueryServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-28T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Test
    void dailyReturnsNeedsDataWhenSnapshotIsMissing() {
        OperationsBusinessControlWorkflowQueryService service = new OperationsBusinessControlWorkflowQueryService(
                properties(tempDir), new ObjectMapper(), CLOCK);

        BusinessControlWorkflowResult result = service.inspectDaily();

        assertThat(result.getStatus()).isEqualTo(BusinessControlWorkflowResult.Status.NEEDS_DATA);
        assertThat(result.getPhase()).isEqualTo("WAITING_KPI_SNAPSHOT");
        assertThat(result.getBlockers()).containsExactly("KPI_SNAPSHOT_NOT_FOUND");
    }

    @Test
    void dailyBuildsReadableAnomaliesAndSuggestedWorkOrders() throws Exception {
        write("dashboard-snapshot.json", """
                {
                  "snapshot_id": "snap-daily-1",
                  "generated_at": "2026-07-28T08:00:00+08:00",
                  "evidence_scope": "LOCAL_TEST",
                  "tenant_label": "得物V1",
                  "metrics": {
                    "finance.net_revenue_yuan": {"value": 398, "unit": "yuan", "status": "runtime_local_test", "freshness": "2026-07-28 07:59:00", "note": "net revenue"},
                    "commerce.order_count": {"value": 28, "unit": "count", "status": "runtime_local_test", "freshness": "2026-07-28 07:59:00", "note": "orders"},
                    "commerce.reconciled_rate": {"value": 13.04, "unit": "percent", "status": "runtime_local_test", "freshness": "2026-07-28 07:59:00", "note": "reconciled"},
                    "inventory.low_stock_balance_count": {"value": 2, "unit": "count", "status": "runtime_local_test", "freshness": "2026-07-28 07:59:00", "note": "low stock"},
                    "inventory.stockout_rate": {"value": 6.5, "unit": "percent", "status": "runtime_local_test", "freshness": "2026-07-28 07:59:00", "note": "stockout"},
                    "service.resolution_sla_rate": {"value": 70, "unit": "percent", "status": "runtime_local_test", "freshness": "2026-07-28 07:59:00", "note": "sla"}
                  }
                }
                """);
        write("kpi-catalog.json", """
                {
                  "metrics": [
                    {"id":"finance.net_revenue_yuan","name":"净收入"},
                    {"id":"commerce.order_count","name":"订单量"},
                    {"id":"commerce.reconciled_rate","name":"交易对账率"},
                    {"id":"inventory.low_stock_balance_count","name":"低库存余额数"},
                    {"id":"inventory.stockout_rate","name":"缺货率"},
                    {"id":"service.resolution_sla_rate","name":"客服解决 SLA 达成率"}
                  ]
                }
                """);
        OperationsBusinessControlWorkflowQueryService service = new OperationsBusinessControlWorkflowQueryService(
                properties(tempDir), new ObjectMapper(), CLOCK);

        BusinessControlWorkflowResult result = service.inspectDaily();

        assertThat(result.getStatus()).isEqualTo(BusinessControlWorkflowResult.Status.RUNNING);
        assertThat(result.getAnomalies()).hasSize(4);
        assertThat(result.getSuggestedWorkOrders()).extracting("ownerRole")
                .contains("交易与财务", "库控与供应计划", "商品与库控", "客服运营");
        assertThat(result.getSummary()).contains("每日经营总控发现 4 项重点异常");
    }

    @Test
    void weeklyWaitsWhenSnapshotIsStale() throws Exception {
        write("dashboard-snapshot.json", """
                {
                  "snapshot_id": "snap-weekly-1",
                  "generated_at": "2026-07-18T15:33:37+08:00",
                  "evidence_scope": "LOCAL_TEST",
                  "tenant_label": "得物V1",
                  "metrics": {
                    "finance.contribution_margin_rate": {"value": 39.7, "unit": "percent", "status": "runtime_local_test", "freshness": "2026-07-18 05:23:08", "note": "margin"},
                    "commerce.reconciled_rate": {"value": 13.04, "unit": "percent", "status": "runtime_local_test", "freshness": "2026-07-18 06:42:05", "note": "reconciled"},
                    "inventory.turnover_days": {"value": 240, "unit": "days", "status": "runtime_local_test", "freshness": "2026-07-18 07:33:36", "note": "turnover"},
                    "inventory.sell_through_rate_30d": {"value": 3.7, "unit": "percent", "status": "runtime_local_test", "freshness": "2026-07-18 06:57:43", "note": "sell through"},
                    "procurement.otif_rate": {"value": 100, "unit": "percent", "status": "runtime_local_test", "freshness": "2026-07-18 04:29:10", "note": "otif"},
                    "service.first_contact_resolution_rate": {"value": 50, "unit": "percent", "status": "runtime_local_test", "freshness": "2026-07-18 04:16:32", "note": "fcr"},
                    "service.resolution_sla_rate": {"value": 50, "unit": "percent", "status": "runtime_local_test", "freshness": "2026-07-18 04:16:32", "note": "sla"}
                  }
                }
                """);
        write("kpi-catalog.json", """
                {
                  "metrics": [
                    {"id":"finance.contribution_margin_rate","name":"贡献利润率"},
                    {"id":"commerce.reconciled_rate","name":"交易对账率"},
                    {"id":"inventory.turnover_days","name":"库存周转天数"},
                    {"id":"inventory.sell_through_rate_30d","name":"30 天售罄率"},
                    {"id":"procurement.otif_rate","name":"采购 OTIF"},
                    {"id":"service.first_contact_resolution_rate","name":"一次解决率"},
                    {"id":"service.resolution_sla_rate","name":"客服解决 SLA 达成率"}
                  ]
                }
                """);
        OperationsBusinessControlWorkflowQueryService service = new OperationsBusinessControlWorkflowQueryService(
                properties(tempDir), new ObjectMapper(), CLOCK);

        BusinessControlWorkflowResult result = service.inspectWeekly();

        assertThat(result.getStatus()).isEqualTo(BusinessControlWorkflowResult.Status.WAITING);
        assertThat(result.getBlockers()).containsExactly("KPI_SNAPSHOT_STALE:2026-07-18T15:33:37+08:00");
        assertThat(result.getSummary()).contains("已经过期");
    }

    private OperationsIntelligenceAnalyticsProperties properties(Path dir) {
        OperationsIntelligenceAnalyticsProperties properties = new OperationsIntelligenceAnalyticsProperties();
        properties.setDataDirectory(dir);
        return properties;
    }

    private void write(String name, String content) throws Exception {
        java.nio.file.Files.writeString(tempDir.resolve(name), content);
    }
}
