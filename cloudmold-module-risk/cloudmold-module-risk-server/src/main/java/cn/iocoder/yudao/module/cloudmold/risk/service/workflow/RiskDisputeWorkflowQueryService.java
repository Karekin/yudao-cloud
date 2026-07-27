package cn.iocoder.yudao.module.cloudmold.risk.service.workflow;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.risk.api.workflow.RiskDisputeWorkflowQueryPort;
import cn.iocoder.yudao.module.cloudmold.risk.api.workflow.RiskDisputeWorkflowResult;
import cn.iocoder.yudao.module.cloudmold.risk.api.workflow.RiskDisputeWorkflowResult.Artifact;
import cn.iocoder.yudao.module.cloudmold.risk.api.workflow.RiskDisputeWorkflowResult.Status;
import cn.iocoder.yudao.module.cloudmold.risk.dal.dataobject.RiskRecords.Decision;
import cn.iocoder.yudao.module.cloudmold.risk.dal.dataobject.RiskRecords.LossEntry;
import cn.iocoder.yudao.module.cloudmold.risk.dal.dataobject.RiskRecords.PaymentDispute;
import cn.iocoder.yudao.module.cloudmold.risk.dal.dataobject.RiskRecords.ReviewCase;
import cn.iocoder.yudao.module.cloudmold.risk.dal.mysql.RiskStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RiskDisputeWorkflowQueryService implements RiskDisputeWorkflowQueryPort {

    private final RiskStoreMapper mapper;

    @Override
    public RiskDisputeWorkflowResult inspect(String disputeId) {
        if (!StringUtils.hasText(disputeId)) {
            throw new IllegalArgumentException("disputeId is required");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String key = disputeId.trim();
        PaymentDispute dispute = mapper.selectPaymentDispute(tenantId, key);
        if (dispute == null) {
            return result(key, Status.PREPARE, "争议准备", false, "尚未创建支付争议", 0L,
                    List.of("Risk SoR 中不存在支付争议"), List.of("创建风险调查案件和支付争议"), List.of());
        }
        List<Artifact> artifacts = new ArrayList<>();
        artifacts.add(artifact("PAYMENT_DISPUTE", dispute.getDisputeId(), dispute.getStatus(), dispute.getVersion(), "支付争议"));
        ReviewCase review = StringUtils.hasText(dispute.getCaseId())
                ? mapper.selectReviewCase(tenantId, dispute.getCaseId()) : null;
        Decision decision = StringUtils.hasText(dispute.getDecisionId())
                ? mapper.selectDecision(tenantId, dispute.getDecisionId()) : null;
        List<LossEntry> losses = safe(mapper.selectLossEntriesByDispute(tenantId, key));
        if (review != null) {
            artifacts.add(artifact("REVIEW_CASE", review.getCaseId(), review.getStatus(), review.getVersion(), "风险调查案件"));
        }
        if (decision != null) {
            artifacts.add(artifact("RISK_DECISION", decision.getDecisionId(), decision.getDecisionType(), null, "人工风险决策"));
        }
        losses.forEach(loss -> artifacts.add(artifact("LOSS_ENTRY", loss.getLossEntryId(), loss.getEntryType(), null, "损失台账")));
        if ("OPEN".equals(dispute.getStatus())) {
            List<String> blockers = new ArrayList<>();
            List<String> next = new ArrayList<>();
            if (review == null) {
                blockers.add("支付争议未关联风险调查案件");
                next.add("创建并关联风险调查案件");
            } else {
                blockers.add("风险调查和人工决策尚未完成");
                next.add("完成调查、审批并处置争议");
            }
            return result(key, review == null ? Status.WAITING : Status.RUNNING, "风险调查与审批", false,
                    "支付争议正在调查处理中", dispute.getVersion(), blockers, next, artifacts);
        }

        List<String> blockers = new ArrayList<>();
        List<String> next = new ArrayList<>();
        if (review == null || !("DECIDED".equals(review.getStatus()) || "CLOSED".equals(review.getStatus()))) {
            blockers.add("风险调查案件尚未形成终态");
            next.add("完成风险案件调查");
        }
        if (decision == null) {
            blockers.add("缺少真实人工风险决策");
            next.add("记录人工审批决策");
        }
        if ("LOST".equals(dispute.getStatus()) && losses.stream().noneMatch(loss ->
                "CHARGEBACK_LOSS".equals(loss.getEntryType()) || "CONFIRMED_RISK_LOSS".equals(loss.getEntryType()))) {
            blockers.add("败诉争议缺少损失台账");
            next.add("登记拒付或风险损失");
        }
        if ("REVERSED".equals(dispute.getStatus())
                && losses.stream().noneMatch(loss -> "REVERSAL".equals(loss.getEntryType()))) {
            blockers.add("撤销争议缺少冲销台账");
            next.add("登记损失冲销");
        }
        if (!List.of("WON", "LOST", "REVERSED", "CANCELLED").contains(dispute.getStatus())) {
            blockers.add("争议状态不是规范终态：" + dispute.getStatus());
            next.add("人工核对争议状态");
        }
        long version = review == null || review.getVersion() == null ? dispute.getVersion()
                : Math.max(dispute.getVersion(), review.getVersion());
        if (!blockers.isEmpty()) {
            return result(key, Status.WAITING, "争议终态核验", false,
                    "争议已更新，但调查、审批或损失证据尚不完整", version, blockers, next, artifacts);
        }
        return result(key, Status.SUCCEEDED, "风险争议闭环", true,
                "支付争议 " + dispute.getDisputeId() + " 已完成调查、人工决策和损失核验，终态为 "
                        + dispute.getStatus(), version, List.of(), List.of(), artifacts);
    }

    private static RiskDisputeWorkflowResult result(String key, Status status, String phase, boolean terminal,
                                                     String summary, Long version, List<String> blockers,
                                                     List<String> next, List<Artifact> artifacts) {
        return RiskDisputeWorkflowResult.builder().workflowType("RiskDisputeWorkflow")
                .workflowInstanceKey("risk-dispute:" + key).businessKey(key).status(status).phase(phase)
                .terminal(terminal).actionRequired(!next.isEmpty()).summary(summary).aggregateVersion(version)
                .blockers(blockers).nextActions(next).artifacts(artifacts).build();
    }

    private static Artifact artifact(String type, String id, String status, Long version, String label) {
        return Artifact.builder().type(type).id(id).status(status).version(version).label(label).build();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
