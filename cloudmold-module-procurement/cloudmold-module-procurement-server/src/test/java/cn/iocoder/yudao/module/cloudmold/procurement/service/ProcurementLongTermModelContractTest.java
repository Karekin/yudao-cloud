package cn.iocoder.yudao.module.cloudmold.procurement.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.P2pEvidenceIngestionApi;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementCommand;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementOperation;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementResult;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.OrderStatusHistory;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.ProcurementOrder;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementMapper;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementSourcingMapper;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementSourcingRecords.Award;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementSourcingRecords.AwardLine;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcurementLongTermModelContractTest {
    private static final String ACTOR = "principal-buyer-01";

    private final ProcurementMapper mapper = mock(ProcurementMapper.class);
    private final ProcurementSourcingMapper sourcingMapper = mock(ProcurementSourcingMapper.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final ProcurementActorPrincipalPort actorPrincipalPort = mock(ProcurementActorPrincipalPort.class);
    private final ProcurementReferenceValidationPort referenceValidationPort = mock(ProcurementReferenceValidationPort.class);
    private final P2pEvidenceIngestionApi p2pEvidence = mock(P2pEvidenceIngestionApi.class);
    private final ProcurementServiceImpl service = new ProcurementServiceImpl(
            mapper, sourcingMapper, outbox, actorPrincipalPort, referenceValidationPort, p2pEvidence);
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
        when(mapper.selectLastInsertId()).thenReturn(301L);
        when(mapper.selectOperationForUpdate(301L, 31L)).thenAnswer(invocation ->
                new Operation().setOperationId(301L).setTenantId(31L)
                        .setRequestHash(requestHash.get()).setAttemptToken(attemptToken.get()).setStatus(0));
        when(mapper.insertOrder(any())).thenReturn(1);
        when(mapper.insertItems(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(mapper.insertAwardSources(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(mapper.insertSchedules(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(mapper.insertStatusHistory(any(OrderStatusHistory.class))).thenReturn(1);
        when(mapper.markOperationSucceeded(eq(301L), eq(31L), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(sourcingMapper.selectApprovedAward(31L, "award-01")).thenReturn(new Award()
                .setAwardId("award-01").setTenantId(31L).setStatus("APPROVED").setVersion(1L));
        when(sourcingMapper.selectAwardLines(31L, "award-01")).thenReturn(List.of(
                awardLine("award-line-10", "sku-01", "12", 12000L, 1560L, 13560L),
                awardLine("award-line-20", "sku-02", "8", 8000L, 1040L, 9040L)));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsPurchaseOrderWithTwoCanonicalLinesAndHeaderTotals() {
        ProcurementResult result = service.execute(baseCreateCommand()
                .purchaseOrder(ProcurementCommand.PurchaseOrderDefinition.builder()
                        .orderCode("PO-CM-STD-001")
                        .sourceBusinessType("SOURCING_AWARD")
                        .sourceBusinessRef("award-01")
                        .legalEntityId("legal-entity-01")
                        .awardId("award-01").awardVersion(1L)
                        .supplierId("supplier-01")
                        .currencyCode("CNY")
                        .leadTimeDays(7)
                        .headerNetAmountMinor(20000L)
                        .headerTaxAmountMinor(2600L)
                        .headerGrossAmountMinor(22600L)
                        .lines(List.of(
                                ProcurementCommand.PurchaseOrderLineDefinition.builder()
                                        .lineNumber(10)
                                        .awardLineId("award-line-10")
                                        .canonicalSkuId("sku-01")
                                        .orderedQuantity(new BigDecimal("12"))
                                        .uomCode("EA")
                                        .taxCode("VAT13")
                                        .taxRateBps(1300)
                                        .unitNetPriceMinor(new BigDecimal("1000.000000"))
                                        .valuationPolicyId("valuation-policy-01")
                                        .valuationPolicyVersion("v1")
                                        .valuationPolicyHash("a".repeat(64))
                                        .lineNetAmountMinor(12000L)
                                        .lineTaxAmountMinor(1560L)
                                        .lineGrossAmountMinor(13560L)
                                        .schedules(List.of(ProcurementCommand.PurchaseOrderDeliveryScheduleDefinition.builder()
                                                .scheduleNumber(1)
                                                .requiredDeliveryDate(LocalDate.of(2026, 8, 3))
                                                .canonicalWarehouseId("warehouse-01")
                                                .scheduledQuantity(new BigDecimal("12"))
                                                .build()))
                                        .build(),
                                ProcurementCommand.PurchaseOrderLineDefinition.builder()
                                        .lineNumber(20)
                                        .awardLineId("award-line-20")
                                        .canonicalSkuId("sku-02")
                                        .orderedQuantity(new BigDecimal("8"))
                                        .uomCode("EA")
                                        .taxCode("VAT13")
                                        .taxRateBps(1300)
                                        .unitNetPriceMinor(new BigDecimal("1000.000000"))
                                        .valuationPolicyId("valuation-policy-02")
                                        .valuationPolicyVersion("v1")
                                        .valuationPolicyHash("b".repeat(64))
                                        .lineNetAmountMinor(8000L)
                                        .lineTaxAmountMinor(1040L)
                                        .lineGrossAmountMinor(9040L)
                                        .schedules(List.of(ProcurementCommand.PurchaseOrderDeliveryScheduleDefinition.builder()
                                                .scheduleNumber(1)
                                                .requiredDeliveryDate(LocalDate.of(2026, 8, 3))
                                                .canonicalWarehouseId("warehouse-01")
                                                .scheduledQuantity(new BigDecimal("8"))
                                                .build()))
                                        .build()))
                        .build())
                .build(), ACTOR);

        assertThat(result.getStatus()).isEqualTo("DRAFT");
        verify(mapper).insertOrder(argThat(row ->
                row.getStatus().equals("DRAFT")
                        && row.getVersion().equals(1L)));
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("procurement.order.created")
                        && event.getAggregateType().equals("procurement_order")));
    }

    @Test
    void rejectsPurchaseOrderWhenLineNumbersRepeat() {
        assertThatThrownBy(() -> service.execute(baseCreateCommand()
                .purchaseOrder(ProcurementCommand.PurchaseOrderDefinition.builder()
                        .orderCode("PO-CM-STD-002")
                        .sourceBusinessType("REPLENISHMENT")
                        .sourceBusinessRef("recommendation-02")
                        .legalEntityId("legal-entity-01")
                        .awardId("award-01").awardVersion(1L)
                        .supplierId("supplier-01")
                        .currencyCode("CNY")
                        .leadTimeDays(7)
                        .headerNetAmountMinor(10000L)
                        .headerTaxAmountMinor(1300L)
                        .headerGrossAmountMinor(11300L)
                        .lines(List.of(
                                ProcurementCommand.PurchaseOrderLineDefinition.builder()
                                        .lineNumber(10)
                                        .awardLineId("award-line-10")
                                        .canonicalSkuId("sku-01")
                                        .orderedQuantity(new BigDecimal("5"))
                                        .uomCode("EA")
                                        .taxCode("VAT13")
                                        .taxRateBps(1300)
                                        .unitNetPriceMinor(new BigDecimal("1000.000000"))
                                        .valuationPolicyId("valuation-policy-01")
                                        .valuationPolicyVersion("v1")
                                        .valuationPolicyHash("a".repeat(64))
                                        .lineNetAmountMinor(5000L)
                                        .lineTaxAmountMinor(650L)
                                        .lineGrossAmountMinor(5650L)
                                        .schedules(List.of(ProcurementCommand.PurchaseOrderDeliveryScheduleDefinition.builder()
                                                .scheduleNumber(1)
                                                .requiredDeliveryDate(LocalDate.of(2026, 8, 3))
                                                .canonicalWarehouseId("warehouse-01")
                                                .scheduledQuantity(new BigDecimal("5"))
                                                .build()))
                                        .build(),
                                ProcurementCommand.PurchaseOrderLineDefinition.builder()
                                        .lineNumber(10)
                                        .awardLineId("award-line-20")
                                        .canonicalSkuId("sku-02")
                                        .orderedQuantity(new BigDecimal("5"))
                                        .uomCode("EA")
                                        .taxCode("VAT13")
                                        .taxRateBps(1300)
                                        .unitNetPriceMinor(new BigDecimal("1000.000000"))
                                        .valuationPolicyId("valuation-policy-02")
                                        .valuationPolicyVersion("v1")
                                        .valuationPolicyHash("b".repeat(64))
                                        .lineNetAmountMinor(5000L)
                                        .lineTaxAmountMinor(650L)
                                        .lineGrossAmountMinor(5650L)
                                        .schedules(List.of(ProcurementCommand.PurchaseOrderDeliveryScheduleDefinition.builder()
                                                .scheduleNumber(1)
                                                .requiredDeliveryDate(LocalDate.of(2026, 8, 3))
                                                .canonicalWarehouseId("warehouse-01")
                                                .scheduledQuantity(new BigDecimal("5"))
                                                .build()))
                                        .build()))
                        .build())
                .build(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("purchase order lineNumber must be unique");
    }

    @Test
    void rejectsPurchaseOrderWhenHeaderGrossDoesNotMatchNetPlusTax() {
        assertThatThrownBy(() -> service.execute(baseCreateCommand()
                .purchaseOrder(ProcurementCommand.PurchaseOrderDefinition.builder()
                        .orderCode("PO-CM-STD-003")
                        .sourceBusinessType("REPLENISHMENT")
                        .sourceBusinessRef("recommendation-03")
                        .legalEntityId("legal-entity-01")
                        .awardId("award-01").awardVersion(1L)
                        .supplierId("supplier-01")
                        .currencyCode("CNY")
                        .leadTimeDays(7)
                        .headerNetAmountMinor(10000L)
                        .headerTaxAmountMinor(1300L)
                        .headerGrossAmountMinor(11299L)
                        .lines(List.of(
                                ProcurementCommand.PurchaseOrderLineDefinition.builder()
                                        .lineNumber(10)
                                        .awardLineId("award-line-10")
                                        .canonicalSkuId("sku-01")
                                        .orderedQuantity(new BigDecimal("10"))
                                        .uomCode("EA")
                                        .taxCode("VAT13")
                                        .taxRateBps(1300)
                                        .unitNetPriceMinor(new BigDecimal("1000.000000"))
                                        .valuationPolicyId("valuation-policy-01")
                                        .valuationPolicyVersion("v1")
                                        .valuationPolicyHash("a".repeat(64))
                                        .lineNetAmountMinor(10000L)
                                        .lineTaxAmountMinor(1300L)
                                        .lineGrossAmountMinor(11300L)
                                        .schedules(List.of(ProcurementCommand.PurchaseOrderDeliveryScheduleDefinition.builder()
                                                .scheduleNumber(1)
                                                .requiredDeliveryDate(LocalDate.of(2026, 8, 3))
                                                .canonicalWarehouseId("warehouse-01")
                                                .scheduledQuantity(new BigDecimal("10"))
                                                .build()))
                                        .build()))
                        .build())
                .build(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("purchase order header gross amount must equal net plus tax");
    }

    @Test
    void rejectsPurchaseOrderWhenTaxAmountViolatesHalfUpRule() {
        assertThatThrownBy(() -> service.execute(baseCreateCommand()
                .purchaseOrder(ProcurementCommand.PurchaseOrderDefinition.builder()
                        .orderCode("PO-CM-STD-004")
                        .sourceBusinessType("REPLENISHMENT")
                        .sourceBusinessRef("recommendation-04")
                        .legalEntityId("legal-entity-01")
                        .awardId("award-01").awardVersion(1L)
                        .supplierId("supplier-01")
                        .currencyCode("CNY")
                        .leadTimeDays(7)
                        .headerNetAmountMinor(999L)
                        .headerTaxAmountMinor(129L)
                        .headerGrossAmountMinor(1128L)
                        .lines(List.of(
                                ProcurementCommand.PurchaseOrderLineDefinition.builder()
                                        .lineNumber(10)
                                        .awardLineId("award-line-10")
                                        .canonicalSkuId("sku-01")
                                        .orderedQuantity(BigDecimal.ONE)
                                        .uomCode("EA")
                                        .taxCode("VAT13")
                                        .taxRateBps(1300)
                                        .unitNetPriceMinor(new BigDecimal("999.000000"))
                                        .valuationPolicyId("valuation-policy-01")
                                        .valuationPolicyVersion("v1")
                                        .valuationPolicyHash("a".repeat(64))
                                        .lineNetAmountMinor(999L)
                                        .lineTaxAmountMinor(129L)
                                        .lineGrossAmountMinor(1128L)
                                        .schedules(List.of(ProcurementCommand.PurchaseOrderDeliveryScheduleDefinition.builder()
                                                .scheduleNumber(1)
                                                .requiredDeliveryDate(LocalDate.of(2026, 8, 3))
                                                .canonicalWarehouseId("warehouse-01")
                                                .scheduledQuantity(BigDecimal.ONE)
                                                .build()))
                                        .build()))
                        .build())
                .build(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("purchase order lineTaxAmountMinor must equal HALF_UP(lineNetAmountMinor * taxRateBps / 10000)");
    }

    private static ProcurementCommand.ProcurementCommandBuilder baseCreateCommand() {
        return ProcurementCommand.builder()
                .operation(ProcurementOperation.CREATE_PURCHASE_ORDER)
                .idempotencyKey("procurement-idempotency-create-standard")
                .runId("run-001")
                .correlationId("11111111-1111-4111-8111-111111111111")
                .occurredAt(Instant.parse("2026-07-27T00:00:00Z"));
    }

    private static AwardLine awardLine(String awardLineId, String skuId, String quantity,
                                       long net, long tax, long gross) {
        return new AwardLine().setAwardLineId(awardLineId).setAwardId("award-01").setTenantId(31L)
                .setSupplierId("supplier-01").setCanonicalSkuId(skuId).setCanonicalWarehouseId("warehouse-01")
                .setAwardedQuantity(new BigDecimal(quantity)).setUomCode("EA").setCurrencyCode("CNY")
                .setUnitNetPriceMinor(new BigDecimal("1000.000000")).setTaxCode("VAT13").setTaxRateBps(1300)
                .setPromisedDeliveryDate(LocalDate.of(2026, 8, 3)).setLineNetAmountMinor(net)
                .setLineTaxAmountMinor(tax).setLineGrossAmountMinor(gross);
    }
}
