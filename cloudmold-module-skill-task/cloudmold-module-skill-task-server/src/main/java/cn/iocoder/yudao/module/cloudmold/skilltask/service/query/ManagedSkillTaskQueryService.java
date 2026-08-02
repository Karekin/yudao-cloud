package cn.iocoder.yudao.module.cloudmold.skilltask.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskChildRunView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskDetailView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskRunPageRequest;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskRunView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskStepView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowDetailView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowIdempotencyBindingView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowStepView;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Step;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Task;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinition;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinitionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManagedSkillTaskQueryService {

    static final String TRIGGER_SOURCE = "ADMIN_CONSOLE";
    static final String MANAGEMENT_SURFACE = "DEER_FLOW";
    static final String DURABLE_AUTHORITY = "SKILL_TASK";
    private static final Map<String, WorkflowPresentation> WORKFLOW_PRESENTATIONS = Map.ofEntries(
            Map.entry("skill.cloudmold.agentcontrol.mission-lifecycle-stockout.v1",
                    new WorkflowPresentation("缺断码 Mission 生命周期",
                            "在审批后创建固定缺断码处置 Mission，并回读五段真实工作图；不宣称支持通用 Charter、预算、KPI 或动态 DAG。")),
            Map.entry("skill.cloudmold.catalog.inspect-active-sku.v1",
                    new WorkflowPresentation("在售 SKU 查询",
                            "查询当前租户指定 SKU 的规范商品与在售状态，不修改业务数据。")),
            Map.entry("skill.cloudmold.catalog.assortment-wave-readiness.v1",
                    new WorkflowPresentation("波段企划准备度",
                            "按年度、季节和波段汇总真实款式、SPU 与 SKU，并明确趋势、价格带、款量、毛利及供给计划缺口。")),
            Map.entry("skill.cloudmold.catalog.assortment-planning-lifecycle.v1",
                    new WorkflowPresentation("选品与波段企划闭环",
                            "模拟选品经理完成趋势信号收集、候选池建立、AI 多因子评估、毛利与退货约束选款、独立审批、上新日历发布及商品开发交接；每天生成全新波段。")),
            Map.entry("skill.cloudmold.inventory.stockout-diagnosis.v1",
                    new WorkflowPresentation("尺码缺断码诊断",
                            "按 SPU 检查各尺码可售库存，识别缺货与低库存风险，不修改业务数据。")),
            Map.entry("skill.cloudmold.listing.lifecycle-readback.v1",
                    new WorkflowPresentation("商品刊登生命周期终态跟踪",
                            "持续核验刊登发布、渠道回执、暂停、下架、归档及销售资格失效后的批量下架结果。")),
            Map.entry("skill.cloudmold.commerce.catalog-matrix.v1",
                    new WorkflowPresentation("商品款色码建档",
                            "建立款式、SPU 与 6 个 SKU，并完成商品生命周期激活。")),
            Map.entry("skill.cloudmold.commerce.aftersale-saga.v1",
                    new WorkflowPresentation("跨境退货质检处置闭环",
                            "贯通发布、库存、下单支付、履约、退货智能成色评估、复检、返售或报废处置、退款与订单关闭。")),
            Map.entry("skill.cloudmold.commerce.legacy-projection-plan.v1",
                    new WorkflowPresentation("旧系统投影预检",
                            "只读规划规范 SKU 向 Mall、ERP 与 WMS 的兼容投影，不执行旧系统写入。")),
            Map.entry("skill.cloudmold.commerce.reuse-ready-master.v1",
                    new WorkflowPresentation("商家与仓网主数据准备",
                            "校验身份与 ERP 仓，创建并激活商家店铺，绑定可用仓网。")),
            Map.entry("skill.cloudmold.commerce.terminal-readback.v1",
                    new WorkflowPresentation("全链路终态核验",
                            "只读核验商品、商家、刊登、订单、支付、履约、售后及仓网终态。")),
            Map.entry("skill.cloudmold.commerce.full-chain-hsf.v1",
                    new WorkflowPresentation("商品售后自治全链路",
                            "依次编排商品建档、旧系统投影、商家仓网、售后 Saga 与终态核验。")),
            Map.entry("skill.cloudmold.commerce.product-to-listing.v1",
                    new WorkflowPresentation("自动铺品",
                            "串联规范商品建档、商家店铺准备、商品刊登审核发布与终态回读；缺少真实渠道回执时明确标记待渠道确认。")),
            Map.entry("skill.cloudmold.pricing.reprice-lifecycle.v1",
                    new WorkflowPresentation("定价与收益运营",
                            "仅对已确认发布的刊登创建不可变报价修订，重新取得渠道回执；缺少前置刊登证据时不发起调价。")),
            Map.entry("skill.cloudmold.commerce.autonomous-day.v1",
                    new WorkflowPresentation("AI 自主经营日",
                            "每日生成全新商品与刊登，再由独立消费者身份完成选购、支付履约、售后、客服和社区种草闭环。")),
            Map.entry("skill.cloudmold.commerce.category-daily-operations.v1",
                    new WorkflowPresentation("品类日常运营闭环",
                            "品类运营创建并认领行动单，依次完成新品铺货、转化实验、活动触达和真实消费者选购验证；"
                                    + "四条业务链全部成功后才关闭行动单，每日轮换问题场景并生成全新业务数据。")),
            Map.entry("skill.cloudmold.commerce.product-management-lifecycle.v1",
                    new WorkflowPresentation("商品管理主流程",
                            "商品运营从全平台供给池选品、平台大店铺品、新品企划与爆款培育，"
                                    + "到神秘买手实购、审版核价、素材拍摄、模特试穿和质量标验收；"
                                    + "只有质量证据 VERIFIED 后才完成商品管理闭环。")),
            Map.entry("skill.cloudmold.customer-experience.ticket-responsibility-lifecycle.v1",
                    new WorkflowPresentation("消费者体验工单判责",
                            "每天创建全新的消费者订单与投诉案例，关联订单证据完成调查、商家责任判定、"
                                    + "服务补偿、关单和双终态验收；判责未生效或赔付未完成时不会触发商家整改。")),
            Map.entry("skill.cloudmold.customer-experience.unfulfillable-order-compensation-lifecycle.v1",
                    new WorkflowPresentation("无法履约订单主动赔付",
                            "对已支付未发货且无法履约的订单，先委托取消 Saga 完成发货拦截、全额退款、"
                                    + "库存释放和订单取消，再完成消费者通知、平台责任判定、服务赔付与关单。")),
            Map.entry("skill.cloudmold.consumer.shopping-journey.v1",
                    new WorkflowPresentation("消费者选购与服务全旅程",
                            "模拟真实会员完成搜索、商详、收藏、加购、结算、下单支付、履约、售后、咨询与社区发布。")),
            Map.entry("skill.cloudmold.supply-planning.prepare.v1",
                    new WorkflowPresentation("补货单准备",
                            "将已批准的补货建议转换为真实采购或调拨草稿，并明确后续等待的供应商或仓储事件。")),
            Map.entry("skill.cloudmold.operations.daily-business-control.v1",
                    new WorkflowPresentation("日经营控制",
                            "只读汇总当日经营指标、异常与建议工单；事实不完整时保持待数据状态。")),
            Map.entry("skill.cloudmold.operations.weekly-business-review.v1",
                    new WorkflowPresentation("周经营复盘",
                            "只读汇总周度目标偏差、异常与行动建议；事实不完整时保持待数据状态。")),
            Map.entry("skill.cloudmold.merchant.onboarding-readback.v1",
                    new WorkflowPresentation("商家入驻终态跟踪",
                            "持续回读商家申请、门店与授权终态，不代替人工审批或领域写入。")),
            Map.entry("skill.cloudmold.merchant.onboarding-lifecycle.v1",
                    new WorkflowPresentation("商家入驻经营闭环",
                            "模拟招商运营完成资料建档、提交、审核、批准、商家激活、店铺激活与终态验收；每天创建全新的测试商家。")),
            Map.entry("skill.cloudmold.merchant.managed-growth-lifecycle.v1",
                    new WorkflowPresentation("托管商家入驻与成材",
                            "围绕托管商家准入、来源归因、证据包、AI 货盘建议、验厂状态机与人工终审，"
                                    + "执行受审批保护的 Merchant red-zone 命令链并形成终审结果；"
                                    + "买手分配、等级权益和清退仍保留人工边界，AI 不自动授予权益、变更等级或执行清退。")),
            Map.entry("skill.cloudmold.merchant-experience.rectification-lifecycle.v1",
                    new WorkflowPresentation("商家触发整改与体验恢复",
                            "仅在商家责任判定生效且服务补偿已支付后触发，校验责任商家、重开原工单、"
                                    + "回传整改进度、二次解决、质量复核、满意度回访并重新关单。")),
            Map.entry("skill.cloudmold.engagement.promotion-campaign-operations.v1",
                    new WorkflowPresentation("促销活动投放闭环",
                            "模拟活动运营完成活动启用、人群触达、渠道发送、送达、打开、点击、活动收尾与终态验收；每天生成全新活动。")),
            Map.entry("skill.cloudmold.growth.experiment-lifecycle.v1",
                    new WorkflowPresentation("增长实验决策闭环",
                            "模拟增长运营完成实验活动、分组、曝光、指标快照、护栏判断、显著性结论与终态验收；每天生成全新实验。")),
            Map.entry("skill.cloudmold.procurement.sourcing-lifecycle.v1",
                    new WorkflowPresentation("采购寻源定标闭环",
                            "基于已筛选的规范供应商候选完成多行、多交期采购申请、寻源发布、双供应商报价、独立多维评分、定标审批与事件关闭。")),
            Map.entry("skill.cloudmold.procurement.order-lifecycle.v1",
                    new WorkflowPresentation("采购订单履约闭环",
                            "基于最近一次真实定标结果创建采购订单，完成审批下发、供应商确认与终态验收；每天生成全新采购订单。")),
            Map.entry("skill.cloudmold.wms.operations.v1",
                    new WorkflowPresentation("仓储收发调盘闭环",
                            "模拟仓储运营完成商家与双仓造数、商品建档、收货、调拨、出库、盘点及库存验收；每天生成全新仓储单据与库存轨迹。")),
            Map.entry("skill.cloudmold.supply.replenishment-lifecycle.v1",
                    new WorkflowPresentation("智能补货执行闭环",
                            "模拟补货运营从需求场景、双供应商寻源、定标、采购下发到收货、调拨、出库与库存验收；日常补货与大促补货按日期自动轮换。")),
            Map.entry("skill.cloudmold.supply.warehouse-admission-lifecycle.v1",
                    new WorkflowPresentation("入仓决策主流程",
                            "供应链小二以 VERIFIED 买样质检为硬门槛，完成 BD 商家入驻、采购补货、"
                                    + "平台仓收货调拨与履约验收，并以站内活动承接质检通过商品的质量流量。")),
            Map.entry("skill.cloudmold.supply-planning.sop-lifecycle.v1",
                    new WorkflowPresentation("需求预测与 S&OP 决策闭环",
                            "模拟需求计划经理完成预测发布与偏差评估、供需双场景测算、AI 鲁棒推荐、方案选择、会签发布、补货批准并生成真实 WMS 调拨草稿。")),
            Map.entry("skill.cloudmold.customer-service.resolution-lifecycle.v1",
                    new WorkflowPresentation("客服咨询解决闭环",
                            "模拟真实用户就最近订单发起咨询，客服完成建单、关联订单、接收消息、分派、处理、解决、满意度反馈、关单与终态验收。")),
            Map.entry("skill.cloudmold.engagement.promotion-campaign-readback.v1",
                    new WorkflowPresentation("促销活动终态跟踪",
                            "持续回读活动与投放结果；依赖数据未齐备时保持等待并展示阻塞项。")),
            Map.entry("skill.cloudmold.engagement.growth-experiment-readback.v1",
                    new WorkflowPresentation("增长实验终态跟踪",
                            "持续回读增长实验结果；样本或归因未齐备时保持等待并展示阻塞项。")),
            Map.entry("skill.cloudmold.commerce.order-cancellation-operational.v1",
                    new WorkflowPresentation("订单取消补偿闭环",
                            "模拟订单异常运营为每天新建的已支付未发货订单完成开案、通知、认领、"
                                    + "履约关闭、退款、库存释放、订单取消、结案和双终态验收；"
                                    + "当前支付退款使用 INTERNAL_TEST 权威，真实 PSP 仍由外部系统负责。")),
            Map.entry("skill.cloudmold.commerce.order-to-cash-readback.v1",
                    new WorkflowPresentation("订单到回款终态跟踪",
                            "持续核验订单、库存、支付与履约事实，直至订单到回款链路形成终态。")),
            Map.entry("skill.cloudmold.commerce.order-cancellation-readback.v1",
                    new WorkflowPresentation("订单取消终态跟踪",
                            "持续核验取消 Saga、库存释放与退款事实，异常或人工处理会明确留痕。")),
            Map.entry("skill.cloudmold.commerce.fulfillment-exception-readback.v1",
                    new WorkflowPresentation("履约异常终态跟踪",
                            "持续核验订单履约异常的处理结果，未闭环时保持等待或人工处理状态。")),
            Map.entry("skill.cloudmold.fulfillment.exception-resolution-lifecycle.v1",
                    new WorkflowPresentation("履约异常处置闭环",
                            "模拟物流经理为全新在途订单识别异常、诊断影响、制定方案、经过审批、恢复交付、完成订单并关闭异常；每天生成全新订单与异常案例。")),
            Map.entry("skill.cloudmold.crossborder.fulfillment-compliance-lifecycle.v1",
                    new WorkflowPresentation("跨境履约与关务合规闭环",
                            "模拟跨境运营为全新已支付订单完成受控直邮合规评估、AI 路线推荐、风险与法务会签、申报与三单校验、国际承运、清关放行、妥投和关单；当前限定 CN→US 测试规则包。")),
            Map.entry("skill.cloudmold.crossborder.bonded-customs-lifecycle.v1",
                    new WorkflowPresentation("保税仓关务闭环",
                            "模拟保税仓关务为全新已支付订单完成准入评估、商品归类、订单/支付/物流三单对碰、税费计算、风险与法务会签、海关受理、保税放行、境内妥投和关单；当前限定受控测试规则包。")),
            Map.entry("skill.cloudmold.partner-marketing.kol-media-operations.v1",
                    new WorkflowPresentation("海外 KOL 与媒体合作投放闭环",
                            "模拟海外合作运营完成候选筛选与风险准入、合作 brief、内容发布核验、真实消费者选购归因、达人结算和终态验收；每天生成全新的合作案例与商品订单。")),
            Map.entry("skill.cloudmold.mes.production-readiness.v1",
                    new WorkflowPresentation("MES 新品试产准备",
                            "内部造数子流程：为一次新品试产建立全新的产品、车间、工作站、关键工序与可用工艺路线。")),
            Map.entry("skill.cloudmold.mes.production-execution-lifecycle.v1",
                    new WorkflowPresentation("新品试产与量产交付闭环",
                            "模拟生产主管完成新品产线准备、工单确认、派工、报工审核、合格品入库、工单完工与终态验收；每天生成全新产品和生产批次。")),
            Map.entry("skill.cloudmold.consumer.in-transit-order-scenario.v1",
                    new WorkflowPresentation("在途订单场景准备",
                            "内部造数子流程：模拟会员选购、下单支付和出库运输，为物流异常岗位流程生成全新的在途订单。")),
            Map.entry("skill.cloudmold.commerce.return-refund-readback.v1",
                    new WorkflowPresentation("退货退款终态跟踪",
                            "持续核验售后、退货质检、退款与库存恢复事实，直至闭环终态。")),
            Map.entry("skill.cloudmold.customer-service.resolution-readback.v1",
                    new WorkflowPresentation("客户问题解决终态跟踪",
                            "持续回读客服工单和解决结果，不越过客服审批或领域写入边界。")),
            Map.entry("skill.cloudmold.quality.recall-readback.v1",
                    new WorkflowPresentation("质量召回终态跟踪",
                            "持续回读质量召回动作及影响范围，证据不完整时保持等待并展示阻塞项。")),
            Map.entry("skill.cloudmold.quality.inspection-recall-lifecycle.v1",
                    new WorkflowPresentation("质量检验与召回闭环",
                            "模拟质量运营岗位完成标准发布、双人持证检验、CAPA、批次召回、库存隔离和终态验收；每天生成全新质检批次。")),
            Map.entry("skill.cloudmold.risk.dispute-readback.v1",
                    new WorkflowPresentation("风险争议终态跟踪",
                            "持续回读风险争议处置结果，未决或待人工裁定时不会误报成功。")),
            Map.entry("skill.cloudmold.risk.dispute-resolution-lifecycle.v1",
                    new WorkflowPresentation("风险争议与损失处置闭环",
                            "风险运营为全新已支付订单完成拒付识别、人工复核、争议终态、损失台账和行动单验收。")),
            Map.entry("skill.cloudmold.data-ai-operations.data-quality-recovery-lifecycle.v1",
                    new WorkflowPresentation("数据质量恢复闭环",
                            "数据与 AI 运营为全新指标数据链路完成质量异常登记、血缘定位、失败证据、修复重跑、DQC 通过和行动单验收。")),
            Map.entry("skill.cloudmold.payment.reconciliation-readback.v1",
                    new WorkflowPresentation("支付对账终态跟踪",
                            "持续核验订单与支付对账结果，账实未一致时保持等待并展示阻塞项。")),
            Map.entry("skill.cloudmold.procurement.supplier-confirmation-readback.v1",
                    new WorkflowPresentation("供应商采购确认跟踪",
                            "从真实采购单创建事件持续核验供应商确认状态；仅覆盖采购确认，不冒充 RFQ、比价、样品或供应商准入。")),
            Map.entry("skill.cloudmold.procurement.sourcing-decision-readback.v1",
                    new WorkflowPresentation("采购寻源定标终态跟踪",
                            "从 Procurement 定标批准事件持续核验规范定标快照；仅 APPROVED 定标终态视为成功。")),
            Map.entry("skill.cloudmold.finance.close-readiness.v1",
                    new WorkflowPresentation("财务关账准备度跟踪",
                            "从真实会计期间开启事件持续核验渠道账单、对账差异、结算批次和凭证；仅领域 CLOSED 终态视为成功。")),
            Map.entry("skill.cloudmold.finance.close-lifecycle.v1",
                    new WorkflowPresentation("财务结算关账闭环",
                            "模拟财务结算岗位以制单、复核双身份完成账单导入、差异调整、结算、凭证、过账、关账与终态验收；每天生成全新账期。")),
            Map.entry("skill.cloudmold.finance.logistics-service-settlement-lifecycle.v1",
                    new WorkflowPresentation("物流服务商仓发提结算",
                            "按仓储、发货和提货报价导入物流账单，委托财务权威完成对账差异、结算确认、凭证过账与关账。")),
            Map.entry("skill.cloudmold.finance.merchant-service-fee-settlement-lifecycle.v1",
                    new WorkflowPresentation("商家技术服务费结算",
                            "按商家销售额、退款额与技术服务费形成净结算，委托财务权威完成独立复核、结算、凭证与关账。")),
            Map.entry("skill.cloudmold.finance.advertising-fee-settlement-lifecycle.v1",
                    new WorkflowPresentation("平台广告费结算",
                            "对广告账单、退款或返还和净应付金额完成对账，委托财务权威完成结算确认、凭证过账及关账。")),
            Map.entry("skill.cloudmold.finance.profit-loss-improvement-lifecycle.v1",
                    new WorkflowPresentation("问题订单与退货损益改善",
                            "读取周经营 KPI 快照，围绕问题订单率、退货损失、确收金额和贡献利润建立可追踪的损益改善行动单。")),
            Map.entry("skill.cloudmold.warehouse.allocation-transfer-readback.v1",
                    new WorkflowPresentation("库存调拨终态跟踪",
                            "从已批准的补货转换事件持续核验真实 WMS 移库单；仅移库完成视为成功，作废明确进入失败终态。")),
            Map.entry("skill.cloudmold.warehouse.inbound-readback.v1",
                    new WorkflowPresentation("仓库入库终态跟踪",
                            "持续核验 ASN、收货与上架状态；仅上架完成视为成功，ASN 取消明确进入失败终态。"))
    );

    private final SkillTaskMapper mapper;
    private final SkillTaskDefinitionRegistry definitionRegistry;
    private final ManagedSkillTaskBusinessOutcomePresenter outcomePresenter;
    private final ManagedSkillTaskBusinessTimelinePresenter timelinePresenter;

    public List<ManagedSkillTaskWorkflowView> listManagedWorkflows() {
        return definitionRegistry.all().stream()
                .filter(definition -> "BUSINESS_ROLE".equals(definition.getWorkflowLevel()))
                .sorted(Comparator.comparing(SkillTaskDefinition::getSkillId)
                        .thenComparing(SkillTaskDefinition::getSkillVersion))
                .map(this::toWorkflowItem)
                .toList();
    }

    public ManagedSkillTaskWorkflowDetailView getManagedWorkflow(String skillId) {
        String normalizedSkillId = normalizeRequired(skillId, "skillId");
        SkillTaskDefinition definition = definitionRegistry.all().stream()
                .filter(item -> normalizedSkillId.equals(item.getSkillId()))
                .filter(item -> "BUSINESS_ROLE".equals(item.getWorkflowLevel()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Managed workflow is not registered: " + normalizedSkillId));
        return ManagedSkillTaskWorkflowDetailView.builder()
                .workflow(toWorkflowItem(definition))
                .steps(definition.getSteps().stream()
                        .map(step -> ManagedSkillTaskWorkflowStepView.builder()
                                .stepOrder(step.getStepOrder())
                                .stepCode(step.getStepCode())
                                .displayName(outcomePresenter.stepDisplayName(
                                        step.getStepCode(), step.getChildSkillId()))
                                .stepKind(step.getStepKind())
                                .operationType(step.getOperationType())
                                .approvalRequired(step.getApprovalRequired())
                                .capabilityId(step.getCapabilityId())
                                .childSkillId(step.getChildSkillId())
                                .childSkillVersion(step.getChildSkillVersion())
                                .pollIntervalSeconds(step.getPollIntervalSeconds())
                                .idempotencyBinding(step.getIdempotencyBinding() == null ? null
                                        : ManagedSkillTaskWorkflowIdempotencyBindingView.builder()
                                        .argumentIndex(step.getIdempotencyBinding().getArgumentIndex())
                                        .jsonPointer(step.getIdempotencyBinding().getJsonPointer())
                                        .build())
                                .waitSuccessJson(step.getWaitSuccess() == null ? null : step.getWaitSuccess().toString())
                                .waitFailureJson(step.getWaitFailure() == null ? null : step.getWaitFailure().toString())
                                .argumentsJson(step.getArguments() == null ? null : step.getArguments().toString())
                                .build())
                        .toList())
                .build();
    }

    public PageResult<ManagedSkillTaskRunView> getManagedRunPage(ManagedSkillTaskRunPageRequest request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String taskId = normalize(request.getTaskId());
        String runId = normalize(request.getRunId());
        String skillId = normalize(request.getSkillId());
        String status = normalizeUpper(request.getStatus());
        String riskLevel = normalizeUpper(request.getRiskLevel());
        long total = mapper.countManagedRunPage(tenantId, taskId, runId, skillId, status, riskLevel);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        List<Task> tasks = mapper.selectManagedRunPage(tenantId, taskId, runId, skillId, status, riskLevel,
                offset, request.getPageSize());
        if (tasks.isEmpty()) {
            return new PageResult<>(List.of(), total);
        }
        Map<String, List<Step>> stepsByTask = mapper.selectStepsForTasks(tenantId,
                        tasks.stream().map(Task::getTaskId).toList()).stream()
                .collect(Collectors.groupingBy(Step::getTaskId, LinkedHashMap::new, Collectors.toList()));
        return new PageResult<>(tasks.stream()
                .map(task -> toRunSummary(task, stepsByTask.getOrDefault(task.getTaskId(), List.of())))
                .toList(), total);
    }

    public ManagedSkillTaskDetailView getManagedRun(String taskId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Task task = requireTask(tenantId, normalizeRequired(taskId, "taskId"));
        List<Task> descendants = loadDescendants(tenantId, task);
        Map<String, Task> childrenByParentStep = new LinkedHashMap<>();
        for (Task child : descendants) {
            if (!task.getTaskId().equals(child.getParentTaskId())) {
                continue;
            }
            childrenByParentStep.putIfAbsent(child.getParentStepCode(), child);
        }
        List<Step> steps = mapper.selectSteps(tenantId, task.getTaskId());
        Map<String, List<Step>> stepsByTask = new LinkedHashMap<>();
        stepsByTask.put(task.getTaskId(), steps);
        if (!descendants.isEmpty()) {
            stepsByTask.putAll(mapper.selectStepsForTasks(tenantId,
                            descendants.stream().map(Task::getTaskId).toList()).stream()
                    .collect(Collectors.groupingBy(
                            Step::getTaskId, LinkedHashMap::new, Collectors.toList())));
        }
        return ManagedSkillTaskDetailView.builder()
                .task(toRunSummary(task, steps))
                .businessPhases(timelinePresenter.present(task, descendants, stepsByTask))
                .steps(steps.stream()
                        .map(step -> toStepItem(step, childrenByParentStep.get(step.getStepCode())))
                        .toList())
                .build();
    }

    private List<Task> loadDescendants(Long tenantId, Task root) {
        final int maxDepth = 4;
        final int maxTasks = 64;
        List<Task> descendants = new ArrayList<>();
        List<Task> frontier = List.of(root);
        Set<String> visited = new HashSet<>();
        visited.add(root.getTaskId());
        for (int depth = 0; depth < maxDepth && !frontier.isEmpty(); depth++) {
            List<Task> next = new ArrayList<>();
            for (Task parent : frontier) {
                for (Task child : mapper.selectChildren(tenantId, parent.getTaskId())) {
                    if (!visited.add(child.getTaskId())) {
                        continue;
                    }
                    descendants.add(child);
                    next.add(child);
                    if (descendants.size() >= maxTasks) {
                        return descendants;
                    }
                }
            }
            frontier = next;
        }
        return descendants;
    }

    private ManagedSkillTaskWorkflowView toWorkflowItem(SkillTaskDefinition definition) {
        WorkflowPresentation presentation = WORKFLOW_PRESENTATIONS.get(definition.getSkillId());
        if (presentation == null) {
            throw new IllegalStateException("Managed workflow presentation is missing: " + definition.getSkillId());
        }
        long writeStepCount = definition.getSteps().stream()
                .filter(step -> "WRITE".equals(step.getOperationType()))
                .count();
        return ManagedSkillTaskWorkflowView.builder()
                .skillId(definition.getSkillId())
                .skillVersion(definition.getSkillVersion())
                .displayName(presentation.displayName())
                .description(presentation.description())
                .workflowLevel(definition.getWorkflowLevel())
                .ownerRole(definition.getOwnerRole())
                .riskLevel(definition.getRiskLevel())
                .maxAttempts(definition.getMaxAttempts())
                .stepCount(definition.getSteps().size())
                .writeStepCount((int) writeStepCount)
                .approvalRequired(!"R1".equals(definition.getRiskLevel()))
                .definitionSha256(definition.getDefinitionSha256())
                .definitionClosureSha256(definition.getDefinitionClosureSha256())
                .triggerSource(TRIGGER_SOURCE)
                .managementSurface(MANAGEMENT_SURFACE)
                .orchestrationSurface(TRIGGER_SOURCE)
                .durableAuthority(DURABLE_AUTHORITY)
                .build();
    }

    private record WorkflowPresentation(String displayName, String description) {
    }

    private ManagedSkillTaskRunView toRunSummary(Task task, List<Step> steps) {
        return ManagedSkillTaskRunView.builder()
                .taskId(task.getTaskId())
                .runId(task.getRunId())
                .skillId(task.getSkillId())
                .skillVersion(task.getSkillVersion())
                .inputSha256(task.getInputSha256())
                .definitionSha256(task.getDefinitionSha256())
                .definitionClosureSha256(task.getDefinitionClosureSha256())
                .terminalResultSha256(task.getTerminalResultSha256())
                .businessOutcome(outcomePresenter.present(task, steps))
                .riskLevel(task.getRiskLevel())
                .parentTaskId(task.getParentTaskId())
                .parentStepCode(task.getParentStepCode())
                .status(task.getStatus())
                .currentStepCode(task.getCurrentStepCode())
                .attemptCount(task.getAttemptCount())
                .maxAttempts(task.getMaxAttempts())
                .version(task.getVersion())
                .startedAt(toInstant(task.getStartedAt()))
                .completedAt(toInstant(task.getCompletedAt()))
                .createdAt(toInstant(task.getCreatedAt()))
                .updatedAt(toInstant(task.getUpdatedAt()))
                .build();
    }

    private ManagedSkillTaskStepView toStepItem(Step step, Task childTask) {
        return ManagedSkillTaskStepView.builder()
                .taskId(step.getTaskId())
                .stepCode(step.getStepCode())
                .displayName(outcomePresenter.stepDisplayName(step))
                .resultSummary(outcomePresenter.stepResultSummary(step))
                .stepOrder(step.getStepOrder())
                .stepKind(step.getStepKind())
                .capabilityId(step.getCapabilityId())
                .operationType(step.getOperationType())
                .childSkillId(step.getChildSkillId())
                .childSkillVersion(step.getChildSkillVersion())
                .childTaskId(step.getChildTaskId())
                .pollIntervalSeconds(step.getPollIntervalSeconds())
                .idempotencyKey(step.getIdempotencyKey())
                .status(step.getStatus())
                .attemptCount(step.getAttemptCount())
                .requestSha256(step.getRequestSha256())
                .resultSha256(step.getResultSha256())
                .lastErrorCode(step.getLastErrorCode())
                .startedAt(toInstant(step.getStartedAt()))
                .completedAt(toInstant(step.getCompletedAt()))
                .createdAt(toInstant(step.getCreatedAt()))
                .updatedAt(toInstant(step.getUpdatedAt()))
                .childTask(childTask == null ? null : toChildSummary(childTask))
                .build();
    }

    private ManagedSkillTaskChildRunView toChildSummary(Task task) {
        return ManagedSkillTaskChildRunView.builder()
                .taskId(task.getTaskId())
                .runId(task.getRunId())
                .skillId(task.getSkillId())
                .skillVersion(task.getSkillVersion())
                .inputSha256(task.getInputSha256())
                .definitionSha256(task.getDefinitionSha256())
                .definitionClosureSha256(task.getDefinitionClosureSha256())
                .terminalResultSha256(task.getTerminalResultSha256())
                .riskLevel(task.getRiskLevel())
                .status(task.getStatus())
                .currentStepCode(task.getCurrentStepCode())
                .version(task.getVersion())
                .startedAt(toInstant(task.getStartedAt()))
                .completedAt(toInstant(task.getCompletedAt()))
                .createdAt(toInstant(task.getCreatedAt()))
                .updatedAt(toInstant(task.getUpdatedAt()))
                .build();
    }

    private Task requireTask(Long tenantId, String taskId) {
        Task task = mapper.selectTask(tenantId, taskId);
        if (task == null) {
            throw new IllegalArgumentException("Skill task does not exist: " + taskId);
        }
        return task;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }

    private static String normalizeRequired(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static Instant toInstant(java.time.LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
