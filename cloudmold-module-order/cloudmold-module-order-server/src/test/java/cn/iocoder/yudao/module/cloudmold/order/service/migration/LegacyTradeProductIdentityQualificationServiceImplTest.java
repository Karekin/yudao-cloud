package cn.iocoder.yudao.module.cloudmold.order.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.order.api.migration.LegacyTradeProductIdentityQualificationApi.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.migration.LegacyTradeProductIdentityQualificationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LegacyTradeProductIdentityQualificationServiceImplTest {

    private static final String SOURCE_RUN = "45000000-0000-4000-8000-000000000001";
    private static final String ITEM_EVIDENCE = "43000000-0000-4000-8000-000000000013";
    private static final String REQUEST = "4d000000-0000-4000-8000-000000000001";
    private static final String QUALIFICATION = "4e000000-0000-4000-8000-000000000001";
    private static final String CORRELATION = "4d000000-0000-4000-8000-000000000099";

    private final LegacyTradeProductIdentityQualificationMapper mapper =
            mock(LegacyTradeProductIdentityQualificationMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final LegacyTradeProductIdentityQualificationServiceImpl service =
            new LegacyTradeProductIdentityQualificationServiceImpl(mapper, outboxAppender);

    @BeforeEach
    void setUp() {
        reset(mapper, outboxAppender);
        TenantContextHolder.setTenantId(1L);
        doAnswer(invocation -> null).when(outboxAppender).append(any());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void requestFreezesExactEvidenceWithoutCreatingQualification() {
        when(mapper.selectSourceItem(1L, SOURCE_RUN, ITEM_EVIDENCE)).thenReturn(source());
        when(mapper.insertRequest(any())).thenReturn(1);

        QualificationRequestResult result = service.request(requestCommand(), 10L);

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getApprovalCount()).isZero();
        assertThat(result.getQualificationId()).isNull();
        assertThat(result.getQualificationStatus()).isEqualTo("NOT_APPLIED");
        ArgumentCaptor<LegacyTradeProductIdentityQualificationRequestDO> saved =
                ArgumentCaptor.forClass(LegacyTradeProductIdentityQualificationRequestDO.class);
        verify(mapper).insertRequest(saved.capture());
        assertThat(saved.getValue().getScopeHash()).matches("[0-9a-f]{64}");
        assertThat(saved.getValue().getRequesterId()).isEqualTo(10L);
        verify(mapper, never()).insertQualification(any());
        ArgumentCaptor<AppendDomainEventCommand> event = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender).append(event.capture());
        assertThat(event.getValue().getPayload()).containsEntry("approval_count", 0)
                .containsEntry("canonical_import_allowed", false);
    }

    @Test
    void requesterCannotApproveOwnQualification() {
        when(mapper.selectRequestForUpdate(1L, REQUEST)).thenReturn(pendingRequest(1L, 10L));

        assertThatThrownBy(() -> service.approve(REQUEST, approvalCommand("DATA_OWNER", 1L), 10L))
                .isInstanceOf(ServiceException.class).hasMessageContaining("requester");
        verify(mapper, never()).insertApproval(any());
    }

    @Test
    void rejectsCallerSuppliedSnapshotDigestThatDiffersFromServerCapture() {
        when(mapper.selectSourceItem(1L, SOURCE_RUN, ITEM_EVIDENCE)).thenReturn(
                source().setHistoricalProductSnapshotHash("e".repeat(64)));

        assertThatThrownBy(() -> service.request(requestCommand(), 10L))
                .isInstanceOf(ServiceException.class).hasMessageContaining("server-captured");
        verify(mapper, never()).insertRequest(any());
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void oneActorCannotSatisfyBothApprovalRoles() {
        LegacyTradeProductIdentityQualificationRequestDO request = pendingRequest(2L, 10L)
                .setStatus("PARTIALLY_APPROVED").setApprovalCount(1);
        when(mapper.selectRequestForUpdate(1L, REQUEST)).thenReturn(request);
        when(mapper.selectApprovalsForUpdate(1L, REQUEST)).thenReturn(List.of(approval("DATA_OWNER", 20L)));

        assertThatThrownBy(() -> service.approve(REQUEST, approvalCommand("CHANGE_MANAGER", 2L), 20L))
                .isInstanceOf(ServiceException.class).hasMessageContaining("both");
        verify(mapper, never()).insertApproval(any());
    }

    @Test
    void secondIndependentApprovalCreatesQualificationAtomically() {
        LegacyTradeProductIdentityQualificationRequestDO request = pendingRequest(2L, 10L)
                .setStatus("PARTIALLY_APPROVED").setApprovalCount(1);
        when(mapper.selectRequestForUpdate(1L, REQUEST)).thenReturn(request);
        when(mapper.selectApprovalsForUpdate(1L, REQUEST)).thenReturn(List.of(approval("DATA_OWNER", 20L)));
        when(mapper.selectSourceItem(1L, SOURCE_RUN, ITEM_EVIDENCE)).thenReturn(source());
        when(mapper.insertApproval(any())).thenReturn(1);
        when(mapper.insertQualification(any())).thenReturn(1);
        when(mapper.advanceRequest(eq(1L), eq(REQUEST), eq(2L), eq(2), eq("APPLIED"),
                anyString(), any(), any())).thenReturn(1);

        QualificationRequestResult result = service.approve(
                REQUEST, approvalCommand("CHANGE_MANAGER", 2L), 30L);

        assertThat(result.getStatus()).isEqualTo("APPLIED");
        assertThat(result.getApprovalCount()).isEqualTo(2);
        assertThat(result.getQualificationStatus()).isEqualTo("QUALIFIED");
        assertThat(result.getQualificationId()).isNotBlank();
        assertThat(result.getApprovals()).extracting(QualificationApprovalResult::getApproverId)
                .containsExactlyInAnyOrder(20L, 30L);
        ArgumentCaptor<LegacyTradeProductIdentityQualificationDO> qualification =
                ArgumentCaptor.forClass(LegacyTradeProductIdentityQualificationDO.class);
        verify(mapper).insertQualification(qualification.capture());
        assertThat(qualification.getValue().getApprovalSetHash()).matches("[0-9a-f]{64}");
        assertThat(qualification.getValue().getRequestId()).isEqualTo(REQUEST);
        verify(mapper, never()).revokeQualification(anyLong(), anyString(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void secondIndependentApprovalBindsRevocationProofAtomically() {
        LegacyTradeProductIdentityQualificationRequestDO request = pendingRequest(2L, 10L)
                .setActionType("REVOKE").setTargetQualificationId(QUALIFICATION)
                .setStatus("PARTIALLY_APPROVED").setApprovalCount(1);
        LegacyTradeProductIdentityQualificationDO target = new LegacyTradeProductIdentityQualificationDO()
                .setQualificationId(QUALIFICATION).setTenantId(1L)
                .setSourceMigrationRunId(SOURCE_RUN).setItemEvidenceId(ITEM_EVIDENCE)
                .setLegacyOrderItemId(111L).setHistoricalSpuId(633L).setHistoricalSkuId(1L)
                .setSourceItemEvidenceHash("a".repeat(64)).setHistoricalProductSnapshotHash("b".repeat(64))
                .setStatus("QUALIFIED").setVersion(1L);
        when(mapper.selectRequestForUpdate(1L, REQUEST)).thenReturn(request);
        when(mapper.selectApprovalsForUpdate(1L, REQUEST)).thenReturn(List.of(approval("DATA_OWNER", 20L)));
        when(mapper.selectSourceItem(1L, SOURCE_RUN, ITEM_EVIDENCE)).thenReturn(source());
        when(mapper.selectQualificationForUpdate(1L, QUALIFICATION)).thenReturn(target);
        when(mapper.insertApproval(any())).thenReturn(1);
        when(mapper.revokeQualification(eq(1L), eq(QUALIFICATION), eq(1L), eq(REQUEST),
                matches("[0-9a-f]{64}"), any())).thenReturn(1);
        when(mapper.advanceRequest(eq(1L), eq(REQUEST), eq(2L), eq(2), eq("APPLIED"),
                eq(QUALIFICATION), any(), any())).thenReturn(1);

        QualificationRequestResult result = service.approve(
                REQUEST, approvalCommand("CHANGE_MANAGER", 2L), 30L);

        assertThat(result.getQualificationStatus()).isEqualTo("REVOKED");
        assertThat(result.getQualificationId()).isEqualTo(QUALIFICATION);
        verify(mapper).revokeQualification(eq(1L), eq(QUALIFICATION), eq(1L), eq(REQUEST),
                matches("[0-9a-f]{64}"), any());
        verify(mapper, never()).insertQualification(any());
    }

    private static QualificationRequestCommand requestCommand() {
        return new QualificationRequestCommand().setIdempotencyKey("product-identity-qualify:111")
                .setActionType("QUALIFY").setSourceMigrationRunId(SOURCE_RUN)
                .setItemEvidenceId(ITEM_EVIDENCE).setHistoricalSpuId(633L).setHistoricalSkuId(1L)
                .setSourceItemEvidenceHash("a".repeat(64)).setHistoricalProductSnapshotHash("b".repeat(64))
                .setSourceEvidenceUri("evidence://restricted/product-snapshot/111")
                .setQualificationRef("review:product-history-111")
                .setCorrelationId(CORRELATION).setOccurredAt(Instant.parse("2026-07-17T05:00:00Z"));
    }

    private static QualificationApprovalCommand approvalCommand(String role, Long expectedVersion) {
        return new QualificationApprovalCommand()
                .setIdempotencyKey("product-identity-approval:" + role + ":" + expectedVersion)
                .setApprovalRole(role).setExpectedVersion(expectedVersion)
                .setEvidenceRef("review:" + role.toLowerCase())
                .setCorrelationId(CORRELATION).setOccurredAt(Instant.parse("2026-07-17T05:01:00Z"));
    }

    private static LegacyTradeProductIdentityQualificationSourceDO source() {
        return new LegacyTradeProductIdentityQualificationSourceDO().setTenantId(1L)
                .setSourceMigrationRunId(SOURCE_RUN).setPolicyVersion("legacy-trade-benefit-v5")
                .setItemEvidenceComplete(true).setProductSnapshotEvidenceComplete(true)
                .setItemEvidenceId(ITEM_EVIDENCE).setLegacyOrderItemId(111L)
                .setLegacySpuId(633L).setLegacySkuId(1L).setSourceItemEvidenceHash("a".repeat(64))
                .setHistoricalProductSnapshotHash("b".repeat(64)).setProductSnapshotStatus("CAPTURED")
                .setDeleted(false).setOrderDeleted(false);
    }

    private static LegacyTradeProductIdentityQualificationRequestDO pendingRequest(Long version, Long requesterId) {
        return new LegacyTradeProductIdentityQualificationRequestDO().setRequestId(REQUEST).setTenantId(1L)
                .setActionType("QUALIFY").setSourceMigrationRunId(SOURCE_RUN).setItemEvidenceId(ITEM_EVIDENCE)
                .setLegacyOrderItemId(111L).setHistoricalSpuId(633L).setHistoricalSkuId(1L)
                .setSourceItemEvidenceHash("a".repeat(64)).setHistoricalProductSnapshotHash("b".repeat(64))
                .setSourceEvidenceUri("evidence://restricted/product-snapshot/111")
                .setQualificationRef("review:product-history-111").setScopeHash("c".repeat(64))
                .setRequesterId(requesterId).setApprovalCount(0).setStatus("PENDING").setVersion(version);
    }

    private static LegacyTradeProductIdentityQualificationApprovalDO approval(String role, Long actor) {
        return new LegacyTradeProductIdentityQualificationApprovalDO()
                .setApprovalId("4d000000-0000-4000-8001-000000000001").setTenantId(1L).setRequestId(REQUEST)
                .setApprovalRole(role).setApproverId(actor).setScopeHash("c".repeat(64))
                .setExpectedRequestVersion(1L).setEvidenceRef("review:" + role.toLowerCase())
                .setRequestHash("d".repeat(64)).setStatus("APPROVED").setVersion(1L);
    }
}
