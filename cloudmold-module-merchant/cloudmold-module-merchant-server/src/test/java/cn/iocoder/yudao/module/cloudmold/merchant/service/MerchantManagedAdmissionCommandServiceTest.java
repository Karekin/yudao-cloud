package cn.iocoder.yudao.module.cloudmold.merchant.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.identity.api.PrincipalValidationApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.unpublish.ListingUnpublishSagaCommandApi;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantCommand;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantCommandResult;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantManagedEvidenceItem;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantOperation;
import cn.iocoder.yudao.module.cloudmold.merchant.api.SourceReference;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantAiDiagnosticDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantFactoryInspectionTaskDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantManagedAdmissionDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantManagedEvidencePackageDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantManagedFinalReviewDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantOnboardingApplicationDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.MerchantOperationDO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.mysql.MerchantStoreMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MerchantManagedAdmissionCommandServiceTest {

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
    void completesManagedAdmissionLifecycleWithExplicitHumanGates() {
        MerchantCommandResult admission = harness.execute(command(MerchantOperation.OPEN_MANAGED_ADMISSION, "managed-1")
                .setApplicationId("app-1"));
        MerchantCommandResult attributed = harness.execute(command(MerchantOperation.RECORD_MANAGED_ATTRIBUTION, "managed-2")
                .setAdmissionId(admission.getAdmissionId())
                .setExpectedVersion(admission.getAdmissionVersion())
                .setAttributionChannelCode("yshopping")
                .setSourceReference(new SourceReference().setSourceSystem("erp").setSourceType("merchant")
                        .setSourceId("legacy-merchant-1"))
                .setAttributionReference("invite-batch-20260802")
                .setAttributionEvidenceRef("evidence:managed-attribution-1"));
        MerchantCommandResult packaged = harness.execute(command(MerchantOperation.SUBMIT_MANAGED_EVIDENCE_PACKAGE, "managed-3")
                .setAdmissionId(admission.getAdmissionId())
                .setExpectedVersion(attributed.getAdmissionVersion())
                .setEvidencePackageRef("evidence:managed-package-1")
                .setEvidenceItems(List.of(new MerchantManagedEvidenceItem()
                        .setItemCode("BUSINESS_LICENSE")
                        .setItemLabel("营业执照")
                        .setEvidenceRef("evidence:business-license-1"))));
        MerchantCommandResult proposed = harness.execute(command(MerchantOperation.PROPOSE_AI_DIAGNOSTIC, "managed-4")
                .setAdmissionId(admission.getAdmissionId())
                .setExpectedVersion(packaged.getAdmissionVersion())
                .setRecommendationCode("FACTORY_INSPECTION_REQUIRED")
                .setRecommendationSummary("建议走人工验厂链路")
                .setDiagnosticEvidenceRef("evidence:diagnostic-1"));
        MerchantCommandResult accepted = harness.execute(command(MerchantOperation.ACCEPT_AI_DIAGNOSTIC, "managed-5")
                .setAdmissionId(admission.getAdmissionId())
                .setDiagnosticId(proposed.getDiagnosticId())
                .setExpectedVersion(proposed.getDiagnosticVersion())
                .setActorPrincipalId("principal-reviewer")
                .setReviewNote("人工接受 AI 建议"));
        MerchantCommandResult createdTask = harness.execute(command(MerchantOperation.CREATE_FACTORY_INSPECTION_TASK, "managed-6")
                .setAdmissionId(admission.getAdmissionId())
                .setExpectedVersion(accepted.getAdmissionVersion())
                .setReviewNote("创建验厂任务"));
        MerchantCommandResult claimed = harness.execute(command(MerchantOperation.CLAIM_FACTORY_INSPECTION_TASK, "managed-7")
                .setAdmissionId(admission.getAdmissionId())
                .setInspectionTaskId(createdTask.getInspectionTaskId())
                .setExpectedVersion(createdTask.getInspectionTaskVersion())
                .setActorPrincipalId("principal-inspector"));
        MerchantCommandResult scheduled = harness.execute(command(MerchantOperation.SCHEDULE_FACTORY_INSPECTION_TASK, "managed-8")
                .setAdmissionId(admission.getAdmissionId())
                .setInspectionTaskId(claimed.getInspectionTaskId())
                .setExpectedVersion(claimed.getInspectionTaskVersion())
                .setActorPrincipalId("principal-inspector")
                .setScheduledAt(Instant.parse("2026-08-03T10:00:00Z")));
        MerchantCommandResult inspected = harness.execute(command(MerchantOperation.SUBMIT_FACTORY_INSPECTION, "managed-9")
                .setAdmissionId(admission.getAdmissionId())
                .setInspectionTaskId(scheduled.getInspectionTaskId())
                .setExpectedVersion(scheduled.getInspectionTaskVersion())
                .setActorPrincipalId("principal-inspector")
                .setInspectionOutcomeEvidenceRef("evidence:inspection-outcome-1")
                .setReviewNote("现场检查完成"));
        MerchantCommandResult firstReviewed = harness.execute(command(MerchantOperation.SUBMIT_FACTORY_FIRST_REVIEW, "managed-10")
                .setAdmissionId(admission.getAdmissionId())
                .setInspectionTaskId(inspected.getInspectionTaskId())
                .setExpectedVersion(inspected.getInspectionTaskVersion())
                .setActorPrincipalId("principal-qa")
                .setInspectionOutcomeEvidenceRef("evidence:first-review-1")
                .setReviewNote("QA 初审通过"));
        MerchantCommandResult finalReviewed = harness.execute(command(MerchantOperation.SUBMIT_FACTORY_FINAL_REVIEW, "managed-11")
                .setAdmissionId(admission.getAdmissionId())
                .setInspectionTaskId(firstReviewed.getInspectionTaskId())
                .setExpectedVersion(firstReviewed.getInspectionTaskVersion())
                .setActorPrincipalId("principal-qa")
                .setInspectionOutcomeEvidenceRef("evidence:final-task-review-1")
                .setReviewNote("待终态确认"));
        MerchantCommandResult completed = harness.execute(command(MerchantOperation.COMPLETE_FACTORY_INSPECTION_TASK, "managed-12")
                .setAdmissionId(admission.getAdmissionId())
                .setInspectionTaskId(finalReviewed.getInspectionTaskId())
                .setExpectedVersion(finalReviewed.getInspectionTaskVersion())
                .setActorPrincipalId("principal-qa")
                .setInspectionOutcomeEvidenceRef("evidence:inspection-complete-1")
                .setReviewNote("验厂流程完成"));
        MerchantCommandResult approved = harness.execute(command(MerchantOperation.RECORD_MANAGED_FINAL_REVIEW, "managed-13")
                .setAdmissionId(admission.getAdmissionId())
                .setExpectedVersion(completed.getAdmissionVersion())
                .setActorPrincipalId("principal-final")
                .setReviewDecision("APPROVED")
                .setFinalReviewEvidenceRef("evidence:final-review-1")
                .setReviewNote("人工终审通过"));

        assertThat(approved.getAdmissionStatus()).isEqualTo("FINAL_APPROVED");
        assertThat(approved.getFinalReviewStatus()).isEqualTo("APPROVED");
        assertThat(approved.getReviewDecision()).isEqualTo("APPROVED");
        assertThat(approved.getInspectionTaskStatus()).isEqualTo("COMPLETED");
        assertThat(approved.getInspectionOutcomeEvidenceRef()).isEqualTo("evidence:inspection-complete-1");
        assertThat(approved.getRecommendationCode()).isEqualTo("FACTORY_INSPECTION_REQUIRED");
        assertThat(approved.getEvidenceItems()).singleElement().satisfies(item -> assertThat(item.getItemCode())
                .isEqualTo("BUSINESS_LICENSE"));
        assertThat(harness.events).extracting(AppendDomainEventCommand::getEventType).contains(
                "merchant.managed_admission.status_changed",
                "merchant.managed_evidence_package.status_changed",
                "merchant.ai_diagnostic.status_changed",
                "merchant.factory_inspection.status_changed",
                "merchant.managed_final_review.status_changed");
        verify(harness.listingUnpublishSagaCommandApi, never()).execute(any());
    }

    private static MerchantCommand command(MerchantOperation operation, String key) {
        return new MerchantCommand()
                .setOperation(operation)
                .setIdempotencyKey(key)
                .setRunId("managed-run")
                .setSourceSystem("CLOUDMOLD")
                .setTraceId("managed-trace")
                .setOccurredAt(Instant.parse("2026-08-02T09:30:00Z"));
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
        final Map<String, MerchantOnboardingApplicationDO> applications = new HashMap<>();
        final Map<String, MerchantManagedAdmissionDO> admissions = new LinkedHashMap<>();
        final Map<String, MerchantManagedEvidencePackageDO> evidencePackages = new LinkedHashMap<>();
        final Map<String, MerchantAiDiagnosticDO> diagnostics = new LinkedHashMap<>();
        final Map<String, MerchantFactoryInspectionTaskDO> tasks = new LinkedHashMap<>();
        final Map<String, MerchantManagedFinalReviewDO> finalReviews = new LinkedHashMap<>();
        final List<AppendDomainEventCommand> events = new ArrayList<>();

        Harness() {
            applications.put("app-1", new MerchantOnboardingApplicationDO()
                    .setApplicationId("app-1")
                    .setStatus("APPROVED")
                    .setMerchantId("merchant-1")
                    .setShopId("shop-1")
                    .setVersion(4L));
            stubOperations();
            when(mapper.selectApplicationForUpdate(7L, "app-1")).thenReturn(applications.get("app-1"));
            when(mapper.selectApplication(7L, "app-1")).thenReturn(applications.get("app-1"));
            when(mapper.selectManagedAdmissionByApplicationForUpdate(eq(7L), anyString()))
                    .thenAnswer(i -> selectAdmissionByApplication(i.getArgument(1)));
            when(mapper.selectManagedAdmissionForUpdate(eq(7L), anyString()))
                    .thenAnswer(i -> admissions.get(i.getArgument(1)));
            when(mapper.selectManagedAdmissionByApplication(eq(7L), anyString()))
                    .thenAnswer(i -> selectAdmissionByApplication(i.getArgument(1)));
            when(mapper.selectLatestManagedEvidencePackageForUpdate(eq(7L), anyString()))
                    .thenAnswer(i -> selectEvidenceByAdmission(i.getArgument(1)));
            when(mapper.selectLatestManagedEvidencePackage(eq(7L), anyString()))
                    .thenAnswer(i -> selectEvidenceByAdmission(i.getArgument(1)));
            when(mapper.selectAiDiagnosticForUpdate(eq(7L), anyString()))
                    .thenAnswer(i -> diagnostics.get(i.getArgument(1)));
            when(mapper.selectLatestAiDiagnostic(eq(7L), anyString()))
                    .thenAnswer(i -> selectDiagnosticByAdmission(i.getArgument(1)));
            when(mapper.selectFactoryInspectionTaskForUpdate(eq(7L), anyString()))
                    .thenAnswer(i -> tasks.get(i.getArgument(1)));
            when(mapper.selectLatestFactoryInspectionTask(eq(7L), anyString()))
                    .thenAnswer(i -> selectTaskByAdmission(i.getArgument(1)));
            when(mapper.selectManagedFinalReviewForUpdate(eq(7L), anyString()))
                    .thenAnswer(i -> finalReviews.get(i.getArgument(1)));
            when(mapper.selectLatestManagedFinalReview(eq(7L), anyString()))
                    .thenAnswer(i -> selectFinalReviewByAdmission(i.getArgument(1)));
            doAnswer(i -> {
                MerchantManagedAdmissionDO value = i.getArgument(0);
                admissions.put(value.getAdmissionId(), value);
                return 1;
            }).when(mapper).insertManagedAdmission(any());
            doAnswer(i -> {
                MerchantManagedEvidencePackageDO value = i.getArgument(0);
                evidencePackages.put(value.getEvidencePackageId(), value);
                return 1;
            }).when(mapper).insertManagedEvidencePackage(any());
            doAnswer(i -> {
                MerchantAiDiagnosticDO value = i.getArgument(0);
                diagnostics.put(value.getDiagnosticId(), value);
                return 1;
            }).when(mapper).insertAiDiagnostic(any());
            doAnswer(i -> {
                MerchantFactoryInspectionTaskDO value = i.getArgument(0);
                tasks.put(value.getInspectionTaskId(), value);
                return 1;
            }).when(mapper).insertFactoryInspectionTask(any());
            doAnswer(i -> {
                MerchantManagedFinalReviewDO value = i.getArgument(0);
                finalReviews.put(value.getFinalReviewId(), value);
                return 1;
            }).when(mapper).insertManagedFinalReview(any());
            when(mapper.transitionManagedAdmission(anyLong(), anyString(), anyLong(), anyString(), anyString(),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(i -> transitionAdmission(i.getArgument(1), i.getArgument(2), i.getArgument(3),
                            i.getArgument(4), i.getArgument(5), i.getArgument(6), i.getArgument(7),
                            i.getArgument(8), i.getArgument(9), i.getArgument(10), i.getArgument(11),
                            i.getArgument(12), i.getArgument(13)));
            when(mapper.transitionAiDiagnostic(anyLong(), anyString(), anyLong(), anyString(), anyString(), any(), any(), any()))
                    .thenAnswer(i -> transitionDiagnostic(i.getArgument(1), i.getArgument(2), i.getArgument(3),
                            i.getArgument(4), i.getArgument(5), i.getArgument(6)));
            when(mapper.transitionFactoryInspectionTask(anyLong(), anyString(), anyLong(), anyString(), anyString(),
                    any(), any(), any(), any(), any()))
                    .thenAnswer(i -> transitionTask(i.getArgument(1), i.getArgument(2), i.getArgument(3),
                            i.getArgument(4), i.getArgument(5), i.getArgument(6), i.getArgument(7), i.getArgument(8)));
            doAnswer(i -> {
                events.add(i.getArgument(0));
                return null;
            }).when(outboxAppender).append(any());
        }

        MerchantCommandResult execute(MerchantCommand command) {
            return service.execute(command);
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
            doAnswer(i -> 1).when(mapper).insertHistory(any());
        }

        private MerchantManagedAdmissionDO selectAdmissionByApplication(String applicationId) {
            return admissions.values().stream()
                    .filter(value -> applicationId.equals(value.getApplicationId()))
                    .findFirst()
                    .orElse(null);
        }

        private MerchantManagedEvidencePackageDO selectEvidenceByAdmission(String admissionId) {
            return evidencePackages.values().stream()
                    .filter(value -> admissionId.equals(value.getAdmissionId()))
                    .findFirst()
                    .orElse(null);
        }

        private MerchantAiDiagnosticDO selectDiagnosticByAdmission(String admissionId) {
            return diagnostics.values().stream()
                    .filter(value -> admissionId.equals(value.getAdmissionId()))
                    .findFirst()
                    .orElse(null);
        }

        private MerchantFactoryInspectionTaskDO selectTaskByAdmission(String admissionId) {
            return tasks.values().stream()
                    .filter(value -> admissionId.equals(value.getAdmissionId()))
                    .findFirst()
                    .orElse(null);
        }

        private MerchantManagedFinalReviewDO selectFinalReviewByAdmission(String admissionId) {
            return finalReviews.values().stream()
                    .filter(value -> admissionId.equals(value.getAdmissionId()))
                    .findFirst()
                    .orElse(null);
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
                    .setVersion(value.getVersion() + 1)
                    .setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            return 1;
        }

        private int transitionDiagnostic(String diagnosticId, Long expectedVersion, String before, String after,
                                         String reviewerPrincipalId, String reviewNote) {
            MerchantAiDiagnosticDO value = diagnostics.get(diagnosticId);
            if (value == null || !expectedVersion.equals(value.getVersion()) || !before.equals(value.getStatus())) {
                return 0;
            }
            value.setStatus(after)
                    .setReviewerPrincipalId(reviewerPrincipalId)
                    .setReviewNote(reviewNote)
                    .setVersion(value.getVersion() + 1)
                    .setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            return 1;
        }

        private int transitionTask(String inspectionTaskId, Long expectedVersion, String before, String after,
                                   String actorPrincipalId, LocalDateTime scheduledAt, String evidenceRef, String note) {
            MerchantFactoryInspectionTaskDO value = tasks.get(inspectionTaskId);
            if (value == null || !expectedVersion.equals(value.getVersion()) || !before.equals(value.getStatus())) {
                return 0;
            }
            value.setStatus(after)
                    .setActorPrincipalId(actorPrincipalId)
                    .setScheduledAt(scheduledAt)
                    .setEvidenceRef(evidenceRef)
                    .setNote(note)
                    .setVersion(value.getVersion() + 1)
                    .setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            return 1;
        }
    }
}
