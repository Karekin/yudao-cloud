package cn.iocoder.yudao.module.cloudmold.merchant.service.workflow;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.merchant.api.workflow.MerchantManagedAdmissionWorkflowResult.Status;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantAiDiagnosticDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantBuyerAssignmentDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantExitDecisionDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantGradeDecisionDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantManagedAdmissionDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantManagedEvidencePackageDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantManagedFinalReviewDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantManagedInvitationDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantMonthlyScorecardDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantOnboardingApplicationDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantProbationAssessmentDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.mysql.MerchantStoreMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerchantManagedAdmissionWorkflowQueryServiceTest {

    private final MerchantStoreMapper merchantMapper = mock(MerchantStoreMapper.class);
    private final MerchantManagedAdmissionWorkflowQueryService service =
            new MerchantManagedAdmissionWorkflowQueryService(merchantMapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void reportsSucceededOnlyAfterExplicitFinalReview() {
        when(merchantMapper.selectApplication(7L, "app-1")).thenReturn(new MerchantOnboardingApplicationDO()
                .setApplicationId("app-1").setStatus("APPROVED").setVersion(4L));
        when(merchantMapper.selectManagedAdmissionByApplication(7L, "app-1")).thenReturn(new MerchantManagedAdmissionDO()
                .setAdmissionId("admission-1").setApplicationId("app-1").setMerchantId("merchant-1")
                .setShopId("shop-1").setAttributionChannelCode("invite-001")
                .setStatus("FINAL_APPROVED").setVersion(8L));
        when(merchantMapper.selectLatestManagedEvidencePackage(7L, "admission-1")).thenReturn(
                new MerchantManagedEvidencePackageDO().setEvidencePackageId("pkg-1").setStatus("SUBMITTED")
                        .setVersion(1L));
        when(merchantMapper.selectLatestAiDiagnostic(7L, "admission-1")).thenReturn(
                new MerchantAiDiagnosticDO().setDiagnosticId("diag-1").setStatus("ACCEPTED")
                        .setVersion(2L));
        when(merchantMapper.selectLatestManagedFinalReview(7L, "admission-1")).thenReturn(
                new MerchantManagedFinalReviewDO().setFinalReviewId("review-1").setStatus("APPROVED")
                        .setVersion(1L));
        when(merchantMapper.selectManagedInvitationByCode(7L, "invite-001")).thenReturn(
                new MerchantManagedInvitationDO().setInvitationId("inv-1").setInvitationCode("invite-001")
                        .setStatus("USED").setVersion(2L));
        when(merchantMapper.selectLatestBuyerAssignment(7L, "admission-1")).thenReturn(
                new MerchantBuyerAssignmentDO().setBuyerAssignmentId("buyer-1").setStatus("ACTIVE")
                        .setBuyerTlPrincipalId("tl-1").setBuyerPrincipalId("buyer-operator-1").setVersion(1L));
        when(merchantMapper.selectLatestGradeDecision(7L, "merchant-1")).thenReturn(
                new MerchantGradeDecisionDO().setGradeDecisionId("grade-1").setGradeCode("A")
                        .setDecisionStatus("APPROVED").setVersion(3L));
        when(merchantMapper.selectLatestProbationAssessment(7L, "merchant-1")).thenReturn(
                new MerchantProbationAssessmentDO().setProbationAssessmentId("prob-1").setAssessmentStatus("PASS")
                        .setVersion(2L));
        when(merchantMapper.selectMonthlyScorecardByMonth(7L, "merchant-1", "2026-08")).thenReturn(
                new MerchantMonthlyScorecardDO().setScorecardId("score-1").setScorecardMonth("2026-08")
                        .setScorecardStatus("REVIEWED").setVersion(4L));
        when(merchantMapper.selectLatestApprovedExitDecision(7L, "merchant-1")).thenReturn(
                new MerchantExitDecisionDO().setExitDecisionId("exit-1").setReasonType("RED_LINE")
                        .setDecisionStatus("APPROVED").setVersion(1L));

        assertThat(service.inspect("app-1")).satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(Status.SUCCEEDED);
            assertThat(result.getTerminal()).isTrue();
            assertThat(result.getSummary()).contains("不自动授予权益");
            assertThat(result.getInvitationId()).isEqualTo("inv-1");
            assertThat(result.getInvitationStatus()).isEqualTo("USED");
            assertThat(result.getBuyerAssignmentId()).isEqualTo("buyer-1");
            assertThat(result.getBuyerAssignmentStatus()).isEqualTo("ACTIVE");
            assertThat(result.getGradeDecisionId()).isEqualTo("grade-1");
            assertThat(result.getGradeDecisionStatus()).isEqualTo("APPROVED");
            assertThat(result.getProbationAssessmentId()).isEqualTo("prob-1");
            assertThat(result.getProbationAssessmentStatus()).isEqualTo("PASS");
            assertThat(result.getScorecardId()).isEqualTo("score-1");
            assertThat(result.getScorecardMonth()).isEqualTo("2026-08");
            assertThat(result.getScorecardStatus()).isEqualTo("REVIEWED");
            assertThat(result.getExitDecisionId()).isEqualTo("exit-1");
            assertThat(result.getExitDecisionStatus()).isEqualTo("APPROVED");
            assertThat(result.getArtifacts()).extracting("type")
                    .contains("MANAGED_ADMISSION", "EVIDENCE_PACKAGE", "AI_DIAGNOSTIC", "FINAL_REVIEW",
                            "MANAGED_INVITATION", "BUYER_ASSIGNMENT", "GRADE_DECISION",
                            "PROBATION_ASSESSMENT", "MONTHLY_SCORECARD", "EXIT_DECISION");
        });
    }
}
