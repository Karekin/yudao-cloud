package cn.iocoder.yudao.module.cloudmold.merchant.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.identity.api.PrincipalValidationApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.unpublish.ListingUnpublishSagaCommandApi;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantBenefitEntitlementItem;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantCommand;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantCommandResult;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantOperation;
import cn.iocoder.yudao.module.cloudmold.merchant.api.SourceReference;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantExitDecisionDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantFactoryInspectionTaskDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantGradeDecisionDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantManagedAdmissionDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantManagedInvitationDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantOperationDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.mysql.MerchantStoreMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MerchantManagedGrowthCommandServiceTest {

    private Harness harness;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        harness = new Harness();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void issuesAndUsesInvitationThenBlocksGradeDecisionAfterApprovedExit() {
        MerchantCommandResult issued = harness.execute(base(MerchantOperation.ISSUE_MANAGED_INVITATION, "growth-1")
                .setRecruiterPrincipalId("principal-recruiter")
                .setInvitationCode("invite-001")
                .setInvitationEvidenceRef("evidence:invite-001")
                .setSourceReference(new SourceReference().setSourceSystem("ERP").setSourceType("MERCHANT")
                        .setSourceId("legacy-merchant-001"))
                .setAttributionReference("recruitment-wave-1"));

        assertThat(issued.getInvitationStatus()).isEqualTo("ISSUED");
        verify(harness.principalValidationApi).requireActivePrincipal("principal-recruiter");

        MerchantCommandResult used = harness.execute(base(MerchantOperation.MARK_MANAGED_INVITATION_USED, "growth-2")
                .setInvitationCode("invite-001")
                .setAdmissionId("admission-1")
                .setExpectedVersion(issued.getInvitationVersion()));

        assertThat(used.getInvitationStatus()).isEqualTo("USED");
        assertThat(used.getAdmissionStatus()).isEqualTo("EVIDENCE_PENDING");

        MerchantCommandResult exit = harness.execute(base(MerchantOperation.RECORD_EXIT_DECISION, "growth-3")
                .setMerchantId("merchant-1")
                .setAdmissionId("admission-1")
                .setReviewDecision("APPROVED")
                .setExitReasonType("RED_LINE")
                .setExitEvidenceRef("evidence:exit-001")
                .setReviewNote("红线违规"));

        assertThat(exit.getExitDecisionStatus()).isEqualTo("APPROVED");

        assertThatThrownBy(() -> harness.execute(base(MerchantOperation.RECORD_GRADE_BENEFIT_DECISION, "growth-4")
                .setMerchantId("merchant-1")
                .setGradeDecisionStatus("APPROVED")
                .setGradeCode("A")
                .setGradeTransitionDecision("UPGRADE")
                .setThresholdsConfigRef("config:grade-thresholds-v1")
                .setBenefitDecisionEvidenceRef("evidence:grade-001")
                .setBenefitEntitlements(List.of(new MerchantBenefitEntitlementItem()
                        .setBenefitCode("RESOURCE_SLOT")
                        .setEntitlementStatus("APPROVED")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approved exit decision blocks grade or benefit changes");
    }

    private static MerchantCommand base(MerchantOperation operation, String key) {
        return new MerchantCommand()
                .setOperation(operation)
                .setIdempotencyKey(key)
                .setRunId("growth-run")
                .setSourceSystem("CLOUDMOLD")
                .setTraceId("growth-trace")
                .setOccurredAt(Instant.parse("2026-08-02T10:00:00Z"));
    }

    private static final class Harness {
        final MerchantStoreMapper mapper = mock(MerchantStoreMapper.class);
        final PrincipalValidationApi principalValidationApi = mock(PrincipalValidationApi.class);
        final OutboxAppender outboxAppender = mock(OutboxAppender.class);
        final ListingUnpublishSagaCommandApi listingUnpublishSagaCommandApi = mock(ListingUnpublishSagaCommandApi.class);
        final MerchantCommandServiceImpl service = new MerchantCommandServiceImpl(mapper, principalValidationApi,
                outboxAppender, listingUnpublishSagaCommandApi);
        final AtomicLong sequence = new AtomicLong();
        final ThreadLocal<Long> lastOperation = new ThreadLocal<>();
        final Map<String, Long> operationByKey = new HashMap<>();
        final Map<Long, MerchantOperationDO> operations = new HashMap<>();
        final Map<String, MerchantManagedInvitationDO> invitations = new LinkedHashMap<>();
        final Map<String, MerchantManagedAdmissionDO> admissions = new LinkedHashMap<>();
        final Map<String, MerchantExitDecisionDO> exits = new LinkedHashMap<>();
        final Map<String, MerchantGradeDecisionDO> gradeDecisions = new LinkedHashMap<>();

        Harness() {
            admissions.put("admission-1", new MerchantManagedAdmissionDO()
                    .setAdmissionId("admission-1")
                    .setApplicationId("app-1")
                    .setMerchantId("merchant-1")
                    .setShopId("shop-1")
                    .setStatus("ATTRIBUTION_PENDING")
                    .setVersion(1L));
            stubOperations();
            when(mapper.selectManagedInvitationByCodeForUpdate(eq(7L), anyString()))
                    .thenAnswer(i -> selectInvitation(i.getArgument(1)));
            when(mapper.selectManagedAdmissionForUpdate(eq(7L), anyString()))
                    .thenAnswer(i -> admissions.get(i.getArgument(1)));
            doAnswer(i -> {
                MerchantManagedInvitationDO value = i.getArgument(0);
                invitations.put(value.getInvitationCode(), value);
                return 1;
            }).when(mapper).insertManagedInvitation(any());
            when(mapper.transitionManagedInvitation(anyLong(), anyString(), anyLong(), anyString(), anyString(), any(), any(), any()))
                    .thenAnswer(i -> transitionInvitation(i.getArgument(1), i.getArgument(2), i.getArgument(3),
                            i.getArgument(4), i.getArgument(5)));
            when(mapper.transitionManagedAdmission(anyLong(), anyString(), anyLong(), anyString(), anyString(),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(i -> transitionAdmission(i.getArgument(1), i.getArgument(2), i.getArgument(3),
                            i.getArgument(4), i.getArgument(5), i.getArgument(6), i.getArgument(7),
                            i.getArgument(8), i.getArgument(9), i.getArgument(10), i.getArgument(11),
                            i.getArgument(12), i.getArgument(13)));
            doAnswer(i -> {
                MerchantExitDecisionDO value = i.getArgument(0);
                exits.put(value.getExitDecisionId(), value);
                return 1;
            }).when(mapper).insertExitDecision(any());
            when(mapper.selectLatestApprovedExitDecision(eq(7L), eq("merchant-1"))).thenAnswer(i ->
                    exits.values().stream().filter(value -> "APPROVED".equals(value.getDecisionStatus()))
                            .findFirst().orElse(null));
            doAnswer(i -> {
                MerchantGradeDecisionDO value = i.getArgument(0);
                gradeDecisions.put(value.getGradeDecisionId(), value);
                return 1;
            }).when(mapper).insertGradeDecision(any());
            when(mapper.selectLatestBuyerAssignment(anyLong(), anyString())).thenReturn(null);
            when(mapper.selectLatestManagedEvidencePackage(anyLong(), anyString())).thenReturn(null);
            when(mapper.selectLatestAiDiagnostic(anyLong(), anyString())).thenReturn(null);
            when(mapper.selectLatestFactoryInspectionTask(anyLong(), anyString())).thenReturn(null);
            when(mapper.selectLatestManagedFinalReview(anyLong(), anyString())).thenReturn(null);
            when(mapper.selectLatestManagedEvidencePackageForUpdate(anyLong(), anyString())).thenReturn(null);
            when(mapper.selectManagedInvitationByCode(anyLong(), anyString())).thenAnswer(i -> selectInvitation(i.getArgument(1)));
            when(mapper.selectFactoryInspectionTaskForUpdate(anyLong(), anyString())).thenReturn(new MerchantFactoryInspectionTaskDO());
            doAnswer(i -> 1).when(mapper).insertHistory(any());
        }

        MerchantCommandResult execute(MerchantCommand command) {
            return service.execute(command);
        }

        private MerchantManagedInvitationDO selectInvitation(String code) {
            return invitations.get(code.trim().toUpperCase());
        }

        private int transitionInvitation(String invitationId, Long expectedVersion, String before, String after,
                                         String usedAdmissionId) {
            MerchantManagedInvitationDO value = invitations.values().stream()
                    .filter(item -> invitationId.equals(item.getInvitationId()))
                    .findFirst().orElse(null);
            if (value == null || !expectedVersion.equals(value.getVersion()) || !before.equals(value.getStatus())) {
                return 0;
            }
            value.setStatus(after).setUsedAdmissionId(usedAdmissionId).setUsedAt(LocalDateTime.now(ZoneOffset.UTC))
                    .setVersion(value.getVersion() + 1);
            return 1;
        }

        private int transitionAdmission(String admissionId, Long expectedVersion, String before, String after,
                                        String attributionChannelCode, String attributionSourceSystem,
                                        String attributionSourceType, String attributionSourceId,
                                        String attributionReference, String attributionEvidenceRef,
                                        String diagnosticId, String inspectionTaskId, String finalReviewId) {
            MerchantManagedAdmissionDO value = admissions.get(admissionId);
            if (value == null || !expectedVersion.equals(value.getVersion()) || !before.equals(value.getStatus())) {
                return 0;
            }
            value.setStatus(after)
                    .setAttributionChannelCode(attributionChannelCode)
                    .setAttributionSourceSystem(attributionSourceSystem)
                    .setAttributionSourceType(attributionSourceType)
                    .setAttributionSourceId(attributionSourceId)
                    .setAttributionReference(attributionReference)
                    .setAttributionEvidenceRef(attributionEvidenceRef)
                    .setDiagnosticId(diagnosticId)
                    .setInspectionTaskId(inspectionTaskId)
                    .setFinalReviewId(finalReviewId)
                    .setVersion(value.getVersion() + 1);
            return 1;
        }

        private void stubOperations() {
            doAnswer(i -> {
                String key = i.getArgument(1);
                Long existing = operationByKey.get(key);
                if (existing == null) {
                    long id = sequence.incrementAndGet();
                    MerchantOperationDO operation = new MerchantOperationDO()
                            .setOperationId(id)
                            .setTenantId(7L)
                            .setIdempotencyKey(key)
                            .setCommandType(i.getArgument(2))
                            .setRequestHash(i.getArgument(3))
                            .setAttemptToken(i.getArgument(4))
                            .setStatus(0);
                    operations.put(id, operation);
                    operationByKey.put(key, id);
                    lastOperation.set(id);
                } else {
                    lastOperation.set(existing);
                }
                return 1;
            }).when(mapper).insertOrResolveOperation(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
            when(mapper.selectLastInsertId()).thenAnswer(i -> lastOperation.get());
            when(mapper.selectOperationForUpdate(anyLong(), eq(7L))).thenAnswer(i -> operations.get(i.getArgument(0)));
            when(mapper.markOperationSucceeded(anyLong(), eq(7L), anyString(), anyString(), any())).thenAnswer(i -> {
                MerchantOperationDO operation = operations.get(i.getArgument(0));
                operation.setStatus(10);
                operation.setAggregateId(i.getArgument(2));
                operation.setResultJson(i.getArgument(3));
                return 1;
            });
        }
    }
}
