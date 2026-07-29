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
    void shouldDescribeOperationalOrderCancellationWithHonestPspBoundary() {
        ManagedSkillTaskBusinessOutcomeView succeeded = presenter.present(
                task("skill.cloudmold.commerce.order-cancellation-operational.v1"),
                List.of(
                        step("start_order_cancellation", """
                                {"sagaId":"saga-1","cancellationMode":"PAID_UNSHIPPED",
                                 "orderId":"order-id","orderNo":"O-100","status":"REQUESTED",
                                 "activeStep":"CANCEL_FULFILLMENT","expectedReservationCount":1,
                                 "releasedReservationCount":0,"expectedFulfillmentCount":1,
                                 "cancelledFulfillmentCount":0,"paymentId":"payment-1",
                                 "paymentStatus":"CAPTURED","fulfillmentId":"fulfillment-1",
                                 "fulfillmentStatus":"CANCELLATION_PENDING"}
                                """),
                        step("wait_order_cancellation", """
                                {"workflowType":"OrderCancellation","status":"SUCCEEDED",
                                 "phase":"CANCELLED","summary":"订单取消补偿已完成：1/1 个库存预占已释放，1/1 个履约单已关闭。",
                                 "artifacts":[
                                   {"type":"ORDER","id":"order-id","status":"CANCELLED","label":"订单 O-100"},
                                   {"type":"PAYMENT","id":"payment-1","status":"REFUNDED","label":"退款支付"}
                                 ]}
                                """)));

        assertThat(succeeded.getHeadline()).isEqualTo("订单 O-100 已完成取消补偿闭环");
        assertThat(succeeded.getSummary()).contains("1/1 个库存预占已释放", "真实 PSP 退款回执仍需外部权威");
        assertThat(succeeded.getMetrics()).extracting("label", "value")
                .contains(
                        org.assertj.core.groups.Tuple.tuple("取消模式", "已支付未发货"),
                        org.assertj.core.groups.Tuple.tuple("支付状态", "已扣款"));
        assertThat(succeeded.getBusinessObjects()).extracting("objectType", "businessId")
                .contains(
                        org.assertj.core.groups.Tuple.tuple("CANCELLATION_SAGA", "saga-1"),
                        org.assertj.core.groups.Tuple.tuple("ORDER", "order-id"),
                        org.assertj.core.groups.Tuple.tuple("PAYMENT", "payment-1"),
                        org.assertj.core.groups.Tuple.tuple("FULFILLMENT", "fulfillment-1"));
    }

    @Test
    void shouldDescribeOperationalOrderCancellationManualReviewWithoutClaimingSuccess() {
        Task task = task("skill.cloudmold.commerce.order-cancellation-operational.v1");
        task.setStatus("NEEDS_REVIEW");
        task.setTerminalResultSha256(null);
        ManagedSkillTaskBusinessOutcomeView review = presenter.present(
                task,
                List.of(
                        step("start_order_cancellation", """
                                {"sagaId":"saga-2","cancellationMode":"UNPAID_RESERVED",
                                 "orderId":"order-id","orderNo":"O-200","status":"REQUESTED",
                                 "activeStep":"RELEASE_RESERVATIONS","expectedReservationCount":2,
                                 "releasedReservationCount":1}
                                """),
                        step("wait_order_cancellation", "NEEDS_REVIEW", """
                                {"workflowType":"OrderCancellation","status":"MANUAL_REVIEW",
                                 "phase":"RELEASE_RESERVATIONS",
                                 "summary":"订单取消停在人工复核，已保留当前补偿检查点和业务产物。",
                                 "blockers":["CANCELLATION_MANUAL_REVIEW:PAYMENT_REFUND_TIMEOUT"]}
                                """)));

        assertThat(review.getHeadline()).isEqualTo("订单 O-200 进入取消补偿人工复核");
        assertThat(review.getSummary()).contains("人工复核", "PAYMENT_REFUND_TIMEOUT");
        assertThat(review.getMetrics()).extracting("label", "value")
                .contains(org.assertj.core.groups.Tuple.tuple("业务状态", "需人工复核"));
    }

    @Test
    void shouldDescribeProductToListingAsPendingChannelConfirmationWhenNoChannelFactExists() {
        ManagedSkillTaskBusinessOutcomeView outcome = presenter.present(
                task("skill.cloudmold.commerce.product-to-listing.v1", """
                        {"catalog":{"definitions":[
                          {"spuCode":"YS-100","skuCode":"YS-100-BLACK-S"},
                          {"spuCode":"YS-100","skuCode":"YS-100-BLACK-M"}
                        ]}}
                        """),
                List.of(
                        step("wait_catalog", """
                                {"status":"SUCCEEDED","outputs":{"define_1":{
                                  "canonicalSpuId":"spu-id","spuCode":"YS-100","canonicalSkuId":"sku-1"}}}
                                """),
                        step("wait_master", """
                                {"status":"SUCCEEDED","outputs":{"merchant_approve":{
                                  "merchantId":"merchant-id","merchantStatus":"ACTIVE",
                                  "shopId":"shop-id","shopStatus":"ACTIVE"}}}
                                """),
                        step("listing_terminal_readback", """
                                {"listingId":"listing-id","listingNo":"L-100","currentStatus":"PUBLISHED",
                                 "channelPublicationStatus":"PENDING_CONFIRMATION",
                                 "overallResultCode":"PENDING_CONFIRMATION"}
                                """)));

        assertThat(outcome.getHeadline()).isEqualTo("新品 YS-100 已完成规范刊登，待渠道确认");
        assertThat(outcome.getSummary()).contains("尚未收到真实渠道终态回读");
        assertThat(outcome.getMetrics()).extracting("label", "value")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("SKU", "2"),
                        org.assertj.core.groups.Tuple.tuple("刊登", "L-100"),
                        org.assertj.core.groups.Tuple.tuple("渠道状态", "待渠道确认"));
        assertThat(outcome.getBusinessObjects()).extracting("objectType", "businessId")
                .contains(
                        org.assertj.core.groups.Tuple.tuple("SPU", "spu-id"),
                        org.assertj.core.groups.Tuple.tuple("LISTING", "listing-id"),
                        org.assertj.core.groups.Tuple.tuple("MERCHANT", "merchant-id"),
                        org.assertj.core.groups.Tuple.tuple("SHOP", "shop-id"));
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
    void shouldDescribeFulfillmentExceptionAsRecoveredDeliveryAndClosedCase() {
        ManagedSkillTaskBusinessOutcomeView outcome = presenter.present(
                task("skill.cloudmold.fulfillment.exception-resolution-lifecycle.v1"),
                List.of(
                        step("wait_in_transit_order", """
                                {"status":"SUCCEEDED","outputs":{
                                  "order_place":{"orderId":"order-1","orderNo":"O-100"},
                                  "fulfillment_create":{"fulfillmentId":"fulfillment-1",
                                    "fulfillmentNo":"F-100"}}}
                                """),
                        step("complete_recovered_order", """
                                {"orderId":"order-1","orderNo":"O-100","currentStatus":"COMPLETED"}
                                """),
                        step("verify_exception_closed", """
                                {"exceptionId":"exception-1","exceptionNo":"FE-100",
                                 "exceptionType":"DELAY","action":"CONTACT_CARRIER","status":"CLOSED"}
                                """)));

        assertThat(outcome.getHeadline()).isEqualTo("履约异常 FE-100 已恢复交付并关单");
        assertThat(outcome.getSummary()).contains("承运处置", "订单完结");
        assertThat(outcome.getMetrics()).extracting("label", "value")
                .contains(
                        org.assertj.core.groups.Tuple.tuple("异常状态", "已关闭"),
                        org.assertj.core.groups.Tuple.tuple("订单状态", "已完成"));
        assertThat(outcome.getBusinessObjects()).extracting("objectType", "businessId")
                .contains(
                        org.assertj.core.groups.Tuple.tuple("FULFILLMENT_EXCEPTION", "exception-1"),
                        org.assertj.core.groups.Tuple.tuple("ORDER", "order-1"),
                        org.assertj.core.groups.Tuple.tuple("FULFILLMENT", "fulfillment-1"));
    }

    @Test
    void shouldDescribeCrossborderCaseAsReleasedDeliveredAndClosed() {
        ManagedSkillTaskBusinessOutcomeView outcome = presenter.present(
                task("skill.cloudmold.crossborder.fulfillment-compliance-lifecycle.v1"),
                List.of(
                        step("wait_paid_in_transit_order", """
                                {"status":"SUCCEEDED","outputs":{
                                  "order_place":{"orderId":"order-1","orderNo":"O-200"},
                                  "fulfillment_create":{"fulfillmentId":"fulfillment-1",
                                    "fulfillmentNo":"F-200"}}}
                                """),
                        step("verify_crossborder_case_closed", """
                                {"caseId":"case-1","caseNo":"CB-100","tradeMode":"DIRECT_MAIL",
                                 "route":{"routeCode":"CN_US_DIRECT_EXPRESS"},
                                 "customsStatus":"RELEASED",
                                 "deliveryStatus":"DELIVERED","status":"CLOSED"}
                                """)));

        assertThat(outcome.getHeadline())
                .isEqualTo("跨境履约案件 CB-100 已清关、妥投并关单");
        assertThat(outcome.getSummary()).contains("AI 路线推荐", "海关放行", "CN→US");
        assertThat(outcome.getMetrics()).extracting("label", "value")
                .contains(
                        org.assertj.core.groups.Tuple.tuple("运输路线", "CN_US_DIRECT_EXPRESS"),
                        org.assertj.core.groups.Tuple.tuple("海关状态", "已放行"),
                        org.assertj.core.groups.Tuple.tuple("配送状态", "已送达"),
                        org.assertj.core.groups.Tuple.tuple("案件状态", "已关闭"));
        assertThat(outcome.getBusinessObjects()).extracting("objectType", "businessId")
                .contains(
                        org.assertj.core.groups.Tuple.tuple("CROSSBORDER_CASE", "case-1"),
                        org.assertj.core.groups.Tuple.tuple("ORDER", "order-1"),
                        org.assertj.core.groups.Tuple.tuple("FULFILLMENT", "fulfillment-1"));
    }

    @Test
    void shouldDescribeBondedCustomsCaseAsMatchedReleasedDeliveredAndClosed() {
        ManagedSkillTaskBusinessOutcomeView outcome = presenter.present(
                task("skill.cloudmold.crossborder.bonded-customs-lifecycle.v1"),
                List.of(
                        step("wait_paid_in_transit_order", """
                                {"status":"SUCCEEDED","outputs":{
                                  "order_place":{"orderId":"order-2","orderNo":"O-300"}}}
                                """),
                        step("verify_bonded_case_closed", """
                                {"caseId":"bonded-case-1","caseNo":"BC-100",
                                 "tripleMatchStatus":"TRIPLE_MATCHED",
                                 "goodsClassification":{"hsCode":"610910"},
                                 "taxCalculation":{"currency":"CNY","totalTaxMinor":3582},
                                 "customsStatus":"CUSTOMS_ACCEPTED",
                                 "bondedReleaseStatus":"BONDED_RELEASED",
                                 "deliveryStatus":"DELIVERED","status":"CLOSED"}
                                """)));

        assertThat(outcome.getHeadline())
                .isEqualTo("保税关务案件 BC-100 已对碰、放行、妥投并关单");
        assertThat(outcome.getSummary()).contains("商品归类", "三单对碰", "风险与法务会签");
        assertThat(outcome.getMetrics()).extracting("label", "value")
                .contains(
                        org.assertj.core.groups.Tuple.tuple("三单状态", "已对碰"),
                        org.assertj.core.groups.Tuple.tuple("商品归类", "610910"),
                        org.assertj.core.groups.Tuple.tuple("测试税费", "CNY 3582 分"),
                        org.assertj.core.groups.Tuple.tuple("海关状态", "已受理"),
                        org.assertj.core.groups.Tuple.tuple("保税放行", "已放行"),
                        org.assertj.core.groups.Tuple.tuple("配送状态", "已送达"),
                        org.assertj.core.groups.Tuple.tuple("案件状态", "已关闭"));
        assertThat(outcome.getBusinessObjects()).extracting("objectType", "businessId")
                .contains(
                        org.assertj.core.groups.Tuple.tuple("BONDED_CUSTOMS_CASE", "bonded-case-1"),
                        org.assertj.core.groups.Tuple.tuple("ORDER", "order-2"));
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
        return task(skillId, "{}");
    }

    private static Task task(String skillId, String inputJson) {
        Task task = new Task();
        task.setTaskId("task-1");
        task.setSkillId(skillId);
        task.setStatus("SUCCEEDED");
        task.setTerminalResultSha256("e".repeat(64));
        task.setInputJson(inputJson);
        return task;
    }

    private static Step step(String code, String resultJson) {
        return step(code, "SUCCEEDED", resultJson);
    }

    private static Step step(String code, String status, String resultJson) {
        Step step = new Step();
        step.setTaskId("task-1");
        step.setStepCode(code);
        step.setStatus(status);
        step.setResultJson(resultJson);
        return step;
    }
}
