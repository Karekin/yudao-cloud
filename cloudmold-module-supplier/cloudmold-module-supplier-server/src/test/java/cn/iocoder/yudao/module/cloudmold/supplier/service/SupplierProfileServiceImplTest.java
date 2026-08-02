package cn.iocoder.yudao.module.cloudmold.supplier.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileCommand;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileOperation;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileResult;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierProfileRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierProfileRecords.Profile;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.mysql.SupplierProfileMapper;
import cn.iocoder.yudao.module.cloudmold.supplier.service.actor.SupplierActorPrincipalPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupplierProfileServiceImplTest {
    private static final String BUYER = "principal-buyer-01";
    private static final String REVIEWER = "principal-reviewer-01";
    private static final String SHA = "a".repeat(64);
    private final SupplierProfileMapper mapper = mock(SupplierProfileMapper.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final SupplierActorPrincipalPort actorPrincipalPort = mock(SupplierActorPrincipalPort.class);
    private final SupplierProfileServiceImpl service =
            new SupplierProfileServiceImpl(mapper, outbox, actorPrincipalPort);
    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> attemptToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(162L);
        when(mapper.insertOrResolveOperation(eq(162L), anyString(), anyString(), anyString(),
                anyString(), any())).thenAnswer(invocation -> {
            requestHash.set(invocation.getArgument(3));
            attemptToken.set(invocation.getArgument(4));
            return 1;
        });
        when(mapper.selectLastInsertId()).thenReturn(401L);
        when(mapper.selectOperationForUpdate(401L, 162L)).thenAnswer(invocation ->
                new Operation().setOperationId(401L).setTenantId(162L)
                        .setRequestHash(requestHash.get()).setAttemptToken(attemptToken.get()).setStatus(0));
        when(mapper.markOperationSucceeded(eq(401L), eq(162L), anyString(), anyString(), any()))
                .thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void registersCanonicalSupplierProfile() {
        when(mapper.insertProfile(any())).thenReturn(1);

        SupplierProfileResult result = service.execute(base(SupplierProfileOperation.REGISTER_SUPPLIER)
                .supplier(SupplierProfileCommand.SupplierDefinition.builder()
                        .supplierId("supplier-01").supplierCode("SUP-01")
                        .supplierName("Garment Factory One").countryCode("cn")
                        .capabilitySummary("Knits and cut-and-sew").riskLevel("low").build())
                .build(), BUYER);

        assertThat(result.getSupplierId()).isEqualTo("supplier-01");
        assertThat(result.getSupplierStatus()).isEqualTo("CANDIDATE");
        assertThat(result.getAdmissionStatus()).isEqualTo("DRAFT");
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("supplier.profile.registered")
                        && event.getAggregateType().equals("supplier_profile")));
    }

    @Test
    void submitsAdmissionWithImmutableEvidenceDigests() {
        Profile profile = draftProfile();
        when(mapper.selectProfileForUpdate(162L, "supplier-01")).thenReturn(profile);
        when(mapper.submitAdmission(eq(162L), eq("supplier-01"), eq(1L), eq(BUYER),
                eq(SHA), eq(SHA), eq(null), any())).thenReturn(1);

        SupplierProfileResult result = service.execute(base(SupplierProfileOperation.SUBMIT_ADMISSION)
                .admission(SupplierProfileCommand.AdmissionDefinition.builder()
                        .supplierId("supplier-01").expectedVersion(1L)
                        .qualificationEvidenceSha256(SHA).riskEvidenceSha256(SHA).build())
                .build(), BUYER);

        assertThat(result.getAggregateVersion()).isEqualTo(2L);
        assertThat(result.getAdmissionStatus()).isEqualTo("UNDER_REVIEW");
    }

    @Test
    void approvesAdmissionOnlyWithIndependentReviewer() {
        Profile profile = reviewProfile();
        when(mapper.selectProfileForUpdate(162L, "supplier-01")).thenReturn(profile);
        when(mapper.approveAdmission(eq(162L), eq("supplier-01"), eq(2L), eq(REVIEWER),
                eq(null), any())).thenReturn(1);

        SupplierProfileResult result = service.execute(base(SupplierProfileOperation.APPROVE_ADMISSION)
                .admission(SupplierProfileCommand.AdmissionDefinition.builder()
                        .supplierId("supplier-01").expectedVersion(2L).build())
                .build(), REVIEWER);

        assertThat(result.getSupplierStatus()).isEqualTo("ACTIVE");
        assertThat(result.getAdmissionStatus()).isEqualTo("ADMITTED");
        verify(outbox).append(argThat(event -> event.getEventType().equals("supplier.admission.approved")));
    }

    @Test
    void rejectsAdmissionApprovalBySubmitter() {
        when(mapper.selectProfileForUpdate(162L, "supplier-01")).thenReturn(reviewProfile());

        assertThatThrownBy(() -> service.execute(base(SupplierProfileOperation.APPROVE_ADMISSION)
                .admission(SupplierProfileCommand.AdmissionDefinition.builder()
                        .supplierId("supplier-01").expectedVersion(2L).build())
                .build(), BUYER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("independent reviewer");
        verify(mapper, never()).approveAdmission(any(), anyString(), any(), anyString(), any(), any());
    }

    private static Profile draftProfile() {
        return new Profile().setSupplierId("supplier-01").setSupplierCode("SUP-01")
                .setSupplierName("Garment Factory One").setStatus("CANDIDATE")
                .setAdmissionStatus("DRAFT").setVersion(1L);
    }

    private static Profile reviewProfile() {
        return draftProfile().setAdmissionStatus("UNDER_REVIEW").setVersion(2L)
                .setSubmittedByPrincipalId(BUYER).setQualificationEvidenceSha256(SHA)
                .setRiskEvidenceSha256(SHA);
    }

    private static SupplierProfileCommand.SupplierProfileCommandBuilder base(SupplierProfileOperation operation) {
        return SupplierProfileCommand.builder().operation(operation)
                .idempotencyKey("supplier-profile-" + operation).runId("run-001")
                .correlationId("11111111-1111-4111-8111-111111111111")
                .occurredAt(Instant.parse("2026-08-02T00:00:00Z"));
    }
}
