package cn.iocoder.yudao.module.cloudmold.finance.service.receivables;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.FinanceCommandEnvelope;
import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivableSourceValidationPort;
import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivablesCommandResult;
import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivablesCommands;
import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivableSourceView;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.receivables.ReceiptDO;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.receivables.ReceivablePlanDO;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.receivables.ReceivablesOperationDO;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.receivables.ReceivablesMapper;
import cn.iocoder.yudao.module.cloudmold.finance.service.actor.FinanceActorPrincipalPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReceivablesCommandServiceImplTest {
    private static final long TENANT_ID = 162L;
    private static final String ACTOR = "principal-1";

    private final ReceivablesMapper mapper = mock(ReceivablesMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final FinanceActorPrincipalPort actorPrincipalPort = mock(FinanceActorPrincipalPort.class);
    private final ReceivableSourceValidationPort sourceValidationPort = mock(ReceivableSourceValidationPort.class);
    private final ReceivablesCommandServiceImpl service = new ReceivablesCommandServiceImpl(
            mapper, outboxAppender, actorPrincipalPort, sourceValidationPort);
    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> attemptToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(mapper.insertOrResolveOperation(eq(TENANT_ID), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenReturn(701L);
        when(mapper.selectOperationForUpdate(TENANT_ID, 701L)).thenAnswer(invocation -> {
            ReceivablesOperationDO operation = new ReceivablesOperationDO();
            operation.setOperationId(701L);
            operation.setTenantId(TENANT_ID);
            operation.setRequestHash(requestHash.get());
            operation.setAttemptToken(attemptToken.get());
            operation.setStatus(0);
            return operation;
        });
        when(mapper.markOperationSucceeded(eq(TENANT_ID), eq(701L), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(sourceValidationPort.requireActiveSalesReceivableSource(eq(TENANT_ID), anyString(), anyString()))
                .thenReturn(ReceivableSourceView.builder().customerId("customer-1").salesContractId("contract-1")
                        .currencyCode("CNY").contractAmountMinor(100000L).build());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void registerPlanPersistsOpenPlanAndEmitsOutbox() {
        when(mapper.insertPlan(any())).thenReturn(1);

        ReceivablesCommandResult result = service.registerPlan(ReceivablesCommands.RegisterPlan.builder()
                .envelope(envelope("register-plan"))
                .receivablePlanId("plan-1")
                .planCode("PLAN-001")
                .customerId("customer-1")
                .salesContractId("contract-1")
                .plannedAmountMinor(10000L)
                .currencyCode("CNY")
                .dueDate(LocalDate.of(2026, 8, 31))
                .reasonCode("INITIAL")
                .build(), ACTOR);

        assertThat(result.getReceivablePlanId()).isEqualTo("plan-1");
        assertThat(result.getStatus()).isEqualTo("OPEN");
        verify(sourceValidationPort).requireActiveSalesReceivableSource(TENANT_ID, "customer-1", "contract-1");
        verify(actorPrincipalPort).requireActive(ACTOR);
        ArgumentCaptor<AppendDomainEventCommand> eventCaptor = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender).append(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("finance.receivable_plan.registered");
        assertThat(eventCaptor.getValue().getPayload()).containsEntry("planned_amount_minor", 10000L);
    }

    @Test
    void automationCommandResolvesVerifiedRpcActor() {
        when(actorPrincipalPort.resolveSystemAdmin(228L)).thenReturn(ACTOR);
        when(mapper.insertPlan(any())).thenReturn(1);
        ReceivablesCommands.RegisterPlan command = ReceivablesCommands.RegisterPlan.builder()
                .envelope(envelope("automation-register-plan"))
                .receivablePlanId("plan-ai-1")
                .planCode("PLAN-AI-001")
                .customerId("customer-1")
                .salesContractId("contract-1")
                .plannedAmountMinor(10000L)
                .currencyCode("CNY")
                .dueDate(LocalDate.of(2026, 8, 31))
                .reasonCode("AI_SETTLEMENT")
                .build();

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(228L);

            ReceivablesCommandResult result = service.registerPlan(command);

            assertThat(result.getStatus()).isEqualTo("OPEN");
            verify(actorPrincipalPort).resolveSystemAdmin(228L);
            verify(actorPrincipalPort).requireActive(ACTOR);
        }
    }

    @Test
    void recordReceiptPersistsReceiptAndEmitsOutbox() {
        when(mapper.insertReceipt(any())).thenReturn(1);
        when(sourceValidationPort.requireActiveSalesReceivableSource(TENANT_ID, "customer-1", "contract-1"))
                .thenReturn(ReceivableSourceView.builder().customerId("customer-1").salesContractId("contract-1")
                        .currencyCode("USD").contractAmountMinor(100000L).build());

        ReceivablesCommandResult result = service.recordReceipt(ReceivablesCommands.RecordReceipt.builder()
                .envelope(envelope("record-receipt"))
                .receiptId("receipt-1")
                .receiptCode("RCPT-001")
                .customerId("customer-1")
                .salesContractId("contract-1")
                .receiptAmountMinor(6000L)
                .currencyCode("USD")
                .receiptDate(LocalDate.of(2026, 8, 8))
                .externalReference("BANK-REF-1")
                .reasonCode("PAYMENT")
                .build(), ACTOR);

        assertThat(result.getReceiptId()).isEqualTo("receipt-1");
        assertThat(result.getStatus()).isEqualTo("RECORDED");
        verify(sourceValidationPort).requireActiveSalesReceivableSource(TENANT_ID, "customer-1", "contract-1");
        verify(outboxAppender).append(argThat(event -> event.getEventType().equals("finance.receipt.recorded")
                && event.getPayload().get("receipt_amount_minor").equals(6000L)));
    }

    @Test
    void allocateReceiptRejectsCurrencyMismatch() {
        ReceiptDO receipt = receipt("receipt-1", "customer-1", "contract-1", "CNY", 8000L, 0L, 2L);
        ReceivablePlanDO plan = plan("plan-1", "customer-1", "contract-1", "USD", 8000L, 0L, 3L);
        when(mapper.selectReceiptForUpdate(TENANT_ID, "receipt-1")).thenReturn(receipt);
        when(mapper.selectPlanForUpdate(TENANT_ID, "plan-1")).thenReturn(plan);

        assertThatThrownBy(() -> service.allocateReceipt(ReceivablesCommands.AllocateReceipt.builder()
                .envelope(envelope("allocate-mismatch"))
                .receiptId("receipt-1")
                .expectedVersion(2L)
                .allocations(List.of(ReceivablesCommands.AllocationLine.builder()
                        .receiptAllocationId("alloc-1")
                        .receivablePlanId("plan-1")
                        .expectedPlanVersion(3L)
                        .amountMinor(1000L)
                        .build()))
                .build(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void allocateReceiptUpdatesBalancesPersistsAllocationAndEmitsOutbox() {
        ReceiptDO receipt = receipt("receipt-1", "customer-1", "contract-1", "CNY", 8000L, 1000L, 2L);
        ReceivablePlanDO plan = plan("plan-1", "customer-1", "contract-1", "CNY", 5000L, 1000L, 3L);
        when(mapper.selectReceiptForUpdate(TENANT_ID, "receipt-1")).thenReturn(receipt);
        when(mapper.selectPlanForUpdate(TENANT_ID, "plan-1")).thenReturn(plan);
        when(mapper.allocatePlan(eq(TENANT_ID), eq("plan-1"), eq(3L), eq(3000L), eq("CNY"),
                eq("PARTIALLY_ALLOCATED"), eq(ACTOR), any(), any())).thenReturn(1);
        when(mapper.allocateReceipt(eq(TENANT_ID), eq("receipt-1"), eq(2L), eq(3000L), eq("CNY"),
                eq("PARTIALLY_ALLOCATED"), eq(ACTOR), any(), any())).thenReturn(1);
        when(mapper.insertAllocation(any())).thenReturn(1);

        ReceivablesCommandResult result = service.allocateReceipt(ReceivablesCommands.AllocateReceipt.builder()
                .envelope(envelope("allocate-receipt"))
                .receiptId("receipt-1")
                .expectedVersion(2L)
                .reasonCode("MATCHED")
                .allocations(List.of(ReceivablesCommands.AllocationLine.builder()
                        .receiptAllocationId("alloc-1")
                        .receivablePlanId("plan-1")
                        .expectedPlanVersion(3L)
                        .amountMinor(3000L)
                        .build()))
                .build(), ACTOR);

        assertThat(result.getAggregateVersion()).isEqualTo(3L);
        assertThat(result.getAllocatedAmountMinor()).isEqualTo(3000L);
        assertThat(result.getAllocationIds()).containsExactly("alloc-1");
        verify(outboxAppender).append(argThat(event -> event.getEventType().equals("finance.receipt.allocated")
                && event.getPayload().get("receipt_unallocated_amount_minor").equals(4000L)));
    }

    @Test
    void duplicateIdempotencyReplaysCachedResult() {
        when(mapper.selectOperationForUpdate(TENANT_ID, 701L)).thenAnswer(invocation -> {
            ReceivablesOperationDO existing = new ReceivablesOperationDO();
            existing.setOperationId(701L);
            existing.setTenantId(TENANT_ID);
            existing.setRequestHash(requestHash.get());
            existing.setAttemptToken("other-attempt");
            existing.setStatus(ReceivablesCommandServiceImpl.OPERATION_SUCCEEDED);
            existing.setResultJson("{\"operationId\":701,\"duplicate\":false,\"aggregateType\":\"finance_receivable_plan\","
                    + "\"aggregateId\":\"plan-1\",\"aggregateVersion\":1,\"status\":\"OPEN\",\"receivablePlanId\":\"plan-1\"}");
            return existing;
        });

        ReceivablesCommandResult replay = service.registerPlan(ReceivablesCommands.RegisterPlan.builder()
                .envelope(envelope("register-plan"))
                .receivablePlanId("plan-1")
                .planCode("PLAN-001")
                .customerId("customer-1")
                .salesContractId("contract-1")
                .plannedAmountMinor(10000L)
                .currencyCode("CNY")
                .dueDate(LocalDate.of(2026, 8, 31))
                .build(), ACTOR);

        assertThat(replay.getDuplicate()).isTrue();
        verify(mapper, never()).insertPlan(any());
    }

    private static FinanceCommandEnvelope envelope(String idempotencyKey) {
        return FinanceCommandEnvelope.builder()
                .idempotencyKey(idempotencyKey + "-12345678")
                .correlationId("corr-" + idempotencyKey)
                .causationId("cause-" + idempotencyKey)
                .runId("run-" + idempotencyKey)
                .occurredAt(Instant.parse("2026-08-08T12:00:00Z"))
                .build();
    }

    private static ReceiptDO receipt(String receiptId, String customerId, String salesContractId, String currencyCode,
                                     long receiptAmountMinor, long allocatedAmountMinor, long version) {
        ReceiptDO receipt = new ReceiptDO();
        receipt.setReceiptId(receiptId);
        receipt.setCustomerId(customerId);
        receipt.setSalesContractId(salesContractId);
        receipt.setCurrencyCode(currencyCode);
        receipt.setReceiptAmountMinor(receiptAmountMinor);
        receipt.setAllocatedAmountMinor(allocatedAmountMinor);
        receipt.setVersion(version);
        receipt.setStatus("RECORDED");
        receipt.setUpdatedAt(LocalDateTime.now());
        return receipt;
    }

    private static ReceivablePlanDO plan(String planId, String customerId, String salesContractId, String currencyCode,
                                         long plannedAmountMinor, long allocatedAmountMinor, long version) {
        ReceivablePlanDO plan = new ReceivablePlanDO();
        plan.setReceivablePlanId(planId);
        plan.setCustomerId(customerId);
        plan.setSalesContractId(salesContractId);
        plan.setCurrencyCode(currencyCode);
        plan.setPlannedAmountMinor(plannedAmountMinor);
        plan.setAllocatedAmountMinor(allocatedAmountMinor);
        plan.setVersion(version);
        plan.setStatus("OPEN");
        return plan;
    }
}
