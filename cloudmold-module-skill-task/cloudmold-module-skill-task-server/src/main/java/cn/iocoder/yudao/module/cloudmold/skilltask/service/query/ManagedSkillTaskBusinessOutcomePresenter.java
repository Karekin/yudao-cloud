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
            Map.entry("skill.cloudmold.catalog.assortment-planning-lifecycle.v1", "选品与波段企划闭环"),
            Map.entry("skill.cloudmold.inventory.stockout-diagnosis.v1", "尺码缺断码诊断"),
            Map.entry("skill.cloudmold.listing.lifecycle-readback.v1", "商品刊登生命周期终态跟踪"),
            Map.entry("skill.cloudmold.commerce.catalog-matrix.v1", "商品款色码建档"),
            Map.entry("skill.cloudmold.commerce.product-to-listing.v1", "自动铺品"),
            Map.entry("skill.cloudmold.commerce.autonomous-day.v1", "AI 自主经营日"),
            Map.entry("skill.cloudmold.commerce.category-daily-operations.v1", "品类日常运营闭环"),
            Map.entry("skill.cloudmold.commerce.product-management-lifecycle.v1", "商品管理主流程"),
            Map.entry("skill.cloudmold.customer-experience.ticket-responsibility-lifecycle.v1",
                    "消费者体验工单判责"),
            Map.entry("skill.cloudmold.customer-experience.unfulfillable-order-compensation-lifecycle.v1",
                    "无法履约订单主动赔付"),
            Map.entry("skill.cloudmold.consumer.shopping-journey.v1", "消费者选购与服务全旅程"),
            Map.entry("skill.cloudmold.commerce.aftersale-saga.v1", "跨境退货质检处置闭环"),
            Map.entry("skill.cloudmold.commerce.legacy-projection-plan.v1", "旧系统投影预检"),
            Map.entry("skill.cloudmold.commerce.reuse-ready-master.v1", "商家与仓网主数据准备"),
            Map.entry("skill.cloudmold.commerce.terminal-readback.v1", "全链路终态核验"),
            Map.entry("skill.cloudmold.commerce.full-chain-hsf.v1", "商品售后自治全链路"),
            Map.entry("skill.cloudmold.supply-planning.prepare.v1", "补货单准备"),
            Map.entry("skill.cloudmold.operations.daily-business-control.v1", "日经营控制"),
            Map.entry("skill.cloudmold.operations.weekly-business-review.v1", "周经营复盘"),
            Map.entry("skill.cloudmold.merchant.onboarding-readback.v1", "商家入驻终态跟踪"),
            Map.entry("skill.cloudmold.merchant.onboarding-lifecycle.v1", "商家入驻经营闭环"),
            Map.entry("skill.cloudmold.merchant-experience.rectification-lifecycle.v1",
                    "商家触发整改与体验恢复"),
            Map.entry("skill.cloudmold.engagement.promotion-campaign-operations.v1", "促销活动投放闭环"),
            Map.entry("skill.cloudmold.growth.experiment-lifecycle.v1", "增长实验决策闭环"),
            Map.entry("skill.cloudmold.procurement.sourcing-lifecycle.v1", "采购寻源定标闭环"),
            Map.entry("skill.cloudmold.procurement.order-lifecycle.v1", "采购订单履约闭环"),
            Map.entry("skill.cloudmold.wms.operations.v1", "仓储收发调盘闭环"),
            Map.entry("skill.cloudmold.supply.replenishment-lifecycle.v1", "智能补货执行闭环"),
            Map.entry("skill.cloudmold.supply.warehouse-admission-lifecycle.v1", "入仓决策主流程"),
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
            Map.entry("skill.cloudmold.partner-marketing.kol-media-operations.v1",
                    "海外 KOL 与媒体合作投放闭环"),
            Map.entry("skill.cloudmold.mes.production-readiness.v1", "MES 新品试产准备"),
            Map.entry("skill.cloudmold.mes.production-execution-lifecycle.v1",
                    "新品试产与量产交付闭环"),
            Map.entry("skill.cloudmold.consumer.in-transit-order-scenario.v1", "在途订单场景准备"),
            Map.entry("skill.cloudmold.consumer.paid-unshipped-order-scenario.v1",
                    "已支付未发货订单场景准备"),
            Map.entry("skill.cloudmold.commerce.return-refund-readback.v1", "退货退款终态跟踪"),
            Map.entry("skill.cloudmold.customer-service.resolution-readback.v1", "客户问题解决终态跟踪"),
            Map.entry("skill.cloudmold.quality.recall-readback.v1", "质量召回终态跟踪"),
            Map.entry("skill.cloudmold.quality.inspection-recall-lifecycle.v1", "质量检验与召回闭环"),
            Map.entry("skill.cloudmold.risk.dispute-readback.v1", "风险争议终态跟踪"),
            Map.entry("skill.cloudmold.risk.dispute-resolution-lifecycle.v1", "风险争议与损失处置闭环"),
            Map.entry("skill.cloudmold.data-ai-operations.data-quality-recovery-lifecycle.v1", "数据质量恢复闭环"),
            Map.entry("skill.cloudmold.payment.reconciliation-readback.v1", "支付对账终态跟踪"),
            Map.entry("skill.cloudmold.procurement.supplier-confirmation-readback.v1", "供应商采购确认跟踪"),
            Map.entry("skill.cloudmold.procurement.sourcing-decision-readback.v1", "采购寻源定标终态跟踪"),
            Map.entry("skill.cloudmold.finance.close-readiness.v1", "财务关账准备度跟踪"),
            Map.entry("skill.cloudmold.finance.close-lifecycle.v1", "财务结算关账闭环"),
            Map.entry("skill.cloudmold.finance.logistics-service-settlement-lifecycle.v1",
                    "物流服务商仓发提结算"),
            Map.entry("skill.cloudmold.finance.merchant-service-fee-settlement-lifecycle.v1",
                    "商家技术服务费结算"),
            Map.entry("skill.cloudmold.finance.advertising-fee-settlement-lifecycle.v1",
                    "平台广告费结算"),
            Map.entry("skill.cloudmold.finance.profit-loss-improvement-lifecycle.v1",
                    "问题订单与退货损益改善"),
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
            Map.entry("skill.cloudmold.catalog.assortment-planning-lifecycle.v1",
                    "模拟选品经理从趋势信号与候选发现开始，完成 AI 多因子评分、毛利/退货率/价格带约束选款、独立会签、上新日历发布和商品开发交接；每天生成新的波段与候选池。"),
            Map.entry("skill.cloudmold.inventory.stockout-diagnosis.v1",
                    "按 SPU 检查各尺码可售库存，识别缺货与低库存风险，不修改业务数据。"),
            Map.entry("skill.cloudmold.listing.lifecycle-readback.v1",
                    "持续核验刊登发布、渠道回执、暂停、下架、归档及销售资格失效后的批量下架结果。"),
            Map.entry("skill.cloudmold.commerce.catalog-matrix.v1",
                    "建立款式、SPU 与 6 个 SKU，并完成商品生命周期激活。"),
            Map.entry("skill.cloudmold.commerce.product-to-listing.v1",
                    "串联规范商品建档、商家店铺准备、商品刊登审核发布与终态回读，缺少渠道回执时明确标记待渠道确认。"),
            Map.entry("skill.cloudmold.commerce.autonomous-day.v1",
                    "模拟综合运营控制岗位创建、通知并认领当日经营行动单，生成全新商品与刊登，"
                            + "再由独立消费者完成选购、支付履约、售后、客服和社区种草；"
                            + "仅在两条业务链全部成功后关闭经营行动单。"),
            Map.entry("skill.cloudmold.commerce.category-daily-operations.v1",
                    "模拟品类运营创建并认领每日行动单，完成新品铺货、转化实验、目标活动触达和真实消费者选购验证；"
                            + "每日轮换低转化、高浏览低加购、活动缺口和内容质量问题，仅在四条业务链全部成功后关闭行动单。"),
            Map.entry("skill.cloudmold.commerce.product-management-lifecycle.v1",
                    "模拟商品运营完成全平台供给池选品、平台大店铺品、新品企划与培育、神秘买手实购，"
                            + "以及审版、核价、素材拍摄、模特试穿和消费者质量标验收；"
                            + "质量未达到 VERIFIED 时不会关闭行动单或交接入仓。"),
            Map.entry("skill.cloudmold.customer-experience.ticket-responsibility-lifecycle.v1",
                    "模拟消费者体验运营为全新订单建立投诉工单，关联规范订单证据，完成受理调查、"
                            + "商家责任判定、服务补偿支付、工单关单和行动单终态验收；"
                            + "不冒充外部支付仲裁或平台处罚权威。"),
            Map.entry("skill.cloudmold.customer-experience.unfulfillable-order-compensation-lifecycle.v1",
                    "已支付未发货订单无法履约时，先由订单取消 Saga 完成发货拦截、全额退款、库存释放与取消，"
                            + "再由客服权威完成平台责任判定、服务赔付、消费者通知与关单。"),
            Map.entry("skill.cloudmold.consumer.shopping-journey.v1",
                    "模拟真实会员完成搜索、商详、收藏、加购、结算、下单支付、履约、售后、咨询与社区发布。"),
            Map.entry("skill.cloudmold.commerce.aftersale-saga.v1",
                    "模拟跨境退货运营岗位完成全新订单、退货收仓、AI 成色与风险评估、仓库独立复检、"
                            + "可返售复库或不可售报废处置、客户退款和订单关闭；每日轮换返售与报废场景。"),
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
            Map.entry("skill.cloudmold.merchant-experience.rectification-lifecycle.v1",
                    "仅在上一条工单已有商家责任复核证据、赔付已支付且工单已关闭时触发；"
                            + "校验责任商家、重开原工单、回传整改进度、二次解决、复核、回访并重新关单。"),
            Map.entry("skill.cloudmold.engagement.promotion-campaign-operations.v1",
                    "模拟活动运营完成活动启用、人群触达、渠道发送、送达、打开、点击、活动收尾与终态验收；每天生成全新活动。"),
            Map.entry("skill.cloudmold.growth.experiment-lifecycle.v1",
                    "模拟增长运营完成实验活动、分组、曝光、指标快照、护栏判断、显著性结论与终态验收；每天生成全新实验。"),
            Map.entry("skill.cloudmold.procurement.sourcing-lifecycle.v1",
                    "基于已筛选的规范供应商候选完成多行、多交期采购申请、寻源发布、双供应商报价、独立多维评分、定标审批与事件关闭。"),
            Map.entry("skill.cloudmold.procurement.order-lifecycle.v1",
                    "基于最近一次真实定标结果创建采购订单，完成审批下发、供应商确认与终态验收；每天生成全新采购订单。"),
            Map.entry("skill.cloudmold.wms.operations.v1",
                    "模拟仓储运营完成双仓与商品造数、采购收货、仓间调拨、销售出库、零差异盘点和最终库存验收；每天生成全新业务单据。"),
            Map.entry("skill.cloudmold.supply.replenishment-lifecycle.v1",
                    "模拟补货运营创建并认领日常或大促补货行动单，完成需求分型、双供应商寻源定标、"
                            + "采购订单下发以及收货、调拨、出库、盘点和库存验收；"
                            + "仅在三条业务链全部成功后关闭行动单。"),
            Map.entry("skill.cloudmold.supply.warehouse-admission-lifecycle.v1",
                    "模拟供应链小二先核验买样质检 VERIFIED，再完成 BD 商家入驻、质检通过 SKU 的采购补货、"
                            + "平台仓收发调盘与履约验收，并以站内活动承接质量流量；"
                            + "当前不冒充外部广告平台预算、竞价或结算权威。"),
            Map.entry("skill.cloudmold.supply-planning.sop-lifecycle.v1",
                    "模拟需求计划经理完成预测发布与偏差评估、精益和韧性双场景测算、AI 鲁棒方案推荐、会签发布、补货批准，并转换为真实 WMS 调拨草稿。"),
            Map.entry("skill.cloudmold.customer-service.resolution-lifecycle.v1",
                    "模拟用户咨询与客服岗位完成建单、关联订单、消息接收、分派、处理、解决、满意度反馈、关单和终态验收；每天产生新的客服案例。"),
            Map.entry("skill.cloudmold.engagement.promotion-campaign-readback.v1",
                    "持续回读活动与投放结果；依赖数据未齐备时保持等待并展示阻塞项。"),
            Map.entry("skill.cloudmold.engagement.growth-experiment-readback.v1",
                    "持续回读增长实验结果；样本或归因未齐备时保持等待并展示阻塞项。"),
            Map.entry("skill.cloudmold.commerce.order-cancellation-operational.v1",
                    "模拟订单异常运营为每天新建的已支付未发货订单完成开案、通知、认领、履约关闭、"
                            + "退款、库存释放、订单取消、结案和双终态验收；"
                            + "当前支付退款使用 INTERNAL_TEST 权威，真实 PSP 仍由外部系统负责。"),
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
            Map.entry("skill.cloudmold.partner-marketing.kol-media-operations.v1",
                    "模拟海外 KOL 与媒体合作岗位完成候选风险筛选、合作 brief 和披露合规审批、受控外部发布、"
                            + "真实消费者选购支付归因、交付验收与结算闭环；外部平台发布和付款使用测试证据，不冒充真实平台或银行权威。"),
            Map.entry("skill.cloudmold.mes.production-readiness.v1",
                    "内部造数子流程：为单次新品试产建立 occurrence 唯一的 MES 产品、车间、工作站、关键工序与启用工艺路线。"),
            Map.entry("skill.cloudmold.mes.production-execution-lifecycle.v1",
                    "模拟生产主管完成 occurrence 全新的新品产线准备、生产工单确认、任务派工、合格报工审核、"
                            + "产成品入库、工单完工与数量终态核验；仅工单、任务、报工和入库单全部闭环才成功。"),
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
            Map.entry("skill.cloudmold.risk.dispute-resolution-lifecycle.v1",
                    "模拟风险运营从全新已支付订单识别拒付，完成风险聚类、人工复核、争议终态、"
                            + "损失台账与行动单闭环；所有资金损失均以 Risk 权威与人工决策为准。"),
            Map.entry("skill.cloudmold.data-ai-operations.data-quality-recovery-lifecycle.v1",
                    "模拟数据与 AI 运营为全新数据源、数据集和指标链路记录质量异常，保留失败 DQC 证据，"
                            + "完成受控重跑、PASS 复核与行动单终态验收。"),
            Map.entry("skill.cloudmold.payment.reconciliation-readback.v1",
                    "持续核验订单与支付对账结果，账实未一致时保持等待并展示阻塞项。"),
            Map.entry("skill.cloudmold.procurement.supplier-confirmation-readback.v1",
                    "从真实采购单创建事件持续核验供应商确认状态；仅覆盖采购确认，不冒充 RFQ、比价、样品或供应商准入。"),
            Map.entry("skill.cloudmold.procurement.sourcing-decision-readback.v1",
                    "持续核验 Procurement 已批准定标及其规范快照；仅 APPROVED 终态视为成功。"),
            Map.entry("skill.cloudmold.finance.close-readiness.v1",
                    "持续核验渠道账单、对账差异、结算批次和已过账凭证；仅规范会计期间 CLOSED 终态视为成功。"),
            Map.entry("skill.cloudmold.finance.close-lifecycle.v1",
                    "模拟财务结算岗位以制单、复核双身份完成期间开启、渠道账单导入、差异调整、结算确认、凭证制备与过账、期间关闭及终态验收；每天生成全新账期。"),
            Map.entry("skill.cloudmold.finance.logistics-service-settlement-lifecycle.v1",
                    "按仓储、发货和提货服务报价导入物流账单，复用财务结算权威完成差异调整、结算、凭证与关账。"),
            Map.entry("skill.cloudmold.finance.merchant-service-fee-settlement-lifecycle.v1",
                    "按商家销售、退款与技术服务费计算净结算金额，复用财务权威完成独立复核、结算、凭证和关账。"),
            Map.entry("skill.cloudmold.finance.advertising-fee-settlement-lifecycle.v1",
                    "对平台广告账单、退款或返还和净应付金额完成对账，复用财务权威完成结算确认、凭证过账与关账。"),
            Map.entry("skill.cloudmold.finance.profit-loss-improvement-lifecycle.v1",
                    "读取周经营 KPI 快照建立损益改善行动单，重点治理问题订单、退货损失、确收金额与贡献利润；缺失指标不会被虚构。"),
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
            "readback", "skill.cloudmold.commerce.terminal-readback.v1",
            "production_readiness", "skill.cloudmold.mes.production-readiness.v1"
    );

    private static final Map<String, String> STEP_NAMES = Map.ofEntries(
            Map.entry("get-active-sku", "查询在售 SKU"),
            Map.entry("create_wave", "创建新品波段企划"),
            Map.entry("add_entry_candidate", "建立入门价格带候选"),
            Map.entry("evaluate_entry_candidate", "AI 评估入门候选"),
            Map.entry("add_core_candidate", "建立核心价格带候选"),
            Map.entry("evaluate_core_candidate", "AI 评估核心候选"),
            Map.entry("add_premium_candidate", "建立高端价格带候选"),
            Map.entry("evaluate_premium_candidate", "AI 评估高端候选"),
            Map.entry("select_portfolio", "按经营约束选择商品组合"),
            Map.entry("approve_wave", "独立会签波段组合"),
            Map.entry("publish_wave", "发布上新日历并交接商品开发"),
            Map.entry("verify_wave_published", "验收波段企划发布终态"),
            Map.entry("open_product_management_case", "创建商品管理行动单"),
            Map.entry("notice_product_management_case", "通知商品管理责任人"),
            Map.entry("claim_product_management_case", "认领商品管理行动单"),
            Map.entry("submit_supply_pool_assortment", "启动全平台供给池选品"),
            Map.entry("wait_supply_pool_assortment", "等待选品与波段企划完成"),
            Map.entry("submit_platform_store_listing", "启动平台大店铺品"),
            Map.entry("wait_platform_store_listing", "等待商品刊登发布"),
            Map.entry("submit_mystery_sample_purchase", "启动神秘买手实购"),
            Map.entry("wait_mystery_sample_purchase", "等待买样订单与履约完成"),
            Map.entry("submit_mystery_buyer_quality", "启动买样多维质检"),
            Map.entry("wait_mystery_buyer_quality", "等待质量标验收"),
            Map.entry("submit_new_product_incubation", "启动新品企划与爆款培育"),
            Map.entry("wait_new_product_incubation", "等待新品活动验收"),
            Map.entry("resolve_product_management_case", "关闭商品管理行动单"),
            Map.entry("verify_product_management_resolved", "验收商品管理闭环终态"),
            Map.entry("link_mystery_buyer_inspector", "核验神秘买手质检员身份"),
            Map.entry("create_mystery_buyer_standard", "建立审版核价与试穿标准"),
            Map.entry("publish_mystery_buyer_standard", "发布买样质检标准"),
            Map.entry("certify_mystery_buyer_inspector", "认证买样质检员"),
            Map.entry("register_purchased_sample_lot", "登记实购买样批次"),
            Map.entry("receive_purchased_sample", "接收非卖品质检样品"),
            Map.entry("create_multidimensional_inspection", "创建审版核价拍摄试穿任务"),
            Map.entry("assign_mystery_buyer_inspector", "分派买样质检任务"),
            Map.entry("start_mystery_buyer_inspection", "开始买样质检"),
            Map.entry("record_pattern_price_content_fitting_pass", "记录审版、核价、拍摄与试穿通过"),
            Map.entry("complete_mystery_buyer_inspection", "完成买样质检"),
            Map.entry("verify_quality_badge", "验收消费者质量标证据"),
            Map.entry("open_warehouse_admission_case", "创建入仓决策行动单"),
            Map.entry("notice_warehouse_admission_case", "通知供应链小二"),
            Map.entry("claim_warehouse_admission_case", "认领入仓决策行动单"),
            Map.entry("verify_quality_first_admission_gate", "核验质检优先入仓门槛"),
            Map.entry("submit_bd_merchant_onboarding", "启动 BD 商家入驻"),
            Map.entry("wait_bd_merchant_onboarding", "等待商家与店铺激活"),
            Map.entry("submit_platform_warehouse_fulfillment", "启动平台仓采购与履约"),
            Map.entry("wait_platform_warehouse_fulfillment", "等待收货调拨与履约验收"),
            Map.entry("open_replenishment_day", "创建智能补货行动单"),
            Map.entry("notice_replenishment_day", "通知补货运营负责人"),
            Map.entry("claim_replenishment_day", "认领智能补货行动单"),
            Map.entry("resolve_replenishment_day", "关闭智能补货行动单"),
            Map.entry("verify_replenishment_day_resolved", "验收智能补货闭环"),
            Map.entry("submit_quality_traffic_activation", "启动质检通过商品流量承接"),
            Map.entry("wait_quality_traffic_activation", "等待站内活动验收"),
            Map.entry("resolve_warehouse_admission_case", "关闭入仓决策行动单"),
            Map.entry("verify_warehouse_admission_resolved", "验收入仓决策闭环终态"),
            Map.entry("open_experience_case", "创建消费者体验判责行动单"),
            Map.entry("notice_experience_case", "通知消费者体验判责责任人"),
            Map.entry("claim_experience_case", "认领消费者体验判责行动单"),
            Map.entry("submit_consumer_journey", "生成全新消费者订单与体验证据"),
            Map.entry("wait_consumer_journey", "等待消费者订单旅程完成"),
            Map.entry("ticket_receive_complaint", "记录消费者体验投诉"),
            Map.entry("ticket_start_investigation", "启动跨域证据调查"),
            Map.entry("ticket_resolve_investigation", "形成调查处置方案"),
            Map.entry("ticket_responsibility_decision", "写入工单判责结论"),
            Map.entry("ticket_request_compensation", "申请消费者服务补偿"),
            Map.entry("ticket_approve_compensation", "审批消费者服务补偿"),
            Map.entry("ticket_pay_compensation", "支付消费者服务补偿"),
            Map.entry("resolve_experience_case", "关闭消费者体验判责行动单"),
            Map.entry("verify_experience_case_resolved", "验收消费者体验判责终态"),
            Map.entry("validate_responsible_merchant", "校验被判责商家与店铺"),
            Map.entry("open_merchant_rectification_case", "创建商家整改行动单"),
            Map.entry("notice_merchant_rectification_case", "触达商家整改责任人"),
            Map.entry("claim_merchant_rectification_case", "认领商家整改行动单"),
            Map.entry("reopen_consumer_ticket", "重开原消费者工单"),
            Map.entry("notify_rectification_progress", "回传商家整改进度"),
            Map.entry("resolve_recovered_experience", "二次解决消费者体验问题"),
            Map.entry("verify_merchant_rectification", "复核商家整改结果"),
            Map.entry("collect_recovery_feedback", "收集消费者体验恢复反馈"),
            Map.entry("close_recovered_ticket", "重新关闭消费者工单"),
            Map.entry("resolve_merchant_rectification_case", "关闭商家整改行动单"),
            Map.entry("verify_merchant_rectification_case", "验收商家整改行动单终态"),
            Map.entry("verify_recovered_ticket", "验收消费者工单恢复终态"),
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
            Map.entry("submit_placement_campaign", "启动全新合作投放活动"),
            Map.entry("wait_placement_campaign", "等待合作活动启用"),
            Map.entry("open_candidate_case", "建立 KOL 候选合作案例"),
            Map.entry("qualify_candidate", "完成候选品牌安全与合规筛选"),
            Map.entry("start_outreach", "启动 KOL 合作邀约"),
            Map.entry("submit_brief", "提交合作 brief"),
            Map.entry("approve_brief", "独立审核合作 brief"),
            Map.entry("submit_content", "提交合作内容审阅"),
            Map.entry("approve_content", "独立审核合作内容"),
            Map.entry("verify_publication_disclosure", "核验外部发布与广告披露"),
            Map.entry("reconcile_attribution", "核对真实订单与支付归因"),
            Map.entry("approve_settlement", "独立复核达人结算"),
            Map.entry("mark_settlement_paid", "登记受控付款回执"),
            Map.entry("verify_partner_marketing_closed", "验收发布、归因、结算与关单终态"),
            Map.entry("submit_production_readiness", "准备新品、产线与工艺路线"),
            Map.entry("wait_production_readiness", "等待新品试产准备完成"),
            Map.entry("create_unit_measure", "建立生产计量单位"),
            Map.entry("create_item_type", "建立产成品分类"),
            Map.entry("create_item", "创建业务商品主数据"),
            Map.entry("create_workshop", "建立试产车间"),
            Map.entry("create_process", "建立关键生产工序"),
            Map.entry("create_workstation", "建立试产工作站"),
            Map.entry("create_route", "建立新品工艺路线"),
            Map.entry("create_route_process", "配置关键工序"),
            Map.entry("enable_route", "启用新品工艺路线"),
            Map.entry("create_work_order", "创建新品试产工单"),
            Map.entry("confirm_work_order", "确认生产工单"),
            Map.entry("create_production_task", "派发生产任务"),
            Map.entry("record_production_feedback", "记录合格生产报工"),
            Map.entry("submit_production_feedback", "提交生产报工审核"),
            Map.entry("approve_production_feedback", "复核报工并完成产成品入库"),
            Map.entry("finish_work_order", "完成生产工单"),
            Map.entry("verify_production_closed_loop", "验收工单、任务、报工与入库终态"),
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
            Map.entry("disposition_assess", "AI 成色与处置评估"),
            Map.entry("inspection_accept", "退货质检通过"),
            Map.entry("wait_resolution", "确认处置、退款与订单关闭"),
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
            Map.entry("purchase_requisition", "创建已批准采购申请"),
            Map.entry("event_create", "创建采购寻源事件"),
            Map.entry("event_publish", "发布采购寻源事件"),
            Map.entry("invite_supplier_a", "邀请候选供应商 A"),
            Map.entry("invite_supplier_b", "邀请候选供应商 B"),
            Map.entry("open_quoting", "开放供应商报价"),
            Map.entry("quotation_a_submit", "记录供应商 A 报价版本"),
            Map.entry("quotation_b_submit", "记录供应商 B 报价版本"),
            Map.entry("close_quoting", "关闭报价窗口"),
            Map.entry("evaluation_policy_create", "冻结多维评估策略"),
            Map.entry("evaluation_a_record", "记录独立评审 A 评分"),
            Map.entry("evaluation_b_record", "记录独立评审 B 评分"),
            Map.entry("award_draft_create", "创建多供应商定标草案"),
            Map.entry("award_submit", "提交定标审批"),
            Map.entry("award_approve", "独立批准定标"),
            Map.entry("event_close", "关闭采购寻源事件"),
            Map.entry("purchase_order_a_create", "创建供应商 A 采购订单"),
            Map.entry("purchase_order_a_submit", "提交供应商 A 采购订单"),
            Map.entry("purchase_order_a_approve", "批准供应商 A 采购订单"),
            Map.entry("purchase_order_a_release", "释放供应商 A 采购订单"),
            Map.entry("purchase_order_a_dispatch", "下发供应商 A 采购订单"),
            Map.entry("purchase_order_a_supplier_confirm", "确认供应商 A 采购订单"),
            Map.entry("purchase_order_b_create", "创建供应商 B 采购订单"),
            Map.entry("purchase_order_b_submit", "提交供应商 B 采购订单"),
            Map.entry("purchase_order_b_approve", "批准供应商 B 采购订单"),
            Map.entry("purchase_order_b_release", "释放供应商 B 采购订单"),
            Map.entry("purchase_order_b_dispatch", "下发供应商 B 采购订单"),
            Map.entry("purchase_order_b_supplier_confirm", "确认供应商 B 采购订单"),
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
            Map.entry("submit_paid_unshipped_order", "生成已支付未发货订单"),
            Map.entry("wait_paid_unshipped_order", "等待订单场景准备完成"),
            Map.entry("open_cancellation_case", "创建订单取消异常案件"),
            Map.entry("notice_cancellation_case", "通知订单异常运营"),
            Map.entry("claim_cancellation_case", "认领订单取消异常案件"),
            Map.entry("resolve_cancellation_case", "关闭订单取消异常案件"),
            Map.entry("verify_cancellation_case_resolved", "核验订单取消案件终态"),
            Map.entry("verify_order_cancellation_closed_loop", "复核订单取消补偿闭环"),
            Map.entry("inspect_order_to_cash", "回读订单到回款终态"),
            Map.entry("inspect_order_cancellation", "回读订单取消终态"),
            Map.entry("inspect_fulfillment_exception", "回读履约异常终态"),
            Map.entry("inspect_return_refund", "回读退货退款终态"),
            Map.entry("inspect_customer_resolution", "回读客户问题解决终态"),
            Map.entry("inspect_quality_recall", "回读质量召回终态"),
            Map.entry("inspect_risk_dispute", "回读风险争议终态"),
            Map.entry("inspect_payment_reconciliation", "回读支付对账终态"),
            Map.entry("inspect_supplier_confirmation", "回读供应商采购确认"),
            Map.entry("inspect_procurement_sourcing_decision", "回读采购寻源定标终态"),
            Map.entry("inspect_finance_close", "回读财务关账终态"),
            Map.entry("inspect_allocation_transfer", "回读库存调拨终态"),
            Map.entry("inspect_warehouse_inbound", "回读仓库入库终态")
            ,Map.entry("create_wms_merchant", "创建每日仓储商家")
            ,Map.entry("create_source_warehouse", "创建收货源仓")
            ,Map.entry("create_target_warehouse", "创建调拨目标仓")
            ,Map.entry("create_item_category", "创建仓储商品分类")
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
            ,Map.entry("submit_procurement_sourcing", "发起补货采购寻源")
            ,Map.entry("wait_procurement_sourcing", "等待采购定标完成")
            ,Map.entry("submit_purchase_order", "发起补货采购订单")
            ,Map.entry("wait_purchase_order", "等待采购订单确认")
            ,Map.entry("submit_physical_warehouse_cycle", "发起补货仓储作业")
            ,Map.entry("wait_physical_warehouse_cycle", "等待仓储收发调盘闭环")
            ,Map.entry("open_category_day", "创建品类运营行动单")
            ,Map.entry("notice_category_day", "通知品类运营岗位")
            ,Map.entry("claim_category_day", "认领品类运营行动单")
            ,Map.entry("submit_product_launch", "发起新品铺货")
            ,Map.entry("wait_product_launch", "等待新品发布完成")
            ,Map.entry("submit_conversion_experiment", "发起转化实验")
            ,Map.entry("wait_conversion_experiment", "等待实验决策完成")
            ,Map.entry("submit_targeted_campaign", "发起目标活动触达")
            ,Map.entry("wait_targeted_campaign", "等待活动触达完成")
            ,Map.entry("submit_consumer_validation", "发起消费者选购验证")
            ,Map.entry("wait_consumer_validation", "等待消费者旅程完成")
            ,Map.entry("resolve_category_day", "关闭品类运营行动单")
            ,Map.entry("verify_category_day_resolved", "核验品类运营行动单终态")
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
            case "skill.cloudmold.partner-marketing.kol-media-operations.v1" ->
                    partnerMarketingLifecycle(task, steps);
            case "skill.cloudmold.mes.production-execution-lifecycle.v1" ->
                    mesProductionLifecycle(task, steps);
            case "skill.cloudmold.supply.replenishment-lifecycle.v1" ->
                    replenishmentLifecycle(task, steps);
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
        return stepDisplayName(step.getStepCode(), childWorkflowSkillId(step));
    }

    public String stepDisplayName(String stepCode, String childSkillId) {
        String explicit = STEP_NAMES.get(stepCode);
        if (explicit != null) {
            return explicit;
        }
        if (stepCode.startsWith("define_")) {
            return "创建第 " + numericSuffix(stepCode) + " 个 SKU";
        }
        if (stepCode.startsWith("activate_sku_")) {
            return "启用第 " + numericSuffix(stepCode) + " 个 SKU";
        }
        if (stepCode.startsWith("activate_size_")) {
            return "启用尺码 " + stepCode.substring("activate_size_".length()).toUpperCase(Locale.ROOT);
        }
        if (stepCode.startsWith("activate_color_")) {
            return "启用颜色 " + stepCode.substring("activate_color_".length());
        }
        if (stepCode.startsWith("plan_")) {
            return "生成第 " + numericSuffix(stepCode) + " 个 SKU 投影方案";
        }
        if (stepCode.startsWith("submit_")) {
            return "启动子流程：" + skillDisplayName(childSkillId);
        }
        if (stepCode.startsWith("wait_")) {
            return "等待子流程完成：" + skillDisplayName(childSkillId);
        }
        return stepCode;
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
        String disposition = statusLabel(text(aftersale, "dispositionCode"));
        return outcome("AFTERSALE_REFUND",
                "售后单 " + valueOr(afterSaleNo, text(aftersale, "afterSaleId"))
                        + " 已完成退货处置与退款",
                "订单 " + valueOr(orderNo, text(order, "orderId"))
                        + " 已完成发布、支付、履约、AI 成色评估、独立复检、"
                        + valueOr(disposition, "退货处置") + "和退款闭环。",
                List.of(metric("退款金额", amount), metric("售后状态", statusLabel(text(aftersale, "caseStatus"))),
                        metric("处置结论", disposition),
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

    private ManagedSkillTaskBusinessOutcomeView partnerMarketingLifecycle(
            Task task, List<Step> steps) {
        JsonNode product = result(steps, "wait_product_launch");
        JsonNode listing = product.at("/outputs/listing_publish");
        JsonNode campaign = result(steps, "wait_placement_campaign").at("/outputs/campaign_activate");
        JsonNode consumer = result(steps, "wait_consumer_validation");
        JsonNode order = consumer.at("/outputs/order_place");
        JsonNode payment = consumer.at("/outputs/payment_capture");
        JsonNode closed = result(steps, "verify_partner_marketing_closed");
        JsonNode artifacts = closed.path("artifacts");
        JsonNode partnerCase = artifact(artifacts, "PARTNER_MARKETING_CASE");
        JsonNode publication = artifact(artifacts, "PUBLICATION");
        JsonNode settlement = artifact(artifacts, "SETTLEMENT");
        String caseId = valueOr(text(partnerCase, "id"), text(closed, "businessKey"));
        return outcome("PARTNER_MARKETING_LIFECYCLE",
                "KOL 合作案例 " + valueOr(caseId, "目标案例") + " 已完成投放归因与结算",
                "海外合作运营已完成候选筛选、邀约、brief 与内容双重审核、广告披露核验、"
                        + "真实消费者选购支付归因、达人结算付款和终态关单；外部发布与付款为受控测试证据。",
                List.of(
                        metric("合作状态", "CLOSED".equals(text(closed, "currentStatus"))
                                ? "已关闭" : statusLabel(text(closed, "currentStatus"))),
                        metric("归因订单", valueOr(text(order, "orderNo"), text(order, "orderId"))),
                        metric("支付状态", statusLabel(text(payment, "currentStatus"))),
                        metric("披露核验", "VERIFIED".equals(text(publication, "status"))
                                ? "已核验" : statusLabel(text(publication, "status"))),
                        metric("结算状态", statusLabel(text(settlement, "status")))),
                List.of(
                        object("PARTNER_MARKETING_CASE", "KOL 合作案例",
                                caseId, caseId, text(closed, "currentStatus")),
                        object("LISTING", "合作商品刊登",
                                text(listing, "listingId"), text(listing, "listingNo"), "PUBLISHED"),
                        object("CAMPAIGN", "合作投放活动",
                                text(campaign, "campaignId"), text(campaign, "campaignCode"), "ACTIVE"),
                        object("ORDER", "归因订单",
                                text(order, "orderId"), text(order, "orderNo"), text(order, "currentStatus")),
                        object("PAYMENT", "归因支付",
                                text(payment, "paymentId"), text(payment, "paymentNo"),
                                text(payment, "currentStatus")),
                        object("SETTLEMENT", "达人结算",
                                text(settlement, "id"), text(settlement, "id"), text(settlement, "status"))),
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

    private ManagedSkillTaskBusinessOutcomeView mesProductionLifecycle(Task task, List<Step> steps) {
        JsonNode readback = result(steps, "verify_production_closed_loop");
        return outcome("MES_PRODUCTION_EXECUTION",
                "新品试产、合格品入库与生产工单已闭环",
                "生产主管已完成新品产线准备、工单确认、任务派工、报工复核、产成品入库和工单完工；"
                        + "工单、任务、报工与入库单数量已通过终态核验。",
                List.of(
                        metric("计划生产", valueOr(text(readback, "plannedQuantity"), "0")),
                        metric("实际产出", valueOr(text(readback, "outputQuantity"), "0")),
                        metric("合格产出", valueOr(text(readback, "passedOutputQuantity"), "0")),
                        metric("不合格产出", valueOr(text(readback, "failedOutputQuantity"), "0"))),
                List.of(
                        object("MES_WORK_ORDER", "生产工单",
                                text(readback, "workOrderId"), null,
                                statusLabel(text(readback, "workOrderStatus"))),
                        object("MES_PRODUCTION_TASK", "生产任务",
                                text(readback, "taskId"), null,
                                statusLabel(text(readback, "taskStatus"))),
                        object("MES_PRODUCTION_FEEDBACK", "生产报工",
                                text(readback, "feedbackId"), null,
                                statusLabel(text(readback, "feedbackStatus"))),
                        object("MES_PRODUCT_PRODUCE", "产成品入库单",
                                text(readback, "produceId"), null,
                                statusLabel(text(readback, "produceStatus")))),
                task);
    }

    private ManagedSkillTaskBusinessOutcomeView genericSuccess(Task task, List<Step> steps) {
        String name = SKILL_NAMES.getOrDefault(task.getSkillId(), "Agent 任务");
        return outcome("GENERIC", name + "已完成",
                "任务共完成 " + succeededCount(steps) + " 个步骤，业务结果已通过终态校验。",
                List.of(metric("成功步骤", Integer.toString(succeededCount(steps)))), List.of(), task);
    }

    private ManagedSkillTaskBusinessOutcomeView replenishmentLifecycle(Task task, List<Step> steps) {
        JsonNode input = json(task.getInputJson());
        JsonNode purchaseOrder = input.path("procurement").path("purchaseOrder");
        JsonNode sourcingCase = input.path("sourcing").path("sourcingCase");
        JsonNode warehouse = input.path("warehouse");
        JsonNode sku = warehouse.path("item").path("skus").path(0);
        JsonNode receipt = warehouse.path("receipt");
        JsonNode movement = warehouse.path("movement");
        JsonNode shipment = warehouse.path("shipment");

        String skuCode = valueOr(text(sku, "code"), text(purchaseOrder, "skuCode"));
        String orderedQuantity = valueOr(text(purchaseOrder, "orderedQuantity"), "-");
        String quantityWithUnit = orderedQuantity + " " + unitLabel(text(purchaseOrder, "uomCode"));
        String purchaseOrderCode = valueOr(text(purchaseOrder, "orderCode"), text(purchaseOrder, "orderId"));
        String sourcingCode = valueOr(text(sourcingCase, "rfqCode"), text(sourcingCase, "id"));
        String receiptNo = valueOr(text(receipt, "no"), text(receipt, "bizOrderNo"));
        String movementNo = text(movement, "no");
        String shipmentNo = text(shipment, "no");

        return outcome("REPLENISHMENT_LIFECYCLE",
                "补货 SKU " + valueOr(skuCode, "-") + " 已完成采购与仓储闭环",
                "计划补货 " + quantityWithUnit + "；采购单 " + valueOr(purchaseOrderCode, "-")
                        + " 已下发，并已完成收货、调拨、出库与库存验收。",
                List.of(
                        metric("补货 SKU", skuCode),
                        metric("计划补货量", quantityWithUnit),
                        metric("补货采购单", purchaseOrderCode),
                        metric("采购金额", money(purchaseOrder.path("totalAmountMinor"), text(purchaseOrder, "currencyCode"))),
                        metric("采购收货单", receiptNo),
                        metric("仓间调拨单", movementNo),
                        metric("销售出库单", shipmentNo)),
                List.of(
                        object("SKU", "补货 SKU", text(purchaseOrder, "canonicalSkuId"), skuCode, "SUCCEEDED"),
                        object("SUPPLIER_SOURCING", "供应商寻源单", text(sourcingCase, "id"), sourcingCode, "SUCCEEDED"),
                        object("PROCUREMENT_ORDER", "补货采购单", text(purchaseOrder, "orderId"), purchaseOrderCode, "SUCCEEDED"),
                        object("WAREHOUSE_RECEIPT", "采购收货单", text(receipt, "id"), receiptNo, "SUCCEEDED"),
                        object("WAREHOUSE_MOVEMENT", "仓间调拨单", text(movement, "id"), movementNo, "SUCCEEDED"),
                        object("WAREHOUSE_SHIPMENT", "销售出库单", text(shipment, "id"), shipmentNo, "SUCCEEDED")),
                task);
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

    private JsonNode artifact(JsonNode artifacts, String type) {
        if (artifacts == null || !artifacts.isArray()) {
            return objectMapper.missingNode();
        }
        for (JsonNode artifact : artifacts) {
            if (type.equals(text(artifact, "type"))) {
                return artifact;
            }
        }
        return objectMapper.missingNode();
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
            case "SUPPLIER_SOURCING" -> "供应商寻源单";
            case "PROCUREMENT_ORDER" -> "补货采购单";
            case "WAREHOUSE_RECEIPT" -> "采购收货单";
            case "WAREHOUSE_MOVEMENT" -> "仓间调拨单";
            case "WAREHOUSE_SHIPMENT" -> "销售出库单";
            default -> type;
        };
    }

    private static String statusLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "-";
        }
        return switch (status) {
            case "ACTIVE" -> "已启用";
            case "OPEN" -> "已创建";
            case "NOTICED" -> "已通知";
            case "CLAIMED" -> "已认领";
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
            case "RESTOCK" -> "返售";
            case "SCRAP" -> "报废";
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

    private static String unitLabel(String unit) {
        return switch (valueOr(unit, "")) {
            case "EA" -> "件";
            case "BOX" -> "箱";
            default -> valueOr(unit, "件");
        };
    }

    private static String valueOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
