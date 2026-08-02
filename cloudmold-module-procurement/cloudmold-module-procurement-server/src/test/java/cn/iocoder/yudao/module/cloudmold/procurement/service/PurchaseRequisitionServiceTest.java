package cn.iocoder.yudao.module.cloudmold.procurement.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.procurement.api.PurchaseRequisitionCommand;
import cn.iocoder.yudao.module.cloudmold.procurement.api.PurchaseRequisitionResult;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseRequisitionStatusHistory;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementMapper;
import cn.iocoder.yudao.module.cloudmold.procurement.service.actor.ProcurementActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.procurement.service.reference.ProcurementReferenceValidationPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PurchaseRequisitionServiceTest {
    private static final String ACTOR = "principal-planner-01";
    private final ProcurementMapper mapper = mock(ProcurementMapper.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final ProcurementActorPrincipalPort actorPrincipalPort = mock(ProcurementActorPrincipalPort.class);
    private final ProcurementReferenceValidationPort referenceValidationPort =
            mock(ProcurementReferenceValidationPort.class);
    private final PurchaseRequisitionService service = new PurchaseRequisitionService(
            mapper, outbox, actorPrincipalPort, referenceValidationPort);
    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> attemptToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(31L);
        when(mapper.insertOrResolveOperation(eq(31L), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenReturn(401L);
        when(mapper.selectOperationForUpdate(401L, 31L)).thenAnswer(invocation ->
                new Operation().setOperationId(401L).setTenantId(31L)
                        .setRequestHash(requestHash.get()).setAttemptToken(attemptToken.get()).setStatus(0));
        when(mapper.insertPurchaseRequisition(any())).thenReturn(1);
        when(mapper.insertPurchaseRequisitionLines(anyList()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(mapper.insertPurchaseRequisitionSchedules(anyList()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(mapper.insertPurchaseRequisitionStatusHistory(any(PurchaseRequisitionStatusHistory.class)))
                .thenReturn(1);
        when(mapper.markOperationSucceeded(eq(401L), eq(31L), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsSupplierNeutralApprovedRequisitionWithCanonicalLinesAndSchedules() {
        PurchaseRequisitionResult result = service.createApproved(command(), ACTOR);

        assertThat(result.getAggregateType()).isEqualTo("purchase_requisition");
        assertThat(result.getStatus()).isEqualTo("APPROVED");
        verify(actorPrincipalPort).requireActive(ACTOR);
        verify(referenceValidationPort).requireActiveSku("sku-01");
        verify(referenceValidationPort).requireActiveWarehouse("warehouse-01");
        verify(mapper).insertPurchaseRequisition(argThat(header ->
                header.getStatus().equals("APPROVED")
                        && header.getSourceBusinessType().equals("REPLENISHMENT")
                        && header.getSourceBusinessRef().equals("recommendation-01")));
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("procurement.purchase_requisition.approved")
                        && event.getAggregateType().equals("purchase_requisition")
                        && event.getAggregateId().equals("purchase-requisition:conversion-01")));
    }

    private static PurchaseRequisitionCommand command() {
        return PurchaseRequisitionCommand.builder()
                .idempotencyKey("replenishment-purchase-requisition:conversion-01")
                .runId("run-01")
                .correlationId("47f33ac9-6144-4b85-a710-377583e948b4")
                .occurredAt(Instant.parse("2026-08-02T02:00:00Z"))
                .requisitionId("purchase-requisition:conversion-01")
                .requisitionCode("PR-CM-CONVERSION01")
                .sourceBusinessType("REPLENISHMENT")
                .sourceBusinessRef("recommendation-01")
                .reasonCode("REPLENISHMENT_APPROVED")
                .lines(List.of(PurchaseRequisitionCommand.LineDefinition.builder()
                        .lineId("purchase-requisition:conversion-01:line:10")
                        .lineNumber(10)
                        .canonicalSkuId("sku-01")
                        .requestedQuantity(new BigDecimal("12.500000"))
                        .uomCode("PCS")
                        .schedules(List.of(PurchaseRequisitionCommand.DeliveryScheduleDefinition.builder()
                                .scheduleId("purchase-requisition:conversion-01:line:10:schedule:1")
                                .scheduleNumber(1)
                                .canonicalWarehouseId("warehouse-01")
                                .requiredDeliveryDate(LocalDate.of(2026, 8, 15))
                                .scheduledQuantity(new BigDecimal("12.500000"))
                                .build()))
                        .build()))
                .build();
    }
}
