package cn.iocoder.yudao.module.cloudmold.warehouse.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3Command;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3CommandApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3CommandResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3Operation;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证切片 C 核心：COMPLETE_RECEIPT 每行同事务调库存 RECEIVE（NON_SELLABLE/PENDING_QC），
 * 短溢量对账（short=max(expected-received,0)），receipt→COMPLETED、asn→RECEIVED。
 */
class InboundCommandServiceImplTest {

    private static final String OWNER = "10000000-0000-4000-8000-000000000001";
    private static final String SKU = "10000000-0000-4000-8000-000000000002";
    private static final String WAREHOUSE = "10000000-0000-4000-8000-000000000003";
    private static final String LOCATION = "10000000-0000-4000-8000-000000000004";
    private static final String TARGET_LOCATION = "10000000-0000-4000-8000-000000000005";
    private static final String ASN_ID = "10000000-0000-4000-8000-000000000010";
    private static final String ASN_LINE_ID = "10000000-0000-4000-8000-000000000011";
    private static final String CORRELATION = "10000000-0000-4000-8000-000000000007";
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-29T01:00:00Z");

    private final InboundOperationMapper operationMapper = mock(InboundOperationMapper.class);
    private final AsnMapper asnMapper = mock(AsnMapper.class);
    private final AsnLineMapper asnLineMapper = mock(AsnLineMapper.class);
    private final ReceiptMapper receiptMapper = mock(ReceiptMapper.class);
    private final ReceiptLineMapper receiptLineMapper = mock(ReceiptLineMapper.class);
    private final PutawayMapper putawayMapper = mock(PutawayMapper.class);
    private final InventoryV3CommandApi inventoryV3CommandApi = mock(InventoryV3CommandApi.class);
    private final WarehouseReferenceValidationApi warehouseValidationApi = mock(WarehouseReferenceValidationApi.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);

