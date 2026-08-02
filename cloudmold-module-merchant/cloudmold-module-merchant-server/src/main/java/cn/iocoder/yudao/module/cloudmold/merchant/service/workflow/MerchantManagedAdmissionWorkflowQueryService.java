package cn.iocoder.yudao.module.cloudmold.merchant.service.workflow;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.merchant.api.workflow.MerchantManagedAdmissionWorkflowQueryPort;
import cn.iocoder.yudao.module.cloudmold.merchant.api.workflow.MerchantManagedAdmissionWorkflowResult;
import cn.iocoder.yudao.module.cloudmold.merchant.api.workflow.MerchantManagedAdmissionWorkflowResult.Artifact;
import cn.iocoder.yudao.module.cloudmold.merchant.api.workflow.MerchantManagedAdmissionWorkflowResult.Status;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantAiDiagnosticDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantBuyerAssignmentDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantExitDecisionDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantFactoryInspectionTaskDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantGradeDecisionDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantManagedAdmissionDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantManagedEvidencePackageDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantManagedFinalReviewDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantManagedInvitationDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantMonthlyScorecardDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantOnboardingApplicationDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantProbationAssessmentDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.mysql.MerchantStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class MerchantManagedAdmissionWorkflowQueryService implements MerchantManagedAdmissionWorkflowQueryPort {

    private final MerchantStoreMapper merchantMapper;

    @Override
    public MerchantManagedAdmissionWorkflowResult inspect(String applicationId) {
        requireId(applicationId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String key = applicationId.trim();
        MerchantOnboardingApplicationDO application = merchantMapper.selectApplication(tenantId, key);
        if (application == null) {
            return result(key, Status.PREPARE, "入驻前置条件", false, "尚未创建规范入驻申请", 0L,
                    List.of("Merchant SoR 中不存在入驻申请"),
                    List.of("先完成规范入驻建档"), List.of());
        }
        List<Artifact> artifacts = new ArrayList<>();
        artifacts.add(artifact("ONBOARDING_APPLICATION", application.getApplicationId(), application.getStatus(),
                application.getVersion(), "规范入驻申请"));
        if (!"APPROVED".equals(application.getStatus())) {
            return result(key, Status.WAITING, "规范入驻审批", false,
                    "托管准入需等待规范入驻审批完成", application.getVersion(),
                    List.of("当前入驻状态：" + application.getStatus()),
                    List.of("完成规范入驻审批"), artifacts);
        }

        MerchantManagedAdmissionDO admission = merchantMapper.selectManagedAdmissionByApplication(tenantId, key);
        if (admission == null) {
            return result(key, Status.WAITING, "托管准入建档", false,
                    "规范入驻已通过，但托管准入尚未建档", application.getVersion(),
                    List.of("未创建托管商家准入档案"),
                    List.of("执行 OPEN_MANAGED_ADMISSION 建档"), artifacts);
        }
        artifacts.add(artifact("MANAGED_ADMISSION", admission.getAdmissionId(), admission.getStatus(),
                admission.getVersion(), "托管准入档案"));
        MerchantManagedEvidencePackageDO evidencePackage = merchantMapper.selectLatestManagedEvidencePackage(tenantId,
                admission.getAdmissionId());
        MerchantAiDiagnosticDO diagnostic = merchantMapper.selectLatestAiDiagnostic(tenantId, admission.getAdmissionId());
        MerchantFactoryInspectionTaskDO inspectionTask = merchantMapper.selectLatestFactoryInspectionTask(tenantId,
                admission.getAdmissionId());
        MerchantManagedFinalReviewDO finalReview = merchantMapper.selectLatestManagedFinalReview(tenantId,
                admission.getAdmissionId());
        MerchantManagedInvitationDO invitation = evidenceText(admission.getAttributionChannelCode())
                ? merchantMapper.selectManagedInvitationByCode(tenantId, admission.getAttributionChannelCode()) : null;
        MerchantBuyerAssignmentDO buyerAssignment = merchantMapper.selectLatestBuyerAssignment(tenantId,
                admission.getAdmissionId());
        MerchantGradeDecisionDO gradeDecision = merchantMapper.selectLatestGradeDecision(tenantId, admission.getMerchantId());
        MerchantProbationAssessmentDO probationAssessment = merchantMapper.selectLatestProbationAssessment(tenantId,
                admission.getMerchantId());
        MerchantMonthlyScorecardDO monthlyScorecard = merchantMapper.selectMonthlyScorecardByMonth(tenantId,
                admission.getMerchantId(), currentScorecardMonth());
        MerchantExitDecisionDO exitDecision = merchantMapper.selectLatestApprovedExitDecision(tenantId,
                admission.getMerchantId());
        if (evidencePackage != null) {
            artifacts.add(artifact("EVIDENCE_PACKAGE", evidencePackage.getEvidencePackageId(), evidencePackage.getStatus(),
                    evidencePackage.getVersion(), "托管准入证据包"));
        }
        if (diagnostic != null) {
            artifacts.add(artifact("AI_DIAGNOSTIC", diagnostic.getDiagnosticId(), diagnostic.getStatus(),
                    diagnostic.getVersion(), "AI 诊断建议"));
        }
        if (inspectionTask != null) {
            artifacts.add(artifact("FACTORY_INSPECTION", inspectionTask.getInspectionTaskId(), inspectionTask.getStatus(),
                    inspectionTask.getVersion(),
                    inspectionTask.getEvidenceRef() == null ? "验厂任务" : "验厂任务（含外部结果证据）"));
        }
        if (finalReview != null) {
            artifacts.add(artifact("FINAL_REVIEW", finalReview.getFinalReviewId(), finalReview.getStatus(),
                    finalReview.getVersion(), "终审结论"));
        }
        if (invitation != null) {
            artifacts.add(artifact("MANAGED_INVITATION", invitation.getInvitationId(), invitation.getStatus(),
                    invitation.getVersion(), "受控邀请码"));
        }
        if (buyerAssignment != null) {
            artifacts.add(artifact("BUYER_ASSIGNMENT", buyerAssignment.getBuyerAssignmentId(), buyerAssignment.getStatus(),
                    buyerAssignment.getVersion(), "买手/买手TL 分配"));
        }
        if (gradeDecision != null) {
            artifacts.add(artifact("GRADE_DECISION", gradeDecision.getGradeDecisionId(), gradeDecision.getDecisionStatus(),
                    gradeDecision.getVersion(), "等级与权益决策"));
        }
        if (probationAssessment != null) {
            artifacts.add(artifact("PROBATION_ASSESSMENT", probationAssessment.getProbationAssessmentId(),
                    probationAssessment.getAssessmentStatus(), probationAssessment.getVersion(), "试用期三关评估"));
        }
        if (monthlyScorecard != null) {
            artifacts.add(artifact("MONTHLY_SCORECARD", monthlyScorecard.getScorecardId(),
                    monthlyScorecard.getScorecardStatus(), monthlyScorecard.getVersion(), "月度九项义务评分"));
        }
        if (exitDecision != null) {
            artifacts.add(artifact("EXIT_DECISION", exitDecision.getExitDecisionId(), exitDecision.getDecisionStatus(),
                    exitDecision.getVersion(), "清退决策"));
        }
        long version = maxVersion(application.getVersion(), admission.getVersion(),
                evidencePackage == null ? null : evidencePackage.getVersion(),
                diagnostic == null ? null : diagnostic.getVersion(),
                inspectionTask == null ? null : inspectionTask.getVersion(),
                finalReview == null ? null : finalReview.getVersion(),
                invitation == null ? null : invitation.getVersion(),
                buyerAssignment == null ? null : buyerAssignment.getVersion(),
                gradeDecision == null ? null : gradeDecision.getVersion(),
                probationAssessment == null ? null : probationAssessment.getVersion(),
                monthlyScorecard == null ? null : monthlyScorecard.getVersion(),
                exitDecision == null ? null : exitDecision.getVersion());
        return switch (admission.getStatus()) {
            case "FINAL_APPROVED" -> enrich(result(key, Status.SUCCEEDED, "托管准入通过", true,
                    "托管商家准入已完成，当前仅保留人工审核证据和结论，不自动授予权益", version,
                    List.of(), List.of(), artifacts), invitation, buyerAssignment, gradeDecision, probationAssessment,
                    monthlyScorecard, exitDecision);
            case "FINAL_REJECTED", "DIAGNOSTIC_REJECTED", "INSPECTION_CANCELLED" -> enrich(result(key, Status.FAILED,
                    "托管准入终止", true, "托管准入链路已被人工驳回或终止", version,
                    blockersForFailure(admission, diagnostic, inspectionTask, finalReview),
                    List.of("根据人工结论重新补证或重新发起新的准入流程"), artifacts), invitation, buyerAssignment,
                    gradeDecision, probationAssessment, monthlyScorecard, exitDecision);
            case "ATTRIBUTION_PENDING" -> enrich(result(key, Status.WAITING, "归因登记", false,
                    "待登记邀请/来源归因", version,
                    List.of("尚未记录邀请/来源归因和证据"),
                    List.of("执行 RECORD_MANAGED_ATTRIBUTION"), artifacts), invitation, buyerAssignment,
                    gradeDecision, probationAssessment, monthlyScorecard, exitDecision);
            case "EVIDENCE_PENDING" -> enrich(result(key, Status.WAITING, "证据包提交", false,
                    "待提交托管准入证据包", version,
                    List.of("尚未形成托管证据包"),
                    List.of("执行 SUBMIT_MANAGED_EVIDENCE_PACKAGE"), artifacts), invitation, buyerAssignment,
                    gradeDecision, probationAssessment, monthlyScorecard, exitDecision);
            case "DIAGNOSTIC_PENDING" -> enrich(result(key, Status.WAITING, "AI 诊断建议", false,
                    "待生成 AI 诊断建议", version,
                    List.of("尚未生成 AI 诊断建议"),
                    List.of("执行 PROPOSE_AI_DIAGNOSTIC"), artifacts), invitation, buyerAssignment,
                    gradeDecision, probationAssessment, monthlyScorecard, exitDecision);
            case "DIAGNOSTIC_PENDING_REVIEW" -> enrich(result(key, Status.WAITING, "人工复核 AI 诊断", false,
                    "AI 诊断建议待人工接受或驳回", version,
                    List.of("AI 诊断尚未人工确认"),
                    List.of("执行 ACCEPT_AI_DIAGNOSTIC 或 REJECT_AI_DIAGNOSTIC"), artifacts), invitation, buyerAssignment,
                    gradeDecision, probationAssessment, monthlyScorecard, exitDecision);
            case "INSPECTION_PENDING" -> enrich(result(key, Status.WAITING, "验厂任务创建", false,
                    "AI 建议已通过，待创建验厂任务或直接进入终审", version,
                    List.of("尚未创建验厂任务"),
                    List.of("推荐为 FACTORY_INSPECTION_REQUIRED 时执行 CREATE_FACTORY_INSPECTION_TASK；否则可直接终审"),
                    artifacts), invitation, buyerAssignment, gradeDecision, probationAssessment, monthlyScorecard,
                    exitDecision);
            case "INSPECTION_IN_PROGRESS" -> enrich(result(key, Status.RUNNING, "验厂执行中", false,
                    "验厂任务处于执行状态机中", version,
                    List.of("验厂与复核尚未完成"),
                    List.of(nextInspectionAction(inspectionTask)), artifacts), invitation, buyerAssignment,
                    gradeDecision, probationAssessment, monthlyScorecard, exitDecision);
            case "FINAL_REVIEW_PENDING" -> enrich(result(key, Status.WAITING, "终审结论", false,
                    "待人工终审并显式给出准入结论", version,
                    List.of("尚未记录终审结论"),
                    List.of("执行 RECORD_MANAGED_FINAL_REVIEW"), artifacts), invitation, buyerAssignment, gradeDecision,
                    probationAssessment, monthlyScorecard, exitDecision);
            default -> enrich(result(key, Status.RUNNING, "准入处理中", false,
                    "托管准入链路处于处理中状态", version,
                    List.of("当前状态：" + admission.getStatus()),
                    List.of("按当前状态补齐人工动作"), artifacts), invitation, buyerAssignment, gradeDecision,
                    probationAssessment, monthlyScorecard, exitDecision);
        };
    }

    private static List<String> blockersForFailure(MerchantManagedAdmissionDO admission, MerchantAiDiagnosticDO diagnostic,
                                                   MerchantFactoryInspectionTaskDO inspectionTask,
                                                   MerchantManagedFinalReviewDO finalReview) {
        List<String> blockers = new ArrayList<>();
        if ("DIAGNOSTIC_REJECTED".equals(admission.getStatus()) && diagnostic != null) {
            blockers.add("AI 诊断已被人工驳回：" + diagnostic.getRecommendationCode());
        }
        if ("INSPECTION_CANCELLED".equals(admission.getStatus()) && inspectionTask != null) {
            blockers.add("验厂任务已取消，状态：" + inspectionTask.getStatus());
        }
        if ("FINAL_REJECTED".equals(admission.getStatus()) && finalReview != null) {
            blockers.add("终审结论为驳回");
        }
        if (blockers.isEmpty()) {
            blockers.add("当前状态：" + admission.getStatus());
        }
        return blockers;
    }

    private static String nextInspectionAction(MerchantFactoryInspectionTaskDO inspectionTask) {
        if (inspectionTask == null) {
            return "补建验厂任务";
        }
        return switch (inspectionTask.getStatus()) {
            case "PENDING_CLAIM" -> "执行 CLAIM_FACTORY_INSPECTION_TASK";
            case "PENDING_SCHEDULE" -> "执行 SCHEDULE_FACTORY_INSPECTION_TASK";
            case "PENDING_INSPECTION" -> "执行 SUBMIT_FACTORY_INSPECTION";
            case "PENDING_QA_INSPECTION" -> "执行 REQUEST_FACTORY_REMEDIATION 或 SUBMIT_FACTORY_FIRST_REVIEW";
            case "PENDING_REMEDIATION" -> "执行 SUBMIT_FACTORY_FIRST_REVIEW";
            case "PENDING_FIRST_REVIEW" -> "执行 SUBMIT_FACTORY_FINAL_REVIEW";
            case "PENDING_FINAL_REVIEW" -> "执行 COMPLETE_FACTORY_INSPECTION_TASK";
            default -> "核对验厂任务状态";
        };
    }

    private static MerchantManagedAdmissionWorkflowResult result(String key, Status status, String phase,
                                                                boolean terminal, String summary, Long version,
                                                                List<String> blockers, List<String> next,
                                                                List<Artifact> artifacts) {
        return MerchantManagedAdmissionWorkflowResult.builder()
                .workflowType("MerchantManagedAdmissionWorkflow")
                .workflowInstanceKey("merchant-managed-admission:" + key)
                .businessKey(key)
                .status(status)
                .phase(phase)
                .terminal(terminal)
                .actionRequired(!next.isEmpty())
                .summary(summary)
                .aggregateVersion(version)
                .blockers(blockers)
                .nextActions(next)
                .artifacts(artifacts)
                .build();
    }

    private static MerchantManagedAdmissionWorkflowResult enrich(MerchantManagedAdmissionWorkflowResult result,
                                                                 MerchantManagedInvitationDO invitation,
                                                                 MerchantBuyerAssignmentDO buyerAssignment,
                                                                 MerchantGradeDecisionDO gradeDecision,
                                                                 MerchantProbationAssessmentDO probationAssessment,
                                                                 MerchantMonthlyScorecardDO monthlyScorecard,
                                                                 MerchantExitDecisionDO exitDecision) {
        return result.toBuilder()
                .invitationId(invitation == null ? null : invitation.getInvitationId())
                .invitationCode(invitation == null ? null : invitation.getInvitationCode())
                .invitationStatus(invitation == null ? null : invitation.getStatus())
                .invitationVersion(invitation == null ? null : invitation.getVersion())
                .buyerAssignmentId(buyerAssignment == null ? null : buyerAssignment.getBuyerAssignmentId())
                .buyerAssignmentStatus(buyerAssignment == null ? null : buyerAssignment.getStatus())
                .buyerAssignmentVersion(buyerAssignment == null ? null : buyerAssignment.getVersion())
                .buyerTlPrincipalId(buyerAssignment == null ? null : buyerAssignment.getBuyerTlPrincipalId())
                .buyerPrincipalId(buyerAssignment == null ? null : buyerAssignment.getBuyerPrincipalId())
                .gradeDecisionId(gradeDecision == null ? null : gradeDecision.getGradeDecisionId())
                .gradeCode(gradeDecision == null ? null : gradeDecision.getGradeCode())
                .gradeDecisionStatus(gradeDecision == null ? null : gradeDecision.getDecisionStatus())
                .gradeDecisionVersion(gradeDecision == null ? null : gradeDecision.getVersion())
                .probationAssessmentId(probationAssessment == null ? null : probationAssessment.getProbationAssessmentId())
                .probationAssessmentStatus(probationAssessment == null ? null : probationAssessment.getAssessmentStatus())
                .probationAssessmentVersion(probationAssessment == null ? null : probationAssessment.getVersion())
                .scorecardId(monthlyScorecard == null ? null : monthlyScorecard.getScorecardId())
                .scorecardMonth(monthlyScorecard == null ? null : monthlyScorecard.getScorecardMonth())
                .scorecardStatus(monthlyScorecard == null ? null : monthlyScorecard.getScorecardStatus())
                .scorecardVersion(monthlyScorecard == null ? null : monthlyScorecard.getVersion())
                .exitDecisionId(exitDecision == null ? null : exitDecision.getExitDecisionId())
                .exitReasonType(exitDecision == null ? null : exitDecision.getReasonType())
                .exitDecisionStatus(exitDecision == null ? null : exitDecision.getDecisionStatus())
                .exitDecisionVersion(exitDecision == null ? null : exitDecision.getVersion())
                .build();
    }

    private static Artifact artifact(String type, String id, String status, Long version, String label) {
        return Artifact.builder().type(type).id(id).status(status).version(version).label(label).build();
    }

    private static long maxVersion(Long... values) {
        long max = 0L;
        for (Long value : values) {
            if (value != null && value > max) {
                max = value;
            }
        }
        return max;
    }

    private static void requireId(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("applicationId is required");
        }
    }

    private static boolean evidenceText(String value) {
        return StringUtils.hasText(value);
    }

    private static String currentScorecardMonth() {
        return java.time.YearMonth.now(ZoneOffset.UTC).toString();
    }
}
