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

    private static final Map<String, String> SKILL_NAMES = Map.ofEntries(
            Map.entry("skill.cloudmold.agentcontrol.mission-lifecycle-stockout.v1",
                    "缺断码 Mission 生命周期"),
            Map.entry("skill.cloudmold.catalog.inspect-active-sku.v1", "在售 SKU 查询"),
            Map.entry("skill.cloudmold.catalog.assortment-wave-readiness.v1", "波段企划准备度"),
            Map.entry("skill.cloudmold.inventory.stockout-diagnosis.v1", "尺码缺断码诊断"),
            Map.entry("skill.cloudmold.listing.lifecycle-readback.v1", "商品刊登生命周期终态跟踪"),
            Map.entry("skill.cloudmold.commerce.catalog-matrix.v1", "商品款色码建档"),
            Map.entry("skill.cloudmold.commerce.product-to-listing.v1", "自动铺品"),
            Map.entry("skill.cloudmold.commerce.autonomous-day.v1", "AI 自主经营日"),
            Map.entry("skill.cloudmold.consumer.shopping-journey.v1", "消费者选购与服务全旅程"),
            Map.entry("skill.cloudmold.commerce.aftersale-saga.v1", "售后退款全链路"),
            Map.entry("skill.cloudmold.commerce.legacy-projection-plan.v1", "旧系统投影预检"),
            Map.entry("skill.cloudmold.commerce.reuse-ready-master.v1", "商家与仓网主数据准备"),
            Map.entry("skill.cloudmold.commerce.terminal-readback.v1", "全链路终态核验"),
            Map.entry("skill.cloudmold.commerce.full-chain-hsf.v1", "商品售后自治全链路"),
            Map.entry("skill.cloudmold.supply-planning.prepare.v1", "补货单准备"),
            Map.entry("skill.cloudmold.operations.daily-business-control.v1", "日经营控制"),
            Map.entry("skill.cloudmold.operations.weekly-business-review.v1", "周经营复盘"),
            Map.entry("skill.cloudmold.merchant.onboarding-readback.v1", "商家入驻终态跟踪"),
            Map.entry("skill.cloudmold.merchant.onboarding-lifecycle.v1", "商家入驻经营闭环"),
            Map.entry("skill.cloudmold.engagement.promotion-campaign-operations.v1", "促销活动投放闭环"),
            Map.entry("skill.cloudmold.growth.experiment-lifecycle.v1", "增长实验决策闭环"),
            Map.entry("skill.cloudmold.supplier.sourcing-lifecycle.v1", "供应商寻源定标闭环"),
            Map.entry("skill.cloudmold.procurement.order-lifecycle.v1", "采购订单履约闭环"),
            Map.entry("skill.cloudmold.wms.operations.v1", "仓储收发调盘闭环"),
            Map.entry("skill.cloudmold.supply.replenishment-lifecycle.v1", "智能补货执行闭环"),
            Map.entry("skill.cloudmold.supply-planning.sop-lifecycle.v1",
                    "需求预测与 S&OP 决策闭环"),
            Map.entry("skill.cloudmold.customer-service.resolution-lifecycle.v1", "客服咨询解决闭环"),
            Map.entry("skill.cloudmold.engagement.promotion-campaign-readback.v1", "促销活动终态跟踪"),
            Map.entry("skill.cloudmold.engagement.growth-experiment-readback.v1", "增长实验终态跟踪"),
            Map.entry("skill.cloudmold.commerce.order-cancellation-operational.v1", "订单取消补偿闭环"),
            Map.entry("skill.cloudmold.commerce.order-to-cash-readback.v1", "订单到回款终态跟踪"),
            Map.entry("skill.cloudmold.commerce.order-cancellation-readback.v1", "订单取消终态跟踪"),
            Map.entry("skill.cloudmold.commerce.fulfillment-exception-readback.v1", "履约异常终态跟踪"),
            Map.entry("skill.cloudmold.fulfillment.exception-resolution-lifecycle.v1",
                    "履约异常处置闭环"),
            Map.entry("skill.cloudmold.crossborder.fulfillment-compliance-lifecycle.v1",
                    "跨境履约与关务合规闭环"),
            Map.entry("skill.cloudmold.crossborder.bonded-customs-lifecycle.v1",
                    "保税仓关务闭环"),
            Map.entry("skill.cloudmold.consumer.in-transit-order-scenario.v1", "在途订单场景准备"),
            Map.entry("skill.cloudmold.commerce.return-refund-readback.v1", "退货退款终态跟踪"),
            Map.entry("skill.cloudmold.customer-service.resolution-readback.v1", "客户问题解决终态跟踪"),
            Map.entry("skill.cloudmold.quality.recall-readback.v1", "质量召回终态跟踪"),
            Map.entry("skill.cloudmold.quality.inspection-recall-lifecycle.v1", "质量检验与召回闭环"),
            Map.entry("skill.cloudmold.risk.dispute-readback.v1", "风险争议终态跟踪"),
            Map.entry("skill.cloudmold.payment.reconciliation-readback.v1", "支付对账终态跟踪"),
            Map.entry("skill.cloudmold.procurement.supplier-confirmation-readback.v1", "供应商采购确认跟踪"),
            Map.entry("skill.cloudmold.supplier.sourcing-decision-readback.v1", "供应商寻源定标终态跟踪"),
            Map.entry("skill.cloudmold.finance.close-readiness.v1", "财务关账准备度跟踪"),
            Map.entry("skill.cloudmold.finance.close-lifecycle.v1", "财务结算关账闭环"),
            Map.entry("skill.cloudmold.warehouse.allocation-transfer-readback.v1", "库存调拨终态跟踪"),
            Map.entry("skill.cloudmold.warehouse.inbound-readback.v1", "仓库入库终态跟踪")
    );

    private static final Map<String, String> SKILL_DESCRIPTIONS = Map.ofEntries(
            Map.entry("skill.cloudmold.agentcontrol.mission-lifecycle-stockout.v1",
                    "在审批后创建固定缺断码处置 Mission 并回读真实工作图；不冒充通用 Mission Charter、预算、KPI 或动态 DAG。"),
            Map.entry("skill.cloudmold.catalog.inspect-active-sku.v1",
                    "查询当前租户指定 SKU 的规范商品与在售状态，不修改业务数据。"),
            Map.entry("skill.cloudmold.catalog.assortment-wave-readiness.v1",
                    "按年度、季节和波段汇总真实 Catalog 事实；趋势、价格带、款量、毛利或供给计划缺失时明确阻塞，不冒充完整企划案。"),
            Map.entry("skill.cloudmold.inventory.stockout-diagnosis.v1",
                    "按 SPU 检查各尺码可售库存，识别缺货与低库存风险，不修改业务数据。"),
            Map.entry("skill.cloudmold.listing.lifecycle-readback.v1",
                    "持续核验刊登发布、渠道回执、暂停、下架、归档及销售资格失效后的批量下架结果。"),
            Map.entry("skill.cloudmold.commerce.catalog-matrix.v1",
                    "建立款式、SPU 与 6 个 SKU，并完成商品生命周期激活。"),
            Map.entry("skill.cloudmold.commerce.product-to-listing.v1",
                    "串联规范商品建档、商家店铺准备、商品刊登审核发布与终态回读，缺少渠道回执时明确标记待渠道确认。"),
            Map.entry("skill.cloudmold.commerce.autonomous-day.v1",
                    "每日生成全新商品与刊登，再由独立消费者身份完成选购、支付履约、售后、客服和社区种草闭环。"),
            Map.entry("skill.cloudmold.consumer.shopping-journey.v1",
                    "模拟真实会员完成搜索、商详、收藏、加购、结算、下单支付、履约、售后、咨询与社区发布。"),
            Map.entry("skill.cloudmold.commerce.aftersale-saga.v1",
                    "贯通发布、库存、下单支付、履约、退货质检、退款和库存恢复。"),
            Map.entry("skill.cloudmold.commerce.legacy-projection-plan.v1",
                    "只读规划规范 SKU 向 Mall、ERP 与 WMS 的兼容投影，不执行旧系统写入。"),
            Map.entry("skill.cloudmold.commerce.reuse-ready-master.v1",
                    "校验身份与 ERP 仓，创建并激活商家店铺，绑定可用仓网。"),
            Map.entry("skill.cloudmold.commerce.terminal-readback.v1",
                    "只读核验商品、商家、刊登、订单、支付、履约、售后及仓网终态。"),
            Map.entry("skill.cloudmold.commerce.full-chain-hsf.v1",
                    "依次编排商品建档、旧系统投影、商家仓网、售后 Saga 与终态核验。"),
            Map.entry("skill.cloudmold.supply-planning.prepare.v1",
                    "将已批准的补货建议转换为真实采购或调拨草稿，并明确后续等待的供应商或仓储事件。"),
            Map.entry("skill.cloudmold.operations.daily-business-control.v1",
                    "只读汇总当日经营指标、异常与建议工单；事实不完整时保持待数据状态。"),
            Map.entry("skill.cloudmold.operations.weekly-business-review.v1",
                    "只读汇总周度目标偏差、异常与行动建议；事实不完整时保持待数据状态。"),
            Map.entry("skill.cloudmold.merchant.onboarding-readback.v1",
                    "持续回读商家申请、门店与授权终态，不代替人工审批或领域写入。"),
            Map.entry("skill.cloudmold.merchant.onboarding-lifecycle.v1",
                    "模拟招商运营完成资料建档、提交、审核、批准、商家激活、店铺激活与终态验收；每天创建全新的测试商家。"),
            Map.entry("skill.cloudmold.engagement.promotion-campaign-operations.v1",
                    "模拟活动运营完成活动启用、人群触达、渠道发送、送达、打开、点击、活动收尾与终态验收；每天生成全新活动。"),
            Map.entry("skill.cloudmold.growth.experiment-lifecycle.v1",
                    "模拟增长运营完成实验活动、分组、曝光、指标快照、护栏判断、显著性结论与终态验收；每天生成全新实验。"),
            Map.entry("skill.cloudmold.supplier.sourcing-lifecycle.v1",
                    "模拟采购寻源岗位完成双供应商准入、RFQ、双报价、样品评估、成本产能比较、定标与终态验收；每天生成全新寻源案例。"),
            Map.entry("skill.cloudmold.procurement.order-lifecycle.v1",
                    "基于最近一次真实定标结果创建采购订单，完成审批下发、供应商确认与终态验收；每天生成全新采购订单。"),
            Map.entry("skill.cloudmold.wms.operations.v1",
                    "模拟仓储运营完成双仓与商品造数、采购收货、仓间调拨、销售出库、零差异盘点和最终库存验收；每天生成全新业务单据。"),
            Map.entry("skill.cloudmold.supply.replenishment-lifecycle.v1",
                    "模拟补货运营完成需求分型、双供应商寻源定标、采购订单下发以及收货、调拨、出库、盘点和库存验收；自动轮换日常与大促补货。"),
            Map.entry("skill.cloudmold.supply-planning.sop-lifecycle.v1",
                    "模拟需求计划经理完成预测发布与偏差评估、精益和韧性双场景测算、AI 鲁棒方案推荐、会签发布、补货批准，并转换为真实 WMS 调拨草稿。"),
            Map.entry("skill.cloudmold.customer-service.resolution-lifecycle.v1",
                    "模拟用户咨询与客服岗位完成建单、关联订单、消息接收、分派、处理、解决、满意度反馈、关单和终态验收；每天产生新的客服案例。"),
            Map.entry("skill.cloudmold.engagement.promotion-campaign-readback.v1",
                    "持续回读活动与投放结果；依赖数据未齐备时保持等待并展示阻塞项。"),
            Map.entry("skill.cloudmold.engagement.growth-experiment-readback.v1",
                    "持续回读增长实验结果；样本或归因未齐备时保持等待并展示阻塞项。"),
            Map.entry("skill.cloudmold.commerce.order-cancellation-operational.v1",
                    "提交真实订单取消补偿 Saga START，并持续回读至成功或人工复核；"
                            + "PAID_UNSHIPPED 当前仅自动覆盖 INTERNAL_TEST 支付退款，真实 PSP 仍由外部权威负责。"),
            Map.entry("skill.cloudmold.commerce.order-to-cash-readback.v1",
                    "持续核验订单、库存、支付与履约事实，直至订单到回款链路形成终态。"),
            Map.entry("skill.cloudmold.commerce.order-cancellation-readback.v1",
                    "持续核验取消 Saga、库存释放与退款事实，异常或人工处理会明确留痕。"),
            Map.entry("skill.cloudmold.commerce.fulfillment-exception-readback.v1",
                    "持续核验订单履约异常的处理结果，未闭环时保持等待或人工处理状态。"),
            Map.entry("skill.cloudmold.fulfillment.exception-resolution-lifecycle.v1",
                    "模拟物流经理为全新在途订单完成异常识别分级、影响诊断、处置方案、R3 审批、承运执行、恢复交付、订单完结、异常关单与终态验收。"),
            Map.entry("skill.cloudmold.crossborder.fulfillment-compliance-lifecycle.v1",
                    "模拟跨境运营岗位为全新已支付订单完成受控直邮合规评估、AI 路线推荐、双岗会签、申报资料与三单校验、国际承运、清关放行、末端妥投及关单；"
                            + "当前仅为 CN→US 测试规则包，不冒充通用税则或真实海关、承运商权威。"),
            Map.entry("skill.cloudmold.crossborder.bonded-customs-lifecycle.v1",
                    "模拟保税仓关务岗位为全新已支付订单完成 179 测试规则下的准入评估、商品归类、订单/支付/物流三单对碰、"
                            + "税费计算、风险与法务会签、申报受理、保税放行、境内妥投及关单；不冒充真实海关或税则权威。"),
            Map.entry("skill.cloudmold.consumer.in-transit-order-scenario.v1",
                    "内部造数子流程：模拟会员选购、下单支付、出库和在途运输，为履约异常岗位主流程提供全新业务对象。"),
            Map.entry("skill.cloudmold.commerce.return-refund-readback.v1",
                    "持续核验售后、退货质检、退款与库存恢复事实，直至闭环终态。"),
            Map.entry("skill.cloudmold.customer-service.resolution-readback.v1",
                    "持续回读客服工单和解决结果，不越过客服审批或领域写入边界。"),
            Map.entry("skill.cloudmold.quality.recall-readback.v1",
                    "持续回读质量召回动作及影响范围，证据不完整时保持等待并展示阻塞项。"),
            Map.entry("skill.cloudmold.quality.inspection-recall-lifecycle.v1",
                    "模拟质量运营岗位完成标准发布、双人持证检验、CAPA、批次召回、库存隔离和终态验收；每天生成全新质检批次。"),
            Map.entry("skill.cloudmold.risk.dispute-readback.v1",
                    "持续回读风险争议处置结果，未决或待人工裁定时不会误报成功。"),
            Map.entry("skill.cloudmold.payment.reconciliation-readback.v1",
                    "持续核验订单与支付对账结果，账实未一致时保持等待并展示阻塞项。"),
            Map.entry("skill.cloudmold.procurement.supplier-confirmation-readback.v1",
                    "从真实采购单创建事件持续核验供应商确认状态；仅覆盖采购确认，不冒充 RFQ、比价、样品或供应商准入。"),
            Map.entry("skill.cloudmold.supplier.sourcing-decision-readback.v1",
                    "持续核验正式 RFQ、至少两家供应商报价、准入、样品通过和定标结果；仅领域 AWARDED 终态视为成功。"),
            Map.entry("skill.cloudmold.finance.close-readiness.v1",
                    "持续核验渠道账单、对账差异、结算批次和已过账凭证；仅规范会计期间 CLOSED 终态视为成功。"),
            Map.entry("skill.cloudmold.finance.close-lifecycle.v1",
                    "模拟财务结算岗位以制单、复核双身份完成期间开启、渠道账单导入、差异调整、结算确认、凭证制备与过账、期间关闭及终态验收；每天生成全新账期。"),
            Map.entry("skill.cloudmold.warehouse.allocation-transfer-readback.v1",
                    "从已批准的补货转换事件持续核验真实 WMS 移库单；仅移库完成视为成功，作废明确进入失败终态。"),
            Map.entry("skill.cloudmold.warehouse.inbound-readback.v1",
                    "持续核验 ASN、收货与上架状态；仅上架完成视为成功，ASN 取消明确进入失败终态。")
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
            Map.entry("inspect_listing_lifecycle", "核验商品刊登生命周期终态"),
            Map.entry("submit_in_transit_order", "生成全新在途订单"),
            Map.entry("submit_paid_in_transit_order", "生成全新已支付跨境订单"),
            Map.entry("wait_paid_in_transit_order", "等待跨境订单进入在途"),
            Map.entry("create_crossborder_case", "建立跨境履约案件"),
            Map.entry("assess_trade_compliance", "AI 评估贸易合规与路线"),
            Map.entry("select_ai_recommended_route", "选择 AI 推荐直邮路线"),
            Map.entry("approve_compliance_plan", "记录跨境合规会签"),
            Map.entry("assemble_customs_declaration", "组装海关申报资料"),
            Map.entry("validate_order_payment_logistics", "校验订单、支付与物流三单"),
            Map.entry("book_international_carrier", "预订国际承运服务"),
            Map.entry("record_carrier_label", "登记国际面单与运单号"),
            Map.entry("handover_export_shipment", "完成出口交接"),
            Map.entry("submit_customs_declaration", "提交受控海关申报"),
            Map.entry("record_customs_release", "登记海关放行回执"),
            Map.entry("record_last_mile_delivery", "登记末端妥投回执"),
            Map.entry("close_crossborder_case", "关闭跨境履约案件"),
            Map.entry("verify_crossborder_case_closed", "验收放行、妥投与关单终态"),
            Map.entry("create_bonded_case", "建立保税关务案件"),
            Map.entry("assess_eligibility", "AI 评估保税进口准入"),
            Map.entry("classify_goods", "归类商品并核验正面清单"),
            Map.entry("match_triple_orders", "对碰订单、支付与物流三单"),
            Map.entry("calculate_tax", "计算受控测试税费"),
            Map.entry("approve_declaration", "记录风险与法务会签"),
            Map.entry("submit_declaration", "提交保税进口申报"),
            Map.entry("accept_customs", "登记海关受理回执"),
            Map.entry("release_bonded_stock", "登记保税库存放行"),
            Map.entry("confirm_delivery", "确认境内末端妥投"),
            Map.entry("close_case", "关闭保税关务案件"),
            Map.entry("verify_bonded_case_closed", "验收三单、受理、放行、妥投与关单终态"),
            Map.entry("wait_in_transit_order", "等待订单进入在途"),
            Map.entry("open_exception", "识别并登记履约异常"),
            Map.entry("plan_response", "诊断影响并制定处置方案"),
            Map.entry("record_approval", "登记真实审批证据"),
            Map.entry("start_response", "启动承运处置"),
            Map.entry("resolve_exception", "确认异常恢复"),
            Map.entry("deliver_recovered_shipment", "完成恢复后交付"),
            Map.entry("complete_recovered_order", "完结恢复订单"),
            Map.entry("close_exception", "关闭履约异常"),
            Map.entry("verify_exception_closed", "验收异常关单"),
            Map.entry("verify_delivery_completed", "验收交付终态"),
            Map.entry("create_forecast", "生成需求预测"),
            Map.entry("publish_forecast", "发布需求预测"),
            Map.entry("evaluate_forecast", "评估预测偏差"),
            Map.entry("create_supply_plan", "制定供需平衡计划"),
            Map.entry("evaluate_lean_scenario", "测算精益供给场景"),
            Map.entry("evaluate_resilient_scenario", "测算韧性供给场景"),
            Map.entry("recommend_plan_scenario", "AI 推荐鲁棒场景"),
            Map.entry("select_recommended_scenario", "选择推荐供给场景"),
            Map.entry("approve_supply_plan", "会签供需计划"),
            Map.entry("release_supply_plan", "发布供需计划"),
            Map.entry("approve_replenishment", "批准补货建议"),
            Map.entry("propose_transfer_execution", "制定调拨执行方案"),
            Map.entry("convert_to_transfer_draft", "生成真实调拨草稿"),
            Map.entry("verify_transfer_draft", "验收调拨执行交接"),
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
            Map.entry("consumer_principal", "核验模拟消费者身份"),
            Map.entry("listing_published", "核验商品可选购"),
            Map.entry("session_start", "进入商城"),
            Map.entry("session_link", "识别会员身份"),
            Map.entry("search_requested", "搜索商品"),
            Map.entry("search_exposed", "浏览搜索结果"),
            Map.entry("search_clicked", "点击搜索商品"),
            Map.entry("pdp_viewed", "查看商品详情"),
            Map.entry("favorite_added", "收藏商品"),
            Map.entry("cart_added", "加入购物车"),
            Map.entry("checkout_started", "发起结算"),
            Map.entry("payment_attribution", "归因成交转化"),
            Map.entry("ticket_create", "发起客服咨询"),
            Map.entry("ticket_link_order", "关联咨询订单"),
            Map.entry("ticket_message", "发送咨询消息"),
            Map.entry("ticket_assign", "分配客服"),
            Map.entry("ticket_start", "客服开始处理"),
            Map.entry("ticket_resolve", "解决客户问题"),
            Map.entry("ticket_feedback", "记录客户评价"),
            Map.entry("ticket_close", "关闭客服工单"),
            Map.entry("community_create", "创建社区种草内容"),
            Map.entry("community_submit", "提交社区内容审核"),
            Map.entry("community_publish", "发布社区种草内容"),
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
            Map.entry("link_finance_maker", "建立财务制单身份"),
            Map.entry("link_finance_checker", "建立财务复核身份"),
            Map.entry("open_period", "开启会计期间"),
            Map.entry("import_statement", "导入渠道账单"),
            Map.entry("reconcile_statement", "执行渠道对账"),
            Map.entry("resolve_difference", "复核并解决对账差异"),
            Map.entry("create_settlement", "生成结算批次"),
            Map.entry("confirm_settlement", "复核银行结算"),
            Map.entry("prepare_journal", "编制会计凭证"),
            Map.entry("post_journal", "复核并过账凭证"),
            Map.entry("close_period", "关闭会计期间"),
            Map.entry("verify_closed_period", "验收财务关账终态"),
            Map.entry("link_primary_inspector", "建立主检员身份"),
            Map.entry("link_independent_reviewer", "建立独立复检员身份"),
            Map.entry("create_quality_standard", "制定质量检验标准"),
            Map.entry("publish_quality_standard", "独立审核并发布质量标准"),
            Map.entry("certify_primary_inspector", "认证主检员资质"),
            Map.entry("certify_independent_reviewer", "认证独立复检员资质"),
            Map.entry("register_inspection_lot", "登记待检库存批次"),
            Map.entry("receive_inspection_stock", "建立批次库存余额"),
            Map.entry("create_inspection_task", "创建质量检验任务"),
            Map.entry("assign_primary_inspector", "分派主检员"),
            Map.entry("start_inspection", "开始质量检验"),
            Map.entry("record_failed_inspection", "记录不合格检验结论"),
            Map.entry("request_independent_recheck", "发起独立复检"),
            Map.entry("confirm_failed_recheck", "确认复检不合格"),
            Map.entry("complete_inspection", "完成质量检验"),
            Map.entry("open_capa", "制定纠正预防措施"),
            Map.entry("verify_capa", "验证纠正预防措施"),
            Map.entry("open_batch_recall", "启动批次召回与库存隔离"),
            Map.entry("acknowledge_batch_recall", "确认召回处置"),
            Map.entry("resolve_batch_recall", "完成召回处置"),
            Map.entry("verify_quality_recall_closed", "验收质量召回终态"),
            Map.entry("merchant_draft", "创建商家草稿"),
            Map.entry("merchant_submit", "提交商家审核"),
            Map.entry("merchant_review", "完成商家审核"),
            Map.entry("merchant_approve", "批准商家入驻"),
            Map.entry("merchant_terminal_readback", "验收商家与店铺经营终态"),
            Map.entry("campaign_activate", "启用促销触达活动"),
            Map.entry("delivery_queue", "创建用户触达任务"),
            Map.entry("provider_send", "执行渠道发送"),
            Map.entry("delivery_receipt", "记录用户送达"),
            Map.entry("open_receipt", "记录用户打开"),
            Map.entry("click_receipt", "记录用户点击"),
            Map.entry("campaign_complete", "完成促销活动"),
            Map.entry("campaign_terminal_readback", "验收活动投放终态"),
            Map.entry("growth_campaign_create", "创建增长实验活动"),
            Map.entry("growth_campaign_activate", "启用增长实验活动"),
            Map.entry("experiment_create", "设计增长实验"),
            Map.entry("experiment_start", "启动实验分流"),
            Map.entry("control_exposure", "记录对照组曝光"),
            Map.entry("treatment_exposure", "记录实验组曝光"),
            Map.entry("control_metric", "计算对照组指标"),
            Map.entry("treatment_metric", "计算实验组指标"),
            Map.entry("experiment_conclude", "形成实验决策"),
            Map.entry("experiment_terminal_readback", "验收增长实验终态"),
            Map.entry("supplier_a_register", "登记候选供应商 A"),
            Map.entry("supplier_a_submit", "提交供应商 A 准入"),
            Map.entry("supplier_a_approve", "批准供应商 A 准入"),
            Map.entry("supplier_b_register", "登记候选供应商 B"),
            Map.entry("supplier_b_submit", "提交供应商 B 准入"),
            Map.entry("supplier_b_approve", "批准供应商 B 准入"),
            Map.entry("rfq_create", "创建采购询价"),
            Map.entry("quote_a_submit", "收集供应商 A 报价"),
            Map.entry("quote_b_submit", "收集供应商 B 报价"),
            Map.entry("sample_a_evaluate", "评估供应商 A 样品"),
            Map.entry("sample_b_evaluate", "评估供应商 B 样品"),
            Map.entry("supplier_award", "比较并定标供应商"),
            Map.entry("sourcing_terminal_readback", "验收寻源定标终态"),
            Map.entry("purchase_order_create", "创建采购订单"),
            Map.entry("purchase_order_dispatch", "审批并下发采购订单"),
            Map.entry("supplier_confirm_order", "供应商确认采购订单"),
            Map.entry("procurement_terminal_readback", "验收采购订单终态"),
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
            Map.entry("warehouse_source", "核验 ERP 仓库来源"),
            Map.entry("inspect_daily_business_control", "回读日经营控制结果"),
            Map.entry("inspect_weekly_business_review", "回读周经营复盘结果"),
            Map.entry("inspect_merchant_onboarding", "回读商家入驻终态"),
            Map.entry("inspect_promotion_campaign", "回读促销活动终态"),
            Map.entry("inspect_growth_experiment", "回读增长实验终态"),
            Map.entry("start_order_cancellation", "启动订单取消补偿"),
            Map.entry("wait_order_cancellation", "等待订单取消补偿终态"),
            Map.entry("inspect_order_to_cash", "回读订单到回款终态"),
            Map.entry("inspect_order_cancellation", "回读订单取消终态"),
            Map.entry("inspect_fulfillment_exception", "回读履约异常终态"),
            Map.entry("inspect_return_refund", "回读退货退款终态"),
            Map.entry("inspect_customer_resolution", "回读客户问题解决终态"),
            Map.entry("inspect_quality_recall", "回读质量召回终态"),
            Map.entry("inspect_risk_dispute", "回读风险争议终态"),
            Map.entry("inspect_payment_reconciliation", "回读支付对账终态"),
            Map.entry("inspect_supplier_confirmation", "回读供应商采购确认"),
            Map.entry("inspect_supplier_sourcing_decision", "回读供应商寻源定标终态"),
            Map.entry("inspect_finance_close", "回读财务关账终态"),
            Map.entry("inspect_allocation_transfer", "回读库存调拨终态"),
            Map.entry("inspect_warehouse_inbound", "回读仓库入库终态")
            ,Map.entry("create_wms_merchant", "创建每日仓储商家")
            ,Map.entry("create_source_warehouse", "创建收货源仓")
            ,Map.entry("create_target_warehouse", "创建调拨目标仓")
            ,Map.entry("create_item_category", "创建仓储商品分类")
            ,Map.entry("create_item", "创建每日仓储商品")
            ,Map.entry("resolve_sku", "解析仓储 SKU")
            ,Map.entry("create_receipt", "创建采购收货单")
            ,Map.entry("complete_receipt", "完成采购收货")
            ,Map.entry("verify_receipt", "核验收货单终态")
            ,Map.entry("create_movement", "创建仓间调拨单")
            ,Map.entry("complete_movement", "完成仓间调拨")
            ,Map.entry("verify_movement", "核验调拨终态")
            ,Map.entry("create_shipment", "创建销售出库单")
            ,Map.entry("complete_shipment", "完成销售出库")
            ,Map.entry("verify_shipment", "核验出库终态")
            ,Map.entry("create_inventory_check", "创建库存盘点单")
            ,Map.entry("complete_inventory_check", "完成库存盘点")
            ,Map.entry("verify_inventory_check", "核验盘点终态")
            ,Map.entry("verify_final_source_inventory", "核验源仓最终库存")
            ,Map.entry("verify_final_target_inventory", "核验目标仓最终库存")
            ,Map.entry("submit_supplier_sourcing", "发起补货供应商寻源")
            ,Map.entry("wait_supplier_sourcing", "等待供应商定标完成")
            ,Map.entry("submit_purchase_order", "发起补货采购订单")
            ,Map.entry("wait_purchase_order", "等待采购订单确认")
            ,Map.entry("submit_physical_warehouse_cycle", "发起补货仓储作业")
            ,Map.entry("wait_physical_warehouse_cycle", "等待仓储收发调盘闭环")
            ,Map.entry("ticket_receive_message", "接收用户咨询消息")
            ,Map.entry("ticket_assign_agent", "分派客服坐席")
            ,Map.entry("ticket_start_processing", "开始处理咨询")
            ,Map.entry("ticket_collect_feedback", "收集用户满意度反馈")
            ,Map.entry("ticket_terminal_readback", "核验客服工单已关闭")
    );

    private final ObjectMapper objectMapper;

    public ManagedSkillTaskBusinessOutcomeView present(Task task, List<Step> steps) {
        if ("skill.cloudmold.commerce.order-cancellation-operational.v1".equals(task.getSkillId())) {
            return orderCancellationOperational(task, steps);
        }
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
            case "skill.cloudmold.fulfillment.exception-resolution-lifecycle.v1" ->
                    fulfillmentExceptionLifecycle(task, steps);
            case "skill.cloudmold.crossborder.fulfillment-compliance-lifecycle.v1" ->
                    crossborderFulfillmentComplianceLifecycle(task, steps);
            case "skill.cloudmold.crossborder.bonded-customs-lifecycle.v1" ->
                    bondedCustomsLifecycle(task, steps);
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
        if (text(result, "workflowType") != null && text(result, "summary") != null) {
            return text(result, "summary");
        }
        if (text(result, "sagaId") != null) {
            return "取消补偿 Saga " + text(result, "sagaId") + "："
                    + valueOr(statusLabel(valueOr(text(result, "activeStep"), text(result, "status"))), "已受理");
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
        addObject(objects, seen, "CANCELLATION_SAGA", "取消补偿 Saga",
                text(result, "sagaId"), text(result, "sagaId"),
                text(result, "status"));
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
        appendWorkflowArtifacts(objects, seen, result.path("artifacts"));
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

    private ManagedSkillTaskBusinessOutcomeView fulfillmentExceptionLifecycle(
            Task task, List<Step> steps) {
        JsonNode transit = result(steps, "wait_in_transit_order");
        JsonNode order = transit.at("/outputs/order_place");
        JsonNode fulfillment = transit.at("/outputs/fulfillment_create");
        JsonNode closed = result(steps, "verify_exception_closed");
        JsonNode completedOrder = result(steps, "complete_recovered_order");
        String exceptionNo = valueOr(text(closed, "exceptionNo"), text(closed, "exceptionId"));
        return outcome("FULFILLMENT_EXCEPTION_RESOLUTION",
                "履约异常 " + valueOr(exceptionNo, "处置单") + " 已恢复交付并关单",
                "物流经理已完成异常识别、影响诊断、审批、承运处置、恢复交付和订单完结；"
                        + "异常、订单与履约终态均已留存证据。",
                List.of(
                        metric("异常类型", statusLabel(text(closed, "exceptionType"))),
                        metric("处置动作", statusLabel(text(closed, "action"))),
                        metric("异常状态", "CLOSED".equals(text(closed, "status"))
                                ? "已关闭" : statusLabel(text(closed, "status"))),
                        metric("订单状态", statusLabel(text(completedOrder, "currentStatus")))),
                List.of(
                        object("FULFILLMENT_EXCEPTION", "履约异常",
                                text(closed, "exceptionId"), exceptionNo, text(closed, "status")),
                        object("ORDER", "恢复订单",
                                text(order, "orderId"), text(order, "orderNo"),
                                text(completedOrder, "currentStatus")),
                        object("FULFILLMENT", "恢复履约单",
                                text(fulfillment, "fulfillmentId"),
                                text(fulfillment, "fulfillmentNo"), "DELIVERED")),
                task);
    }

    private ManagedSkillTaskBusinessOutcomeView crossborderFulfillmentComplianceLifecycle(
            Task task, List<Step> steps) {
        JsonNode transit = result(steps, "wait_paid_in_transit_order");
        JsonNode order = transit.at("/outputs/order_place");
        JsonNode fulfillment = transit.at("/outputs/fulfillment_create");
        JsonNode closed = result(steps, "verify_crossborder_case_closed");
        String caseNo = valueOr(text(closed, "caseNo"), text(closed, "caseId"));
        return outcome("CROSSBORDER_FULFILLMENT_COMPLIANCE",
                "跨境履约案件 " + valueOr(caseNo, "目标案件") + " 已清关、妥投并关单",
                "跨境运营已完成有边界的合规评估、AI 路线推荐、风险与法务会签、申报资料、"
                        + "三单校验、国际承运、海关放行和末端妥投；当前证据属于 CN→US 受控测试场景。",
                List.of(
                        metric("贸易模式", statusLabel(text(closed, "tradeMode"))),
                        metric("运输路线", valueOr(text(closed.path("route"), "routeCode"), "-")),
                        metric("海关状态", "RELEASED".equals(text(closed, "customsStatus"))
                                ? "已放行" : statusLabel(text(closed, "customsStatus"))),
                        metric("配送状态", "DELIVERED".equals(text(closed, "deliveryStatus"))
                                ? "已送达" : statusLabel(text(closed, "deliveryStatus"))),
                        metric("案件状态", "CLOSED".equals(text(closed, "status"))
                                ? "已关闭" : statusLabel(text(closed, "status")))),
                List.of(
                        object("CROSSBORDER_CASE", "跨境履约案件",
                                text(closed, "caseId"), caseNo, text(closed, "status")),
                        object("ORDER", "跨境订单",
                                text(order, "orderId"), text(order, "orderNo"), "IN_TRANSIT"),
                        object("FULFILLMENT", "跨境履约单",
                                text(fulfillment, "fulfillmentId"),
                                text(fulfillment, "fulfillmentNo"), "IN_TRANSIT")),
                task);
    }

    private ManagedSkillTaskBusinessOutcomeView bondedCustomsLifecycle(
            Task task, List<Step> steps) {
        JsonNode transit = result(steps, "wait_paid_in_transit_order");
        JsonNode order = transit.at("/outputs/order_place");
        JsonNode closed = result(steps, "verify_bonded_case_closed");
        String caseNo = valueOr(text(closed, "caseNo"), text(closed, "caseId"));
        JsonNode classification = closed.path("goodsClassification");
        JsonNode tax = closed.path("taxCalculation");
        return outcome("BONDED_CUSTOMS_LIFECYCLE",
                "保税关务案件 " + valueOr(caseNo, "目标案件") + " 已对碰、放行、妥投并关单",
                "保税仓关务已完成有边界的准入评估、商品归类、三单对碰、测试税费计算、"
                        + "风险与法务会签、申报受理、保税放行和境内妥投；当前证据属于受控测试场景。",
                List.of(
                        metric("三单状态", "TRIPLE_MATCHED".equals(text(closed, "tripleMatchStatus"))
                                ? "已对碰" : statusLabel(text(closed, "tripleMatchStatus"))),
                        metric("商品归类", valueOr(text(classification, "hsCode"), "-")),
                        metric("测试税费",
                                valueOr(text(tax, "currency"), "CNY") + " "
                                        + valueOr(text(tax, "totalTaxMinor"), "0") + " 分"),
                        metric("海关状态", "CUSTOMS_ACCEPTED".equals(text(closed, "customsStatus"))
                                ? "已受理" : statusLabel(text(closed, "customsStatus"))),
                        metric("保税放行", "BONDED_RELEASED".equals(text(closed, "bondedReleaseStatus"))
                                ? "已放行" : statusLabel(text(closed, "bondedReleaseStatus"))),
                        metric("配送状态", "DELIVERED".equals(text(closed, "deliveryStatus"))
                                ? "已送达" : statusLabel(text(closed, "deliveryStatus"))),
                        metric("案件状态", "CLOSED".equals(text(closed, "status"))
                                ? "已关闭" : statusLabel(text(closed, "status")))),
                List.of(
                        object("BONDED_CUSTOMS_CASE", "保税关务案件",
                                text(closed, "caseId"), caseNo, text(closed, "status")),
                        object("ORDER", "保税进口订单",
                                text(order, "orderId"), text(order, "orderNo"), "IN_TRANSIT")),
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

    private ManagedSkillTaskBusinessOutcomeView orderCancellationOperational(Task task, List<Step> steps) {
        JsonNode start = result(steps, "start_order_cancellation");
        JsonNode wait = result(steps, "wait_order_cancellation");
        boolean paidUnshipped = "PAID_UNSHIPPED".equals(text(start, "cancellationMode"));
        String orderCode = valueOr(text(start, "orderNo"), text(start, "orderId"));
        String headline = switch (task.getStatus()) {
            case "SUCCEEDED" -> "订单 " + valueOr(orderCode, "目标订单") + " 已完成取消补偿闭环";
            case "NEEDS_REVIEW" -> "订单 " + valueOr(orderCode, "目标订单") + " 进入取消补偿人工复核";
            default -> "订单 " + valueOr(orderCode, "目标订单") + " 正在执行取消补偿";
        };
        String summary = valueOr(text(wait, "summary"),
                valueOr(text(start, "reason"), "订单取消补偿已提交并由规范域持续回读。"));
        if ("NEEDS_REVIEW".equals(task.getStatus())) {
            String blocker = firstArrayText(wait.path("blockers"));
            if (blocker != null) {
                summary = summary + " 阻塞项：" + blocker + "。";
            }
        }
        if (paidUnshipped) {
            summary = summary + " 当前自动闭环仅覆盖规范域与 INTERNAL_TEST 支付退款；真实 PSP 退款回执仍需外部权威。";
        }
        List<ManagedSkillTaskOutcomeMetricView> metrics = new ArrayList<>();
        metrics.add(metric("取消模式", paidUnshipped ? "已支付未发货" : "未支付已预占"));
        metrics.add(metric("业务状态", statusLabel(valueOr(text(wait, "status"), task.getStatus()))));
        metrics.add(metric("当前阶段", valueOr(text(wait, "phase"), valueOr(text(start, "activeStep"), "-"))));
        if (start.hasNonNull("expectedReservationCount")) {
            metrics.add(metric("预占释放",
                    start.path("releasedReservationCount").asText("0")
                            + "/" + start.path("expectedReservationCount").asText("0")));
        }
        if (start.hasNonNull("expectedFulfillmentCount")
                && start.path("expectedFulfillmentCount").asInt(0) > 0) {
            metrics.add(metric("履约关闭",
                    start.path("cancelledFulfillmentCount").asText("0")
                            + "/" + start.path("expectedFulfillmentCount").asText("0")));
        }
        if (StringUtils.hasText(text(start, "paymentStatus"))) {
            metrics.add(metric("支付状态", statusLabel(text(start, "paymentStatus"))));
        }
        return outcome("ORDER_CANCELLATION",
                headline,
                summary,
                metrics,
                orderCancellationObjects(start, wait),
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
            case "NEEDS_REVIEW" -> name + "需人工复核";
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

    private static void appendWorkflowArtifacts(List<ManagedSkillTaskOutcomeObjectView> objects, Set<String> seen,
                                                JsonNode artifacts) {
        if (artifacts == null || !artifacts.isArray()) {
            return;
        }
        artifacts.forEach(artifact -> addObject(objects, seen,
                text(artifact, "type"),
                valueOr(text(artifact, "label"), objectTypeLabel(text(artifact, "type"))),
                text(artifact, "id"),
                null,
                text(artifact, "status")));
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

    private static String firstArrayText(JsonNode node) {
        if (node != null && node.isArray() && !node.isEmpty()) {
            String value = node.get(0).asText();
            return StringUtils.hasText(value) ? value : null;
        }
        return null;
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

    private static List<ManagedSkillTaskOutcomeObjectView> orderCancellationObjects(JsonNode start, JsonNode wait) {
        List<ManagedSkillTaskOutcomeObjectView> objects = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addObject(objects, seen, "CANCELLATION_SAGA", "取消补偿 Saga",
                text(start, "sagaId"), text(start, "sagaId"),
                valueOr(text(wait, "status"), text(start, "status")));
        addObject(objects, seen, "ORDER", "订单",
                text(start, "orderId"), text(start, "orderNo"),
                valueOr(text(wait, "status"), text(start, "orderStatusAtRequest")));
        addObject(objects, seen, "PAYMENT", "支付单",
                text(start, "paymentId"), text(start, "paymentId"),
                text(start, "paymentStatus"));
        addObject(objects, seen, "FULFILLMENT", "履约单",
                text(start, "fulfillmentId"), text(start, "fulfillmentId"),
                text(start, "fulfillmentStatus"));
        appendWorkflowArtifacts(objects, seen, wait.path("artifacts"));
        return objects;
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
            case "ORDER" -> "订单";
            case "PAYMENT" -> "支付单";
            case "FULFILLMENT" -> "履约单";
            case "AFTERSALE" -> "售后单";
            case "CANCELLATION_SAGA" -> "取消补偿 Saga";
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
            case "CAPTURED" -> "已扣款";
            case "CANCELLED" -> "已取消";
            case "REQUESTED" -> "已受理";
            case "RETRY_SCHEDULED" -> "等待重试";
            case "CANCELLATION_PENDING" -> "取消处理中";
            case "APPROVED", "ACCEPTED" -> "已通过";
            case "READY" -> "已就绪";
            case "RUNNING" -> "运行中";
            case "WAITING" -> "等待中";
            case "WAITING_APPROVAL" -> "等待审批";
            case "NEEDS_REVIEW", "MANUAL_REVIEW" -> "需人工复核";
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
