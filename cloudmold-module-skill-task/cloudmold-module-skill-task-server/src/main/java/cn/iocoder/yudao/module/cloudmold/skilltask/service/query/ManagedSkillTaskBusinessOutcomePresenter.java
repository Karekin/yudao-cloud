package cn.iocoder.yudao.module.cloudmold.skilltask.service.query;

import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskBusinessOutcomeView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskOutcomeMetricView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskOutcomeObjectView;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Step;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Task;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ManagedSkillTaskBusinessOutcomePresenter {

    private static final Map<String, String> SKILL_NAMES = Map.of(
            "skill.cloudmold.catalog.inspect-active-sku.v1", "在售 SKU 查询",
            "skill.cloudmold.inventory.stockout-diagnosis.v1", "尺码缺断码诊断",
            "skill.cloudmold.commerce.catalog-matrix.v1", "商品款色码建档",
            "skill.cloudmold.commerce.product-to-listing.v1", "自动铺品",
            "skill.cloudmold.commerce.aftersale-saga.v1", "售后退款全链路",
            "skill.cloudmold.commerce.legacy-projection-plan.v1", "旧系统投影预检",
            "skill.cloudmold.commerce.reuse-ready-master.v1", "商家与仓网主数据准备",
            "skill.cloudmold.commerce.terminal-readback.v1", "全链路终态核验",
            "skill.cloudmold.commerce.full-chain-hsf.v1", "商品售后自治全链路"
    );

    private static final Map<String, String> SKILL_DESCRIPTIONS = Map.of(
            "skill.cloudmold.catalog.inspect-active-sku.v1",
            "查询当前租户指定 SKU 的规范商品与在售状态，不修改业务数据。",
            "skill.cloudmold.inventory.stockout-diagnosis.v1",
            "按 SPU 检查各尺码可售库存，识别缺货与低库存风险，不修改业务数据。",
            "skill.cloudmold.commerce.catalog-matrix.v1",
            "建立款式、SPU 与 6 个 SKU，并完成商品生命周期激活。",
            "skill.cloudmold.commerce.product-to-listing.v1",
            "串联规范商品建档、商家店铺准备、商品刊登审核发布与终态回读，缺少渠道回执时明确标记待渠道确认。",
            "skill.cloudmold.commerce.aftersale-saga.v1",
            "贯通发布、库存、下单支付、履约、退货质检、退款和库存恢复。",
            "skill.cloudmold.commerce.legacy-projection-plan.v1",
            "只读规划规范 SKU 向 Mall、ERP 与 WMS 的兼容投影，不执行旧系统写入。",
            "skill.cloudmold.commerce.reuse-ready-master.v1",
            "校验身份与 ERP 仓，创建并激活商家店铺，绑定可用仓网。",
            "skill.cloudmold.commerce.terminal-readback.v1",
            "只读核验商品、商家、刊登、订单、支付、履约、售后及仓网终态。",
            "skill.cloudmold.commerce.full-chain-hsf.v1",
            "依次编排商品建档、旧系统投影、商家仓网、售后 Saga 与终态核验。"
    );

    private static final Map<String, String> COMPOSITION_SKILL_IDS = Map.of(
            "catalog", "skill.cloudmold.commerce.catalog-matrix.v1",
            "projection", "skill.cloudmold.commerce.legacy-projection-plan.v1",
            "master", "skill.cloudmold.commerce.reuse-ready-master.v1",
            "aftersale", "skill.cloudmold.commerce.aftersale-saga.v1",
            "readback", "skill.cloudmold.commerce.terminal-readback.v1"
    );

    private static final Map<String, String> STEP_NAMES = Map.ofEntries(
            Map.entry("get-active-sku", "查询在售 SKU"),
            Map.entry("diagnose-size-stockout", "诊断尺码库存"),
            Map.entry("activate_style", "启用商品款式"),
            Map.entry("submit_spu", "提交 SPU 审核"),
            Map.entry("approve_spu", "审批通过 SPU"),
            Map.entry("activate_size_group", "启用尺码组"),
            Map.entry("activate_spu", "启用 SPU"),
            Map.entry("listing_create", "创建商品刊登"),
            Map.entry("listing_submit", "提交商品刊登"),
            Map.entry("listing_completion", "完成刊登资料"),
            Map.entry("listing_business", "业务审核刊登"),
            Map.entry("listing_risk", "风险审核刊登"),
            Map.entry("listing_publish", "发布商品刊登"),
            Map.entry("listing_terminal_readback", "回读刊登终态"),
            Map.entry("inventory_receive", "商品入库"),
            Map.entry("order_place", "创建订单"),
            Map.entry("inventory_reserve", "预占库存"),
            Map.entry("payment_capture", "完成支付"),
            Map.entry("fulfillment_create", "创建履约单"),
            Map.entry("fulfillment_ship", "商品发货"),
            Map.entry("fulfillment_transit", "商品运输"),
            Map.entry("fulfillment_deliver", "商品签收"),
            Map.entry("order_complete", "完成订单"),
            Map.entry("aftersale_request", "发起售后"),
            Map.entry("aftersale_approve", "审核售后"),
            Map.entry("return_handover", "退货交接"),
            Map.entry("return_transit", "退货运输"),
            Map.entry("return_receive", "退货入库"),
            Map.entry("inspection_accept", "退货质检通过"),
            Map.entry("wait_resolution", "确认退款与库存恢复"),
            Map.entry("erp_warehouse", "读取 ERP 仓库"),
            Map.entry("principal", "核验运营主体"),
            Map.entry("merchant_draft", "创建商家草稿"),
            Map.entry("merchant_submit", "提交商家审核"),
            Map.entry("merchant_review", "完成商家审核"),
            Map.entry("merchant_approve", "批准商家入驻"),
            Map.entry("merchant_activate", "激活商家"),
            Map.entry("shop_activate", "激活店铺"),
            Map.entry("warehouse_define", "创建仓库"),
            Map.entry("warehouse_activate", "激活仓库"),
            Map.entry("zone_define", "创建库区"),
            Map.entry("zone_activate", "激活库区"),
            Map.entry("location_define", "创建库位"),
            Map.entry("location_activate", "激活库位"),
            Map.entry("warehouse_link_source", "关联 ERP 仓库"),
            Map.entry("warehouse_network", "核验仓网可用"),
            Map.entry("order_inventory", "确认订单库存锁定"),
            Map.entry("order_payment", "确认订单支付"),
            Map.entry("inventory_ship", "扣减发货库存"),
            Map.entry("order_ship", "确认订单已发货"),
            Map.entry("merchant_reference", "核验商家与店铺"),
            Map.entry("merchant_owner", "核验商家归属"),
            Map.entry("catalog_sku", "核验商品 SKU"),
            Map.entry("catalog_sku_valid", "核验 SKU 有效"),
            Map.entry("catalog_spu_valid", "核验 SPU 有效"),
            Map.entry("listing_offer", "核验已发布商品"),
            Map.entry("forward_delivered", "核验正向签收"),
            Map.entry("shipment_delivered", "核验履约已签收"),
            Map.entry("return_get", "读取退货验收结果"),
            Map.entry("return_accepted", "核验退货质检"),
            Map.entry("aftersale_by_item", "核验售后终态"),
            Map.entry("order_attribution", "核验订单归属"),
            Map.entry("payment_cancellation_refunded", "核验支付取消退款"),
            Map.entry("payment_refund_refunded", "核验退款到账"),
            Map.entry("warehouse", "核验仓库"),
            Map.entry("location", "核验库位"),
            Map.entry("warehouse_source", "核验 ERP 仓库来源")
    );

    private final ObjectMapper objectMapper;

    public ManagedSkillTaskBusinessOutcomeView present(Task task, List<Step> steps) {
        if (!"SUCCEEDED".equals(task.getStatus())) {
            return nonSuccess(task, steps);
        }
        return switch (task.getSkillId()) {
            case "skill.cloudmold.catalog.inspect-active-sku.v1" -> activeSku(task, steps);
            case "skill.cloudmold.inventory.stockout-diagnosis.v1" -> stockout(task, steps);
            case "skill.cloudmold.commerce.catalog-matrix.v1" -> catalog(task, steps);
            case "skill.cloudmold.commerce.product-to-listing.v1" -> productToListing(task, steps);
            case "skill.cloudmold.commerce.aftersale-saga.v1" -> aftersale(task, steps);
            case "skill.cloudmold.commerce.legacy-projection-plan.v1" -> projection(task, steps);
            case "skill.cloudmold.commerce.reuse-ready-master.v1" -> masterData(task, steps);
            case "skill.cloudmold.commerce.terminal-readback.v1" -> readback(task, steps);
            case "skill.cloudmold.commerce.full-chain-hsf.v1" -> fullChain(task, steps);
            default -> genericSuccess(task, steps);
        };
    }

    public String skillDisplayName(String skillId) {
        return SKILL_NAMES.getOrDefault(skillId, valueOr(skillId, "Agent 任务"));
    }

    public String skillDescription(String skillId) {
        return SKILL_DESCRIPTIONS.getOrDefault(skillId, "由 Agent 执行并由 SkillTask 持久化的业务阶段。");
    }

    public String childWorkflowSkillId(Step step) {
        if (StringUtils.hasText(step.getChildSkillId())) {
            return step.getChildSkillId();
        }
        String suffix = step.getStepCode().replaceFirst("^(submit|wait)_", "");
        return COMPOSITION_SKILL_IDS.get(suffix);
    }

    public String childWorkflowDisplayName(Step step) {
        String skillId = childWorkflowSkillId(step);
        if (StringUtils.hasText(skillId)) {
            return skillDisplayName(skillId);
        }
        return step.getStepCode().replaceFirst("^(submit|wait)_", "");
    }

    public String stepDisplayName(Step step) {
        String explicit = STEP_NAMES.get(step.getStepCode());
        if (explicit != null) {
            return explicit;
        }
        if (step.getStepCode().startsWith("define_")) {
            return "创建第 " + numericSuffix(step.getStepCode()) + " 个 SKU";
        }
        if (step.getStepCode().startsWith("activate_sku_")) {
            return "启用第 " + numericSuffix(step.getStepCode()) + " 个 SKU";
        }
        if (step.getStepCode().startsWith("activate_size_")) {
            return "启用尺码 " + step.getStepCode().substring("activate_size_".length()).toUpperCase(Locale.ROOT);
        }
        if (step.getStepCode().startsWith("activate_color_")) {
            return "启用颜色 " + step.getStepCode().substring("activate_color_".length());
        }
        if (step.getStepCode().startsWith("plan_")) {
            return "生成第 " + numericSuffix(step.getStepCode()) + " 个 SKU 投影方案";
        }
        if (step.getStepCode().startsWith("submit_")) {
            return "启动子流程：" + childSkillName(step);
        }
        if (step.getStepCode().startsWith("wait_")) {
            return "等待子流程完成：" + childSkillName(step);
        }
        return step.getStepCode();
    }

    public String stepResultSummary(Step step) {
        JsonNode result = json(step.getResultJson());
        if (result.isMissingNode() || result.isNull()) {
            return "SUCCEEDED".equals(step.getStatus()) ? "校验通过" : statusLabel(step.getStatus());
        }
        if (StringUtils.hasText(step.getChildTaskId())) {
            return "子任务 " + step.getChildTaskId() + " 已提交";
        }
        if (step.getStepCode().startsWith("wait_") && text(result, "childTaskId") != null) {
            return "子流程已完成";
        }
        String skuCode = firstText(json(step.getRequestJson()), "skuCode");
        if (step.getStepCode().startsWith("define_")) {
            return Boolean.TRUE.equals(bool(result, "created"))
                    ? "已创建 SKU " + valueOr(skuCode, text(result, "canonicalSkuId"))
                    : "SKU " + valueOr(skuCode, text(result, "canonicalSkuId")) + " 已存在";
        }
        if (text(result, "entityType") != null && text(result, "currentStatus") != null) {
            return objectTypeLabel(text(result, "entityType")) + " "
                    + valueOr(text(result, "businessCode"), text(result, "entityId"))
                    + " 已变更为 " + statusLabel(text(result, "currentStatus"));
        }
        if (text(result, "listingNo") != null) {
            if (text(result, "channelPublicationStatus") != null) {
                return "商品刊登 " + text(result, "listingNo") + "："
                        + listingPublicationStatusLabel(text(result, "channelPublicationStatus"));
            }
            return "商品刊登 " + text(result, "listingNo") + "：" + statusLabel(text(result, "currentStatus"));
        }
        if (text(result, "orderNo") != null) {
            return "订单 " + text(result, "orderNo") + "：" + statusLabel(text(result, "currentStatus"));
        }
        if (text(result, "paymentNo") != null) {
            return "支付单 " + text(result, "paymentNo") + "：" + statusLabel(text(result, "currentStatus"));
        }
        if (text(result, "fulfillmentNo") != null) {
            return "履约单 " + text(result, "fulfillmentNo") + "：" + statusLabel(text(result, "currentStatus"));
        }
        if (text(result, "afterSaleNo") != null) {
            return "售后单 " + text(result, "afterSaleNo") + "：" + statusLabel(text(result, "caseStatus"))
                    + "，退款 " + statusLabel(text(result, "refundStatus"));
        }
        if (result.has("availableQuantity")) {
            return "库存已更新：现货 " + result.path("onHandQuantity").asText("0")
                    + "，可售 " + result.path("availableQuantity").asText("0");
        }
        if (result.has("stockoutCount")) {
            return "缺货 " + result.path("stockoutCount").asInt()
                    + " 个 SKU，低库存 " + result.path("lowStockCount").asInt() + " 个 SKU";
        }
        if (text(result, "merchantStatus") != null || text(result, "shopStatus") != null) {
            return "商家 " + statusLabel(text(result, "merchantStatus"))
                    + "，店铺 " + statusLabel(text(result, "shopStatus"));
        }
        if (text(result, "warehouseStatus") != null) {
            return "仓库、库区和库位均已核验为可用";
        }
        if (text(result, "status") != null) {
            return "业务状态：" + statusLabel(text(result, "status"));
        }
        return "业务操作已成功完成";
    }

    public List<ManagedSkillTaskOutcomeObjectView> stepBusinessObjects(Step step) {
        JsonNode result = json(step.getResultJson());
        JsonNode request = json(step.getRequestJson());
        if (result.isMissingNode() || result.isNull()) {
            return List.of();
        }
        List<ManagedSkillTaskOutcomeObjectView> objects = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addObject(objects, seen, text(result, "entityType"),
                objectTypeLabel(text(result, "entityType")),
                text(result, "entityId"), text(result, "businessCode"),
                text(result, "currentStatus"));
        addObject(objects, seen, "LISTING", "商品刊登",
                text(result, "listingId"), text(result, "listingNo"),
                valueOr(text(result, "currentStatus"), text(result, "status")));
        addObject(objects, seen, "ORDER", "订单",
                text(result, "orderId"), text(result, "orderNo"),
                valueOr(text(result, "currentStatus"), text(result, "status")));
        addObject(objects, seen, "PAYMENT", "支付单",
                text(result, "paymentId"), text(result, "paymentNo"),
                valueOr(text(result, "currentStatus"), text(result, "status")));
        addObject(objects, seen, "FULFILLMENT", "履约单",
                text(result, "fulfillmentId"), text(result, "fulfillmentNo"),
                valueOr(text(result, "currentStatus"), text(result, "status")));
        addObject(objects, seen, "AFTERSALE", "售后单",
                text(result, "afterSaleId"), text(result, "afterSaleNo"),
                valueOr(text(result, "caseStatus"), text(result, "status")));
        addObject(objects, seen, "MERCHANT", "商家",
                text(result, "merchantId"), text(result, "merchantCode"),
                text(result, "merchantStatus"));
        addObject(objects, seen, "SHOP", "店铺",
                text(result, "shopId"), text(result, "shopCode"),
                text(result, "shopStatus"));
        addObject(objects, seen, "WAREHOUSE", "仓库",
                text(result, "warehouseId"), text(result, "warehouseCode"),
                text(result, "warehouseStatus"));
        addObject(objects, seen, "SPU", "SPU",
                text(result, "canonicalSpuId"),
                valueOr(text(result, "spuCode"), firstText(request, "spuCode")),
                text(result, "catalogStatus"));
        addObject(objects, seen, "SKU", "SKU",
                text(result, "canonicalSkuId"),
                valueOr(text(result, "skuCode"), firstText(request, "skuCode")),
                text(result, "catalogStatus"));
        return objects;
    }

    private ManagedSkillTaskBusinessOutcomeView activeSku(Task task, List<Step> steps) {
        JsonNode result = result(steps, "get-active-sku");
        String skuCode = valueOr(text(result, "skuCode"), text(json(task.getInputJson()), "skuId"));
        return outcome("CATALOG_QUERY",
                "SKU " + valueOr(skuCode, "目标商品") + " 已确认为在售",
                "商品目录状态为 " + statusLabel(text(result, "catalogStatus")) + "，款式、颜色和尺码信息读取成功。",
                List.of(metric("SPU", valueOr(text(result, "spuCode"), "1")),
                        metric("尺码", valueOr(text(result, "sizeName"), "-")),
                        metric("颜色", valueOr(text(result, "colorName"), "-"))),
                object("SKU", "在售 SKU", text(result, "canonicalSkuId"), skuCode,
                        text(result, "catalogStatus")), task);
    }

    private ManagedSkillTaskBusinessOutcomeView stockout(Task task, List<Step> steps) {
        JsonNode result = result(steps, "diagnose-size-stockout");
        String spuCode = valueOr(text(result, "spuCode"), text(json(task.getInputJson()), "canonicalSpuId"));
        int stockout = result.path("stockoutCount").asInt();
        int lowStock = result.path("lowStockCount").asInt();
        String headline = "SPU " + valueOr(spuCode, "目标商品") + " 库存诊断完成：缺货 "
                + stockout + " 个，低库存 " + lowStock + " 个";
        return outcome("STOCKOUT_DIAGNOSIS", headline,
                "已检查 " + result.path("skuCount").asInt() + " 个 SKU 的可售库存，并形成尺码级诊断结果。",
                List.of(metric("检查 SKU", result.path("skuCount").asText("0")),
                        metric("缺货 SKU", Integer.toString(stockout)),
                        metric("低库存 SKU", Integer.toString(lowStock))),
                object("SPU", "库存诊断对象", text(result, "canonicalSpuId"), spuCode,
                        text(result, "outcomeCode")), task);
    }

    private ManagedSkillTaskBusinessOutcomeView catalog(Task task, List<Step> steps) {
        JsonNode definitions = json(task.getInputJson()).path("definitions");
        JsonNode firstDefinition = definitions.isArray() && !definitions.isEmpty()
                ? definitions.get(0) : objectMapper.missingNode();
        JsonNode firstResult = firstResult(steps, "define_");
        JsonNode finalResult = result(steps, "activate_spu");
        String spuCode = valueOr(text(firstDefinition, "spuCode"), text(finalResult, "businessCode"));
        int skuCount = definitions.isArray() ? definitions.size() : countPrefix(steps, "define_");
        int colors = distinctCount(definitions, "colorCode");
        int sizes = distinctCount(definitions, "sizeCode");
        List<ManagedSkillTaskOutcomeObjectView> objects = new ArrayList<>();
        objects.add(object("SPU", "新品 SPU", text(firstResult, "canonicalSpuId"), spuCode,
                valueOr(text(finalResult, "currentStatus"), "ACTIVE")));
        for (int index = 0; definitions.isArray() && index < definitions.size(); index++) {
            JsonNode definition = definitions.get(index);
            JsonNode created = result(steps, "define_" + (index + 1));
            objects.add(object("SKU", "商品 SKU", text(created, "canonicalSkuId"),
                    text(definition, "skuCode"), "ACTIVE"));
        }
        return outcome("PRODUCT_CATALOG",
                "新品 " + valueOr(spuCode, "SPU") + " 已完成建档并启用",
                "款式、SPU、颜色、尺码和 SKU 均已建立，商品生命周期审批及启用完成。",
                List.of(metric("SPU", "1"), metric("SKU", Integer.toString(skuCount)),
                        metric("颜色", Integer.toString(colors)), metric("尺码", Integer.toString(sizes))),
                objects, task);
    }

    private ManagedSkillTaskBusinessOutcomeView aftersale(Task task, List<Step> steps) {
        JsonNode listing = result(steps, "listing_publish");
        JsonNode order = result(steps, "order_complete");
        JsonNode aftersale = result(steps, "wait_resolution");
        String afterSaleNo = text(aftersale, "afterSaleNo");
        String orderNo = text(order, "orderNo");
        String amount = money(aftersale.path("approvedAmountMinor"), text(aftersale, "currencyCode"));
        return outcome("AFTERSALE_REFUND",
                "售后单 " + valueOr(afterSaleNo, text(aftersale, "afterSaleId")) + " 已完成退款与库存恢复",
                "订单 " + valueOr(orderNo, text(order, "orderId"))
                        + " 已完成发布、支付、履约、退货质检和退款闭环。",
                List.of(metric("退款金额", amount), metric("售后状态", statusLabel(text(aftersale, "caseStatus"))),
                        metric("退款状态", statusLabel(text(aftersale, "refundStatus")))),
                List.of(
                        object("LISTING", "商品刊登", text(listing, "listingId"), text(listing, "listingNo"),
                                text(listing, "currentStatus")),
                        object("ORDER", "交易订单", text(order, "orderId"), orderNo, text(order, "currentStatus")),
                        object("AFTERSALE", "售后单", text(aftersale, "afterSaleId"), afterSaleNo,
                                text(aftersale, "caseStatus"))),
                task);
    }

    private ManagedSkillTaskBusinessOutcomeView productToListing(Task task, List<Step> steps) {
        JsonNode catalog = result(steps, "wait_catalog");
        JsonNode firstCatalog = catalog.at("/outputs/define_1");
        JsonNode listing = result(steps, "listing_terminal_readback");
        JsonNode merchant = result(steps, "wait_master").at("/outputs/merchant_approve");
        JsonNode definitions = json(task.getInputJson()).path("catalog").path("definitions");
        String spuCode = valueOr(text(firstCatalog, "spuCode"), text(definitions.path(0), "spuCode"));
        int skuCount = definitions.isArray() ? definitions.size() : countPrefix(steps, "define_");
        String listingNo = valueOr(text(listing, "listingNo"), text(result(steps, "listing_publish"), "listingNo"));
        String headline = switch (text(listing, "overallResultCode")) {
            case "PENDING_CONFIRMATION" ->
                    "新品 " + valueOr(spuCode, "SPU") + " 已完成规范刊登，待渠道确认";
            case "PUBLISHED_CONFIRMED" ->
                    "新品 " + valueOr(spuCode, "SPU") + " 已完成铺品并收到渠道确认";
            case "CONFIRMED_PUBLISHED" ->
                    "新品 " + valueOr(spuCode, "SPU") + " 已完成铺品并收到渠道确认";
            case "CHANNEL_PUBLISH_FAILED" ->
                    "新品 " + valueOr(spuCode, "SPU") + " 渠道发布失败";
            default -> "新品 " + valueOr(spuCode, "SPU") + " 已完成铺品执行";
        };
        String summary = switch (text(listing, "overallResultCode")) {
            case "PENDING_CONFIRMATION" ->
                    "规范商品建档、商家店铺准备和刊登审核发布均已完成，刊登 "
                            + valueOr(listingNo, "目标刊登") + " 当前仅有规范侧已发布证据，尚未收到真实渠道终态回读。";
            case "PUBLISHED_CONFIRMED", "CONFIRMED_PUBLISHED" ->
                    "规范商品建档、商家店铺准备和刊登审核发布均已完成，渠道终态已确认。";
            case "CHANNEL_PUBLISH_FAILED" ->
                    "规范商品建档、商家店铺准备和刊登审核发布均已完成，但真实渠道回执返回失败："
                            + valueOr(text(listing, "failureMessage"), valueOr(text(listing, "failureCode"), "未知错误")) + "。";
            default ->
                    "规范商品建档、商家店铺准备和刊登发布链路已执行完成，终态以当前可读取证据为准。";
        };
        List<ManagedSkillTaskOutcomeObjectView> objects = new ArrayList<>();
        objects.add(object("SPU", "新品 SPU", text(firstCatalog, "canonicalSpuId"),
                spuCode, "ACTIVE"));
        objects.add(object("LISTING", "商品刊登", text(listing, "listingId"),
                listingNo, valueOr(text(listing, "currentStatus"), text(listing, "channelPublicationStatus"))));
        if (text(merchant, "merchantId") != null) {
            objects.add(object("MERCHANT", "商家", text(merchant, "merchantId"),
                    text(merchant, "merchantCode"), text(merchant, "merchantStatus")));
        }
        if (text(merchant, "shopId") != null) {
            objects.add(object("SHOP", "店铺", text(merchant, "shopId"),
                    text(merchant, "shopCode"), text(merchant, "shopStatus")));
        }
        return outcome("PRODUCT_TO_LISTING",
                headline,
                summary,
                List.of(metric("SKU", Integer.toString(skuCount)),
                        metric("刊登", valueOr(listingNo, "已创建")),
                        metric("渠道状态", listingPublicationStatusLabel(text(listing, "channelPublicationStatus")))),
                objects,
                task);
    }

    private ManagedSkillTaskBusinessOutcomeView projection(Task task, List<Step> steps) {
        int skuCount = countPrefix(steps, "plan_");
        int projectionCount = steps.stream().filter(step -> step.getStepCode().startsWith("plan_"))
                .map(this::resultNode).mapToInt(node -> node.path("projections").size()).sum();
        return outcome("LEGACY_PROJECTION_PLAN",
                skuCount + " 个 SKU 的旧系统投影方案已生成",
                "已完成 Mall、ERP、WMS 兼容投影预检，本任务只生成方案，没有执行旧系统写入。",
                List.of(metric("SKU", Integer.toString(skuCount)),
                        metric("投影项", Integer.toString(projectionCount))),
                List.of(), task);
    }

    private ManagedSkillTaskBusinessOutcomeView masterData(Task task, List<Step> steps) {
        JsonNode merchant = result(steps, "shop_activate");
        JsonNode warehouse = result(steps, "warehouse_network");
        return outcome("MERCHANT_WAREHOUSE_READY",
                "商家店铺与仓网已准备就绪",
                "商家、店铺、仓库、库区和库位均已激活，并完成 ERP 仓库来源映射。",
                List.of(metric("商家状态", statusLabel(text(merchant, "merchantStatus"))),
                        metric("店铺状态", statusLabel(text(merchant, "shopStatus"))),
                        metric("仓库状态", statusLabel(text(warehouse, "warehouseStatus")))),
                List.of(
                        object("MERCHANT", "商家", text(merchant, "merchantId"), null,
                                text(merchant, "merchantStatus")),
                        object("SHOP", "店铺", text(merchant, "shopId"), null, text(merchant, "shopStatus")),
                        object("WAREHOUSE", "仓库", text(warehouse, "warehouseId"), null,
                                text(warehouse, "warehouseStatus"))),
                task);
    }

    private ManagedSkillTaskBusinessOutcomeView readback(Task task, List<Step> steps) {
        JsonNode listing = result(steps, "listing_offer");
        JsonNode order = result(steps, "order_attribution");
        JsonNode aftersale = result(steps, "aftersale_by_item");
        JsonNode refund = result(steps, "payment_refund_refunded");
        return outcome("TERMINAL_READBACK",
                "商品交易与售后终态核验通过",
                "商品已发布、订单归属正确、正向履约已签收、退货质检通过且退款已到账。",
                List.of(metric("刊登", valueOr(text(listing, "listingNo"), "已发布")),
                        metric("订单", valueOr(text(order, "orderNo"), "已核验")),
                        metric("退款", statusLabel(text(refund, "status")))),
                List.of(
                        object("LISTING", "商品刊登", text(listing, "listingId"), text(listing, "listingNo"),
                                "PUBLISHED"),
                        object("ORDER", "交易订单", text(order, "orderId"), text(order, "orderNo"),
                                text(order, "status")),
                        object("AFTERSALE", "售后单", text(aftersale, "afterSaleId"),
                                text(aftersale, "afterSaleNo"), text(aftersale, "caseStatus"))),
                task);
    }

    private ManagedSkillTaskBusinessOutcomeView fullChain(Task task, List<Step> steps) {
        long completed = steps.stream()
                .filter(step -> step.getStepCode().startsWith("wait_") && "SUCCEEDED".equals(step.getStatus()))
                .count();
        return outcome("COMMERCE_FULL_CHAIN",
                "商品售后自治全链路已完成",
                "商品建档、旧系统投影预检、商家仓网准备、售后退款和终态核验 "
                        + completed + " 条子流程全部成功。",
                List.of(metric("完成子流程", Long.toString(completed)),
                        metric("成功步骤", Integer.toString(succeededCount(steps)))),
                List.of(), task);
    }

    private ManagedSkillTaskBusinessOutcomeView genericSuccess(Task task, List<Step> steps) {
        String name = SKILL_NAMES.getOrDefault(task.getSkillId(), "Agent 任务");
        return outcome("GENERIC", name + "已完成",
                "任务共完成 " + succeededCount(steps) + " 个步骤，业务结果已通过终态校验。",
                List.of(metric("成功步骤", Integer.toString(succeededCount(steps)))), List.of(), task);
    }

    private static String listingPublicationStatusLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "待核验";
        }
        return switch (status) {
            case "PENDING_CONFIRMATION" -> "待渠道确认";
            case "CONFIRMED", "CONFIRMED_PUBLISHED" -> "渠道已确认";
            case "CHANNEL_PUBLISH_FAILED" -> "渠道发布失败";
            case "NOT_PUBLISHED" -> "未上架";
            case "NOT_READY" -> "未满足上架条件";
            default -> status;
        };
    }

    private ManagedSkillTaskBusinessOutcomeView nonSuccess(Task task, List<Step> steps) {
        String name = SKILL_NAMES.getOrDefault(task.getSkillId(), "Agent 任务");
        String headline = switch (task.getStatus()) {
            case "RUNNING" -> name + "正在执行";
            case "WAITING_APPROVAL" -> name + "等待审批";
            case "FAILED" -> name + "未完成";
            default -> name + "：" + statusLabel(task.getStatus());
        };
        return outcome("EXECUTION_STATUS", headline,
                "已完成 " + succeededCount(steps) + " 个步骤，当前状态为 " + statusLabel(task.getStatus()) + "。",
                List.of(metric("已完成步骤", Integer.toString(succeededCount(steps)))),
                List.of(), task);
    }

    private ManagedSkillTaskBusinessOutcomeView outcome(String type, String headline, String summary,
                                                        List<ManagedSkillTaskOutcomeMetricView> metrics,
                                                        ManagedSkillTaskOutcomeObjectView businessObject,
                                                        Task task) {
        return outcome(type, headline, summary, metrics,
                businessObject == null ? List.of() : List.of(businessObject), task);
    }

    private ManagedSkillTaskBusinessOutcomeView outcome(String type, String headline, String summary,
                                                        List<ManagedSkillTaskOutcomeMetricView> metrics,
                                                        List<ManagedSkillTaskOutcomeObjectView> businessObjects,
                                                        Task task) {
        return ManagedSkillTaskBusinessOutcomeView.builder()
                .outcomeType(type)
                .headline(headline)
                .summary(summary)
                .metrics(metrics)
                .businessObjects(businessObjects)
                .evidenceSha256(task.getTerminalResultSha256())
                .build();
    }

    private static ManagedSkillTaskOutcomeMetricView metric(String label, String value) {
        return ManagedSkillTaskOutcomeMetricView.builder().label(label).value(valueOr(value, "-")).build();
    }

    private static ManagedSkillTaskOutcomeObjectView object(String type, String label, String id, String code,
                                                            String status) {
        return ManagedSkillTaskOutcomeObjectView.builder()
                .objectType(type)
                .label(label)
                .businessId(id)
                .businessCode(code)
                .status(status)
                .build();
    }

    private static void addObject(List<ManagedSkillTaskOutcomeObjectView> objects, Set<String> seen,
                                  String type, String label, String id, String code, String status) {
        if (!StringUtils.hasText(id) && !StringUtils.hasText(code)) {
            return;
        }
        String normalizedType = valueOr(type, "BUSINESS_OBJECT");
        String key = normalizedType + "|" + valueOr(id, "") + "|" + valueOr(code, "");
        if (!seen.add(key)) {
            return;
        }
        objects.add(object(normalizedType, valueOr(label, objectTypeLabel(normalizedType)),
                id, code, status));
    }

    private JsonNode result(List<Step> steps, String stepCode) {
        return steps.stream().filter(step -> stepCode.equals(step.getStepCode()))
                .findFirst().map(this::resultNode).orElse(objectMapper.missingNode());
    }

    private JsonNode firstResult(List<Step> steps, String prefix) {
        return steps.stream().filter(step -> step.getStepCode().startsWith(prefix))
                .findFirst().map(this::resultNode).orElse(objectMapper.missingNode());
    }

    private JsonNode resultNode(Step step) {
        return json(step.getResultJson());
    }

    private JsonNode json(String raw) {
        if (!StringUtils.hasText(raw)) {
            return objectMapper.missingNode();
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ignored) {
            return objectMapper.missingNode();
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || !node.hasNonNull(field)) {
            return null;
        }
        String value = node.path(field).asText();
        return StringUtils.hasText(value) ? value : null;
    }

    private static String firstText(JsonNode node, String field) {
        if (node != null && node.isArray() && !node.isEmpty()) {
            return text(node.get(0), field);
        }
        return text(node, field);
    }

    private static Boolean bool(JsonNode node, String field) {
        return node != null && node.has(field) ? node.path(field).asBoolean() : null;
    }

    private static int distinctCount(JsonNode values, String field) {
        if (values == null || !values.isArray()) {
            return 0;
        }
        Set<String> distinct = new LinkedHashSet<>();
        values.forEach(value -> {
            String text = text(value, field);
            if (text != null) {
                distinct.add(text);
            }
        });
        return distinct.size();
    }

    private static int countPrefix(List<Step> steps, String prefix) {
        return (int) steps.stream().filter(step -> step.getStepCode().startsWith(prefix)).count();
    }

    private static int succeededCount(List<Step> steps) {
        return (int) steps.stream().filter(step -> "SUCCEEDED".equals(step.getStatus())).count();
    }

    private static String numericSuffix(String value) {
        int separator = value.lastIndexOf('_');
        return separator >= 0 ? value.substring(separator + 1) : value;
    }

    private static String childSkillName(Step step) {
        String childSkillId = StringUtils.hasText(step.getChildSkillId())
                ? step.getChildSkillId()
                : COMPOSITION_SKILL_IDS.get(step.getStepCode().replaceFirst("^(submit|wait)_", ""));
        return StringUtils.hasText(childSkillId)
                ? SKILL_NAMES.getOrDefault(childSkillId, childSkillId)
                : step.getStepCode().replaceFirst("^(submit|wait)_", "");
    }

    private static String objectTypeLabel(String type) {
        if (type == null) {
            return "业务对象";
        }
        return switch (type) {
            case "STYLE" -> "款式";
            case "SPU" -> "SPU";
            case "SKU" -> "SKU";
            case "COLOR" -> "颜色";
            case "SIZE" -> "尺码";
            case "SIZE_GROUP" -> "尺码组";
            default -> type;
        };
    }

    private static String statusLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "-";
        }
        return switch (status) {
            case "ACTIVE" -> "已启用";
            case "SUBMITTED" -> "已提交";
            case "PUBLISHED" -> "已发布";
            case "SUCCEEDED" -> "成功";
            case "COMPLETED", "RESOLVED", "CLOSED" -> "已完成";
            case "DELIVERED" -> "已签收";
            case "REFUNDED" -> "已退款";
            case "APPROVED", "ACCEPTED" -> "已通过";
            case "READY" -> "已就绪";
            case "RUNNING" -> "运行中";
            case "WAITING_APPROVAL" -> "等待审批";
            case "FAILED" -> "失败";
            default -> status;
        };
    }

    private static String money(JsonNode amountMinor, String currencyCode) {
        if (amountMinor == null || !amountMinor.isNumber()) {
            return "-";
        }
        BigDecimal amount = BigDecimal.valueOf(amountMinor.asLong())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return amount.toPlainString() + " " + valueOr(currencyCode, "CNY");
    }

    private static String valueOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