    private final InboundCommandServiceImpl service = new InboundCommandServiceImpl(operationMapper, asnMapper,
            asnLineMapper, receiptMapper, receiptLineMapper, putawayMapper, inventoryV3CommandApi,
            warehouseValidationApi, outboxAppender);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldCompleteReceiptWithInventoryReceiveAndShortOverReconciliation() {
        prepareNewOperation("COMPLETE_RECEIPT");
        // ASN 已在途，version=1
        AsnDO asn = new AsnDO().setAsnId(ASN_ID).setTenantId(1L).setAsnNo("ASN-1")
                .setSourceBusinessType("REPLENISHMENT").setSourceBusinessRef("REC-1")
                .setSupplierRef("supplier-1")
                .setWarehouseId(WAREHOUSE).setStatus("IN_TRANSIT").setVersion(1L);
        when(asnMapper.selectForUpdate(1L, ASN_ID)).thenReturn(asn);
        // ASN 行预期量 10，收 7 → short=3
        AsnLineDO asnLine = new AsnLineDO().setAsnLineId(ASN_LINE_ID).setTenantId(1L).setAsnId(ASN_ID).setLineNo(1)
                .setCanonicalSkuId(SKU).setOwnerType("MERCHANT").setOwnerId(OWNER).setBaseUomCode("PIECE")
                .setExpectedQuantity(new BigDecimal("10.000000")).setStagingLocationId(LOCATION);
        when(asnLineMapper.selectByAsn(1L, ASN_ID)).thenReturn(List.of(asnLine));
        when(receiptMapper.insert(any(ReceiptDO.class))).thenReturn(1);
        when(receiptMapper.updateStatusCas(eq(1L), anyString(), eq(1L), eq("COMPLETED"), any())).thenReturn(1);
        when(asnMapper.updateStatusCas(eq(1L), eq(ASN_ID), eq(1L), eq("RECEIVED"), any())).thenReturn(1);
        when(receiptLineMapper.insert(any(ReceiptLineDO.class))).thenReturn(1);
        when(receiptLineMapper.updateInventoryWriteback(eq(1L), anyString(), anyString(), anyLong(), anyLong(), anyString()))
                .thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event", "a".repeat(64), false));
        // 库存 RECEIVE 返回（PENDING_QC 暂存余额）
        when(inventoryV3CommandApi.execute(any(InventoryV3Command.class))).thenReturn(
                InventoryV3CommandResult.builder().operationId(201L).ledgerTransactionId(301L)
                        .balanceId("BAL-1").onHandQuantity(new BigDecimal("7.000000")).duplicate(false).build());

        InboundCommand command = InboundCommand.builder().operation(InboundOperation.COMPLETE_RECEIPT)
                .idempotencyKey("inbound-receipt-key").correlationId(CORRELATION).occurredAt(OCCURRED_AT)
                .asn(AsnDefinition.builder().asnId(ASN_ID).expectedVersion(1L).build())
                .receiptLines(List.of(ReceiptLineDefinition.builder().asnLineId(ASN_LINE_ID).canonicalSkuId(SKU)
                        .ownerType("MERCHANT").ownerId(OWNER).baseUomCode("PIECE")
                        .receivedQuantity(new BigDecimal("7.000000")).stagingLocationId(LOCATION).qcRequired(true).build()))
                .build();

        InboundCommandResult result = service.execute(command);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getReceiptId()).isNotBlank();
        assertThat(result.getAsnId()).isEqualTo(ASN_ID);
        // 断言库存被以 RECEIVE + NON_SELLABLE/PENDING_QC + 收货量 7 调用
        ArgumentCaptor<InventoryV3Command> invCaptor = ArgumentCaptor.forClass(InventoryV3Command.class);
        verify(inventoryV3CommandApi).execute(invCaptor.capture());
        InventoryV3Command invCmd = invCaptor.getValue();
        assertThat(invCmd.getOperation()).isEqualTo(InventoryV3Operation.RECEIVE);
        assertThat(invCmd.getStockStatus()).isEqualTo("NON_SELLABLE");
        assertThat(invCmd.getQualityStatus()).isEqualTo("PENDING_QC");
        assertThat(invCmd.getQuantity()).isEqualByComparingTo("7.000000");
        assertThat(invCmd.getLocationId()).isEqualTo(LOCATION);
        // 断言短溢对账：short=3、over=0
        ArgumentCaptor<ReceiptLineDO> rlCaptor = ArgumentCaptor.forClass(ReceiptLineDO.class);
        verify(receiptLineMapper).insert(rlCaptor.capture());
        ReceiptLineDO rl = rlCaptor.getValue();
        assertThat(rl.getShortQuantity()).isEqualByComparingTo("3.000000");
        assertThat(rl.getOverQuantity()).isEqualByComparingTo("0.000000");
        assertThat(rl.getInventoryBalanceId()).isEqualTo("BAL-1");
        assertThat(rl.getInventoryOperationId()).isEqualTo(201L);
        // 事件 inbound.receipt.completed
        verify(outboxAppender).append(argThat(event -> "inbound.receipt.completed".equals(event.getEventType())
                && "inbound_receipt".equals(event.getAggregateType())
                && "REPLENISHMENT".equals(event.getPayload().get("source_business_type"))
                && "REC-1".equals(event.getPayload().get("source_business_ref"))
                && "supplier-1".equals(event.getPayload().get("supplier_ref"))));
    }

    @Test
    void shouldCreateAsnAndAppendCreatedEvent() {
        prepareNewOperation("CREATE_ASN");
        when(asnMapper.selectBySourceRef(1L, "REPLENISHMENT", "REC-1")).thenReturn(null);
        when(asnMapper.insert(any(AsnDO.class))).thenReturn(1);
        when(asnLineMapper.insert(any(AsnLineDO.class))).thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event", "a".repeat(64), false));

        InboundCommand command = InboundCommand.builder().operation(InboundOperation.CREATE_ASN)
                .idempotencyKey("inbound-asn-key").correlationId(CORRELATION).occurredAt(OCCURRED_AT)
                .asn(AsnDefinition.builder().asnNo("ASN-1").sourceBusinessType("REPLENISHMENT")
                        .sourceBusinessRef("REC-1").warehouseId(WAREHOUSE)
                        .lines(List.of(AsnLineDefinition.builder().canonicalSkuId(SKU).ownerType("MERCHANT")
                                .ownerId(OWNER).baseUomCode("PIECE").expectedQuantity(new BigDecimal("10.000000"))
                                .stagingLocationId(LOCATION).build())).build())
                .build();

        InboundCommandResult result = service.execute(command);

        assertThat(result.getStatus()).isEqualTo("DRAFT");
        assertThat(result.getAsnId()).isNotBlank();
        verify(warehouseValidationApi).requireActiveLocation(WAREHOUSE, LOCATION);
        verify(outboxAppender).append(argThat(event -> "inbound.asn.created".equals(event.getEventType())));
    }

    @Test
    void shouldCompletePutawayOnlyAfterInventoryIsReleasedIntoTargetLocation() {
        prepareNewOperation("COMPLETE_PUTAWAY");
        String receiptId = "10000000-0000-4000-8000-000000000020";
        String receiptLineId = "10000000-0000-4000-8000-000000000021";
        String putawayId = "10000000-0000-4000-8000-000000000022";
        ReceiptDO receipt = new ReceiptDO().setReceiptId(receiptId).setTenantId(1L).setReceiptNo("RCV-ASN-1")
                .setWarehouseId(WAREHOUSE).setStatus("COMPLETED").setVersion(2L);
        ReceiptLineDO line = new ReceiptLineDO().setReceiptLineId(receiptLineId).setTenantId(1L)
                .setReceiptId(receiptId).setCanonicalSkuId(SKU).setOwnerType("MERCHANT").setOwnerId(OWNER)
                .setBaseUomCode("PIECE").setReceivedQuantity(new BigDecimal("7.000000"))
                .setStagingLocationId(LOCATION).setQcRequired(0);
        when(receiptMapper.selectForUpdate(1L, receiptId)).thenReturn(receipt);
        when(receiptLineMapper.selectByReceipt(1L, receiptId)).thenReturn(List.of(line));
        when(putawayMapper.selectByReceipt(1L, receiptId)).thenReturn(null);
        when(putawayMapper.insert((PutawayDO) any())).thenReturn(1);
        when(inventoryV3CommandApi.execute(any())).thenReturn(InventoryV3CommandResult.builder()
                .operationId(202L).ledgerTransactionId(302L).balanceId("BAL-SOURCE")
                .onHandQuantity(BigDecimal.ZERO).build());
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event", "a".repeat(64), false));

        InboundCommand resultCommand = InboundCommand.builder().operation(InboundOperation.COMPLETE_PUTAWAY)
                .idempotencyKey("inbound-putaway-key").correlationId(CORRELATION).occurredAt(OCCURRED_AT)
                .putaway(PutawayDefinition.builder().putawayId(putawayId).receiptId(receiptId)
                        .targetLocationId(TARGET_LOCATION).build())
                .build();

        InboundCommandResult result = service.execute(resultCommand);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getPutawayId()).isEqualTo(putawayId);
        ArgumentCaptor<InventoryV3Command> invCaptor = ArgumentCaptor.forClass(InventoryV3Command.class);
        verify(inventoryV3CommandApi).execute(invCaptor.capture());
        InventoryV3Command inventory = invCaptor.getValue();
        assertThat(inventory.getOperation()).isEqualTo(InventoryV3Operation.QUALITY_RELEASE);
        assertThat(inventory.getLocationId()).isEqualTo(LOCATION);
        assertThat(inventory.getTargetLocationId()).isEqualTo(TARGET_LOCATION);
        assertThat(inventory.getStockStatus()).isEqualTo("NON_SELLABLE");
        assertThat(inventory.getQualityStatus()).isEqualTo("PENDING_QC");
        assertThat(inventory.getTargetStockStatus()).isEqualTo("SELLABLE");
        assertThat(inventory.getTargetQualityStatus()).isEqualTo("QUALIFIED");
        assertThat(inventory.getQuantity()).isEqualByComparingTo("7.000000");
        verify(putawayMapper).insert((PutawayDO) argThat((PutawayDO row) -> "COMPLETED".equals(row.getStatus())
                && TARGET_LOCATION.equals(row.getTargetLocationId())));
        verify(outboxAppender).append(argThat(event -> "inbound.putaway.completed".equals(event.getEventType())
                && Boolean.TRUE.equals(event.getPayload().get("inventory_terminal"))
                && Integer.valueOf(1).equals(event.getPayload().get("quality_released_line_count"))));
    }

    @Test
    void shouldFailClosedWhenQcRequiredLineHasNoQualityTask() {
        prepareNewOperation("COMPLETE_PUTAWAY");
        String receiptId = "10000000-0000-4000-8000-000000000030";
        ReceiptDO receipt = new ReceiptDO().setReceiptId(receiptId).setTenantId(1L).setReceiptNo("RCV-ASN-QC")
                .setWarehouseId(WAREHOUSE).setStatus("COMPLETED").setVersion(2L);
        ReceiptLineDO line = new ReceiptLineDO().setReceiptLineId("10000000-0000-4000-8000-000000000031")
                .setTenantId(1L).setReceiptId(receiptId).setCanonicalSkuId(SKU).setOwnerType("MERCHANT")
                .setOwnerId(OWNER).setBaseUomCode("PIECE").setReceivedQuantity(BigDecimal.ONE)
                .setStagingLocationId(LOCATION).setQcRequired(1);
        when(receiptMapper.selectForUpdate(1L, receiptId)).thenReturn(receipt);
        when(receiptLineMapper.selectByReceipt(1L, receiptId)).thenReturn(List.of(line));
        when(putawayMapper.selectByReceipt(1L, receiptId)).thenReturn(null);

        InboundCommand command = InboundCommand.builder().operation(InboundOperation.COMPLETE_PUTAWAY)
                .idempotencyKey("inbound-putaway-qc-key").correlationId(CORRELATION).occurredAt(OCCURRED_AT)
                .putaway(PutawayDefinition.builder().receiptId(receiptId).targetLocationId(TARGET_LOCATION).build())
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.execute(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completed quality task");
        verifyNoInteractions(inventoryV3CommandApi);
        verify(putawayMapper, never()).insert((PutawayDO) any());
    }

    private void prepareNewOperation(String type) {
        AtomicReference<String> attemptToken = new AtomicReference<>();
        when(operationMapper.insertOrResolve(eq(1L), anyString(), isNull(), eq(type), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    attemptToken.set(invocation.getArgument(5));
                    return 1;
                });
        when(operationMapper.selectLastInsertId()).thenReturn(101L);
        when(operationMapper.selectForUpdate(101L, 1L)).thenAnswer(ignored -> new InboundOperationDO()
                .setOperationId(101L).setTenantId(1L).setAttemptToken(attemptToken.get()).setStatus(0));
        when(operationMapper.markSucceeded(eq(101L), eq(1L), anyString(), anyString(), any())).thenReturn(1);
    }
}
