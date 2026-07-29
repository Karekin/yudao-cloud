package cn.iocoder.yudao.module.cloudmold.supplier.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierSourcingCommand;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierSourcingOperation;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierSourcingResult;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierSourcingRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierSourcingRecords.Quote;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierSourcingRecords.SourcingCase;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierSourcingRecords.SupplierProfile;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.mysql.SupplierSourcingMapper;
import cn.iocoder.yudao.module.cloudmold.supplier.service.actor.SupplierActorPrincipalPort;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupplierSourcingServiceImplTest {
    private static final String ACTOR = "principal-buyer-01";
    private final SupplierSourcingMapper mapper = mock(SupplierSourcingMapper.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final SupplierActorPrincipalPort actorPrincipalPort = mock(SupplierActorPrincipalPort.class);
    private final SupplierSourcingServiceImpl service =
            new SupplierSourcingServiceImpl(mapper, outbox, actorPrincipalPort);
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
        when(mapper.markOperationSucceeded(eq(401L), eq(162L), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsCanonicalRfqAndPublishesDiscoveryEvent() {
        when(mapper.insertSourcingCase(any())).thenReturn(1);

        SupplierSourcingResult result = service.execute(base(SupplierSourcingOperation.CREATE_RFQ)
                .sourcingCase(SupplierSourcingCommand.SourcingCaseDefinition.builder()
                        .sourcingCaseId("case-01")
                        .rfqCode("RFQ-2026-001")
                        .requestRef("assortment-wave-01")
                        .canonicalSkuId("sku-01")
                        .targetQuantity(new BigDecimal("500"))
                        .uomCode("EA")
                        .currencyCode("CNY")
                        .maxUnitCostMinor(2500L)
                        .requiredDeliveryDate(LocalDate.of(2026, 9, 1))
                        .requirements("Cotton tee, audited capacity, target gross margin >= 55%")
                        .build())
                .build(), ACTOR);

        assertThat(result.getStatus()).isEqualTo("OPEN");
        assertThat(result.getSourcingCaseId()).isEqualTo("case-01");
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("supplier.sourcing_case.created")
                        && event.getAggregateType().equals("supplier_sourcing_case")
                        && event.getPayload().get("sourcing_case_id").equals("case-01")));
    }

    @Test
    void awardsOnlyAdmittedSupplierWithComparisonAndPassingSample() {
        when(mapper.selectCaseForUpdate(162L, "case-01")).thenReturn(baseCase());
        when(mapper.countQuotedSuppliers(162L, "case-01")).thenReturn(2);
        when(mapper.selectSupplierForUpdate(162L, "supplier-a")).thenReturn(admittedSupplier());
        when(mapper.selectQuote(162L, "quote-a")).thenReturn(selectedQuote());
        when(mapper.countPassingSample(162L, "case-01", "supplier-a", "quote-a")).thenReturn(1);
        when(mapper.awardCase(eq(162L), eq("case-01"), eq(5L), eq("supplier-a"), eq("quote-a"),
                anyString(), eq(ACTOR), any())).thenReturn(1);
        when(mapper.resolveQuotes(eq(162L), eq("case-01"), eq("quote-a"), any())).thenReturn(2);

        SupplierSourcingResult result = service.execute(base(SupplierSourcingOperation.AWARD_SUPPLIER)
                .award(SupplierSourcingCommand.AwardDefinition.builder()
                        .sourcingCaseId("case-01")
                        .supplierId("supplier-a")
                        .quoteId("quote-a")
                        .decisionRationale("Two comparable quotes reviewed; supplier A passed sample and capacity gates.")
                        .expectedCaseVersion(5L)
                        .build())
                .build(), ACTOR);

        assertThat(result.getStatus()).isEqualTo("AWARDED");
        assertThat(result.getSupplierId()).isEqualTo("supplier-a");
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("supplier.sourcing_case.awarded")
                        && event.getPayload().get("quote_id").equals("quote-a")));
    }

    @Test
    void rejectsAwardWithoutTwoComparableSuppliers() {
        when(mapper.selectCaseForUpdate(162L, "case-01")).thenReturn(baseCase());
        when(mapper.countQuotedSuppliers(162L, "case-01")).thenReturn(1);

        assertThatThrownBy(() -> service.execute(base(SupplierSourcingOperation.AWARD_SUPPLIER)
                .award(SupplierSourcingCommand.AwardDefinition.builder()
                        .sourcingCaseId("case-01")
                        .supplierId("supplier-a")
                        .quoteId("quote-a")
                        .decisionRationale("Insufficient comparison.")
                        .expectedCaseVersion(5L)
                        .build())
                .build(), ACTOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least two suppliers");
        verify(mapper, never()).awardCase(anyLong(), anyString(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    void rejectsAwardWhenSelectedQuoteExceedsCostCeiling() {
        Quote expensive = selectedQuote().setUnitCostMinor(2600L);
        when(mapper.selectCaseForUpdate(162L, "case-01")).thenReturn(baseCase());
        when(mapper.countQuotedSuppliers(162L, "case-01")).thenReturn(2);
        when(mapper.selectSupplierForUpdate(162L, "supplier-a")).thenReturn(admittedSupplier());
        when(mapper.selectQuote(162L, "quote-a")).thenReturn(expensive);
        when(mapper.countPassingSample(162L, "case-01", "supplier-a", "quote-a")).thenReturn(1);

        assertThatThrownBy(() -> service.execute(base(SupplierSourcingOperation.AWARD_SUPPLIER)
                .award(SupplierSourcingCommand.AwardDefinition.builder()
                        .sourcingCaseId("case-01").supplierId("supplier-a").quoteId("quote-a")
                        .decisionRationale("Cost ceiling should stop this award.").expectedCaseVersion(5L)
                        .build())
                .build(), ACTOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cost ceiling");
    }

    private static SourcingCase baseCase() {
        return new SourcingCase()
                .setSourcingCaseId("case-01").setTenantId(162L).setRfqCode("RFQ-2026-001")
                .setRequestRef("assortment-wave-01").setCanonicalSkuId("sku-01")
                .setTargetQuantity(new BigDecimal("500")).setUomCode("EA").setCurrencyCode("CNY")
                .setMaxUnitCostMinor(2500L).setRequiredDeliveryDate(LocalDate.of(2026, 9, 1))
                .setRequirements("Cotton tee").setStatus("EVALUATING").setVersion(5L);
    }

    private static SupplierProfile admittedSupplier() {
        return new SupplierProfile().setSupplierId("supplier-a").setSupplierCode("SUP-A")
                .setSupplierName("Supplier A").setStatus("ACTIVE").setAdmissionStatus("ADMITTED")
                .setVersion(3L);
    }

    private static Quote selectedQuote() {
        return new Quote().setQuoteId("quote-a").setSourcingCaseId("case-01")
                .setSupplierId("supplier-a").setQuoteVersion(1).setUnitCostMinor(2200L)
                .setMoq(new BigDecimal("100")).setLeadTimeDays(14)
                .setCapacityQuantity(new BigDecimal("800")).setValidUntil(LocalDate.of(2026, 9, 30))
                .setStatus("SUBMITTED");
    }

    private static SupplierSourcingCommand.SupplierSourcingCommandBuilder base(
            SupplierSourcingOperation operation) {
        return SupplierSourcingCommand.builder()
                .operation(operation)
                .idempotencyKey("supplier-sourcing-" + operation)
                .runId("run-001")
                .correlationId("11111111-1111-4111-8111-111111111111")
                .occurredAt(Instant.parse("2026-07-29T00:00:00Z"));
    }
}
