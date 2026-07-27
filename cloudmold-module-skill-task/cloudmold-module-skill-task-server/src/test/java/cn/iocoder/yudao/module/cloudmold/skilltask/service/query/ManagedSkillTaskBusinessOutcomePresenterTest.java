package cn.iocoder.yudao.module.cloudmold.skilltask.service.query;

import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskBusinessOutcomeView;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Step;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedSkillTaskBusinessOutcomePresenterTest {

    private final ManagedSkillTaskBusinessOutcomePresenter presenter =
            new ManagedSkillTaskBusinessOutcomePresenter(new ObjectMapper());

    @Test
    void shouldDescribeActiveSkuAndStockoutDiagnosis() {
        ManagedSkillTaskBusinessOutcomeView activeSku = presenter.present(
                task("skill.cloudmold.catalog.inspect-active-sku.v1"),
                List.of(step("get-active-sku", """
                        {"canonicalSkuId":"sku-id","skuCode":"SKU-100","spuCode":"SPU-100",
                         "catalogStatus":"ACTIVE","sizeName":"M","colorName":"黑色"}
                        """)));
        ManagedSkillTaskBusinessOutcomeView stockout = presenter.present(
                task("skill.cloudmold.inventory.stockout-diagnosis.v1"),
                List.of(step("diagnose-size-stockout", """
                        {"canonicalSpuId":"spu-id","spuCode":"SPU-100","skuCount":6,
                         "stockoutCount":2,"lowStockCount":1,"outcomeCode":"ATTENTION_REQUIRED"}
                        """)));

        assertThat(activeSku.getHeadline()).isEqualTo("SKU SKU-100 已确认为在售");
        assertThat(stockout.getHeadline()).contains("缺货 2 个", "低库存 1 个");
        assertThat(stockout.getMetrics()).extracting("value").containsExactly("6", "2", "1");
    }

    @Test
    void shouldDescribeAftersaleRefundAsBusinessCompletion() {
        ManagedSkillTaskBusinessOutcomeView outcome = presenter.present(
                task("skill.cloudmold.commerce.aftersale-saga.v1"),
                List.of(
                        step("listing_publish", """
                                {"listingId":"listing-id","listingNo":"L-100","currentStatus":"PUBLISHED"}
                                """),
                        step("order_complete", """
                                {"orderId":"order-id","orderNo":"O-100","currentStatus":"COMPLETED"}
                                """),
                        step("wait_resolution", """
                                {"afterSaleId":"as-id","afterSaleNo":"AS-100","caseStatus":"COMPLETED",
                                 "refundStatus":"REFUNDED","approvedAmountMinor":12900,"currencyCode":"CNY"}
                                """)));

        assertThat(outcome.getHeadline()).isEqualTo("售后单 AS-100 已完成退款与库存恢复");
        assertThat(outcome.getSummary()).contains("订单 O-100", "退货质检和退款闭环");
        assertThat(outcome.getMetrics()).extracting("label", "value")
                .contains(org.assertj.core.groups.Tuple.tuple("退款金额", "129.00 CNY"));
        assertThat(outcome.getBusinessObjects()).extracting("businessCode")
                .containsExactly("L-100", "O-100", "AS-100");
    }

    @Test
    void shouldDescribeProjectionMasterDataAndReadback() {
        ManagedSkillTaskBusinessOutcomeView projection = presenter.present(
                task("skill.cloudmold.commerce.legacy-projection-plan.v1"),
                List.of(
                        step("plan_1", "{\"projections\":[{},{},{}]}"),
                        step("plan_2", "{\"projections\":[{},{},{}]}")));
        ManagedSkillTaskBusinessOutcomeView master = presenter.present(
                task("skill.cloudmold.commerce.reuse-ready-master.v1"),
                List.of(
                        step("shop_activate", """
                                {"merchantId":"merchant-id","merchantStatus":"ACTIVE",
                                 "shopId":"shop-id","shopStatus":"ACTIVE"}
                                """),
                        step("warehouse_network", """
                                {"warehouseId":"warehouse-id","warehouseStatus":"ACTIVE"}
                                """)));
        ManagedSkillTaskBusinessOutcomeView readback = presenter.present(
                task("skill.cloudmold.commerce.terminal-readback.v1"),
                List.of(
                        step("listing_offer", """
                                {"listingId":"listing-id","listingNo":"L-100"}
                                """),
                        step("order_attribution", """
                                {"orderId":"order-id","orderNo":"O-100","status":"COMPLETED"}
                                """),
                        step("aftersale_by_item", """
                                {"afterSaleId":"as-id","afterSaleNo":"AS-100","caseStatus":"COMPLETED"}
                                """),
                        step("payment_refund_refunded", "{\"status\":\"REFUNDED\"}")));

        assertThat(projection.getHeadline()).isEqualTo("2 个 SKU 的旧系统投影方案已生成");
        assertThat(projection.getMetrics()).extracting("value").containsExactly("2", "6");
        assertThat(master.getHeadline()).isEqualTo("商家店铺与仓网已准备就绪");
        assertThat(master.getBusinessObjects()).hasSize(3);
        assertThat(readback.getHeadline()).isEqualTo("商品交易与售后终态核验通过");
        assertThat(readback.getSummary()).contains("退款已到账");
    }

    @Test
    void shouldDescribeFullChainByCompletedChildFlows() {
        ManagedSkillTaskBusinessOutcomeView outcome = presenter.present(
                task("skill.cloudmold.commerce.full-chain-hsf.v1"),
                List.of(
                        step("wait_catalog", "{\"status\":\"SUCCEEDED\"}"),
                        step("wait_projection", "{\"status\":\"SUCCEEDED\"}"),
                        step("wait_master", "{\"status\":\"SUCCEEDED\"}"),
                        step("wait_aftersale", "{\"status\":\"SUCCEEDED\"}"),
                        step("wait_readback", "{\"status\":\"SUCCEEDED\"}")));

        assertThat(outcome.getHeadline()).isEqualTo("商品售后自治全链路已完成");
        assertThat(outcome.getSummary()).contains("5 条子流程全部成功");
    }

    @Test
    void shouldDescribeCompositionStepWhenChildSkillIdIsNotPersisted() {
        Step submit = step("submit_catalog", "{\"status\":\"SUCCEEDED\"}");
        Step wait = step("wait_catalog", "{\"status\":\"SUCCEEDED\"}");

        assertThat(presenter.stepDisplayName(submit)).isEqualTo("启动子流程：商品款色码建档");
        assertThat(presenter.stepDisplayName(wait)).isEqualTo("等待子流程完成：商品款色码建档");
    }

    @Test
    void shouldDescribeCatalogLifecycleStepsInBusinessLanguage() {
        Step submitSpu = step("submit_spu", """
                {"entityType":"SPU","businessCode":"SPU-100","currentStatus":"SUBMITTED"}
                """);

        assertThat(presenter.stepDisplayName(submitSpu)).isEqualTo("提交 SPU 审核");
        assertThat(presenter.stepResultSummary(submitSpu)).isEqualTo("SPU SPU-100 已变更为 已提交");
        assertThat(presenter.stepDisplayName(step("activate_size_group", "{}"))).isEqualTo("启用尺码组");
        assertThat(presenter.stepDisplayName(step("merchant_approve", "{}"))).isEqualTo("批准商家入驻");
        assertThat(presenter.stepDisplayName(step("order_inventory", "{}"))).isEqualTo("确认订单库存锁定");
        assertThat(presenter.stepDisplayName(step("warehouse_source", "{}"))).isEqualTo("核验 ERP 仓库来源");
    }

    @Test
    void shouldExposeOnlyWhitelistedBusinessObjectsForAnAction() {
        Step action = step("order_complete", """
                {"orderId":"order-id","orderNo":"O-100","currentStatus":"COMPLETED",
                 "secret":"must-not-leak"}
                """);

        assertThat(presenter.stepBusinessObjects(action)).singleElement().satisfies(object -> {
            assertThat(object.getObjectType()).isEqualTo("ORDER");
            assertThat(object.getBusinessId()).isEqualTo("order-id");
            assertThat(object.getBusinessCode()).isEqualTo("O-100");
            assertThat(object.getStatus()).isEqualTo("COMPLETED");
        });
    }

    private static Task task(String skillId) {
        Task task = new Task();
        task.setTaskId("task-1");
        task.setSkillId(skillId);
        task.setStatus("SUCCEEDED");
        task.setTerminalResultSha256("e".repeat(64));
        task.setInputJson("{}");
        return task;
    }

    private static Step step(String code, String resultJson) {
        Step step = new Step();
        step.setTaskId("task-1");
        step.setStepCode(code);
        step.setStatus("SUCCEEDED");
        step.setResultJson(resultJson);
        return step;
    }
}
