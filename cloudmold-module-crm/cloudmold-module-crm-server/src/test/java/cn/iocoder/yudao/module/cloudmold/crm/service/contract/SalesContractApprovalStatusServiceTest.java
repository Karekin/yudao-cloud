package cn.iocoder.yudao.module.cloudmold.crm.service.contract;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject.contract.SalesContractRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject.contract.SalesContractRecords.SalesContract;
import cn.iocoder.yudao.module.cloudmold.crm.dal.mysql.contract.SalesContractMapper;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SalesContractApprovalStatusServiceTest {

    private static final long TENANT_ID = 162L;
    private final SalesContractMapper mapper = mock(SalesContractMapper.class);
    private final SalesContractActorPrincipalPort actorPrincipalPort = mock(SalesContractActorPrincipalPort.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final SalesContractApprovalStatusService service =
            new SalesContractApprovalStatusService(mapper, actorPrincipalPort, outboxAppender);
    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> attemptToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(mapper.insertOrResolveOperation(eq(TENANT_ID), anyString(), eq("COMPLETE_APPROVAL"),
                anyString(), anyString(), any())).thenAnswer(invocation -> {
            requestHash.set(invocation.getArgument(3));
            attemptToken.set(invocation.getArgument(4));
            return 1;
        });
        when(mapper.selectLastInsertId()).thenReturn(801L);
        when(mapper.selectOperationForUpdate(TENANT_ID, 801L)).thenAnswer(invocation -> new Operation()
                .setOperationId(801L)
                .setTenantId(TENANT_ID)
                .setRequestHash(requestHash.get())
                .setAttemptToken(attemptToken.get())
                .setStatus(0));
        when(actorPrincipalPort.resolveSystemAdmin(9L)).thenReturn("principal-approver");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void approvalActivatesContractAndEmitsCanonicalEvent() {
        SalesContract contract = contract();
        when(mapper.selectSalesContractForUpdate(TENANT_ID, "contract-1")).thenReturn(contract);
        when(mapper.completeApproval(eq(TENANT_ID), eq("contract-1"), eq("process-1"),
                eq(3L), eq(4L), eq("ACTIVE"), eq("principal-approver"), any())).thenReturn(1);
        when(mapper.insertStatusHistory(any())).thenReturn(1);
        when(mapper.markOperationSucceeded(eq(TENANT_ID), eq(801L), eq("sales_contract"),
                eq("contract-1"), anyString(), any())).thenReturn(1);

        service.complete(event(BpmProcessInstanceStatusEnum.APPROVE.getStatus(), 9L));

        verify(actorPrincipalPort).requireActive("principal-approver");
        ArgumentCaptor<AppendDomainEventCommand> eventCaptor = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender).append(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("crm.sales_contract.status_changed");
        assertThat(eventCaptor.getValue().getPayload())
                .containsEntry("previous_status", "PENDING_APPROVAL")
                .containsEntry("status", "ACTIVE")
                .containsEntry("customer_id", "customer-1");
    }

    @Test
    void missingTerminalOperatorFailsClosedBeforeMutation() {
        assertThatThrownBy(() -> service.complete(event(
                BpmProcessInstanceStatusEnum.APPROVE.getStatus(), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operator is required");

        verifyNoInteractions(mapper, actorPrincipalPort, outboxAppender);
    }

    private static SalesContract contract() {
        return new SalesContract()
                .setSalesContractId("contract-1")
                .setTenantId(TENANT_ID)
                .setContractCode("SC-001")
                .setContractName("Sales Contract")
                .setCustomerId("customer-1")
                .setSellerMerchantId("merchant-1")
                .setSellerShopId("shop-1")
                .setSellerLegalEntityId("legal-1")
                .setStatus("PENDING_APPROVAL")
                .setCurrencyCode("CNY")
                .setTotalAmountMinor(3500L)
                .setEffectiveDate(LocalDate.of(2026, 8, 1))
                .setApprovalProcessInstanceId("process-1")
                .setVersion(3L);
    }

    private static BpmProcessInstanceStatusEvent event(Integer status, Long operatorId) {
        BpmProcessInstanceStatusEvent event = new BpmProcessInstanceStatusEvent();
        event.setId("process-1");
        event.setProcessDefinitionKey("cloudmold_sales_contract_approval");
        event.setBusinessKey("contract-1");
        event.setStatus(status);
        event.setTerminalOperatorUserId(operatorId);
        event.setTerminalTaskId("task-1");
        return event;
    }
}
