package cn.iocoder.yudao.module.cloudmold.finance.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.finance.api.FinanceCloseCommand;
import cn.iocoder.yudao.module.cloudmold.finance.api.FinanceCloseOperation;
import cn.iocoder.yudao.module.cloudmold.finance.api.FinanceCloseResult;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.AccountingPeriod;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.ChannelStatement;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.SettlementBatch;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.FinanceCloseMapper;
import cn.iocoder.yudao.module.cloudmold.finance.service.actor.FinanceActorPrincipalPort;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinanceCloseServiceImplTest {
    private static final String PREPARER = "principal-finance-preparer";
    private static final String APPROVER = "principal-finance-approver";
    private final FinanceCloseMapper mapper = mock(FinanceCloseMapper.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final FinanceActorPrincipalPort actorPrincipalPort = mock(FinanceActorPrincipalPort.class);
    private final FinanceCloseServiceImpl service =
            new FinanceCloseServiceImpl(mapper, outbox, actorPrincipalPort);
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
        when(mapper.selectLastInsertId()).thenReturn(501L);
        when(mapper.selectOperationForUpdate(501L, 162L)).thenAnswer(invocation ->
                new Operation().setOperationId(501L).setTenantId(162L)
                        .setRequestHash(requestHash.get()).setAttemptToken(attemptToken.get()).setStatus(0));
        when(mapper.markOperationSucceeded(eq(501L), eq(162L), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void opensCanonicalPeriodAndPublishesDailyDiscoveryEvent() {
        when(mapper.insertPeriod(any())).thenReturn(1);

        FinanceCloseResult result = service.execute(base(FinanceCloseOperation.OPEN_ACCOUNTING_PERIOD)
                .period(FinanceCloseCommand.PeriodDefinition.builder()
                        .periodId("period-2026-07").periodCode("2026-07")
                        .periodStart(LocalDate.of(2026, 7, 1))
                        .periodEnd(LocalDate.of(2026, 7, 31)).currencyCode("CNY")
                        .reasonCode("MONTHLY_CLOSE").build())
                .build(), PREPARER);

        assertThat(result.getStatus()).isEqualTo("OPEN");
        assertThat(result.getPeriodId()).isEqualTo("period-2026-07");
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("finance.accounting_period.opened")
                        && event.getAggregateType().equals("finance_accounting_period")
                        && event.getPayload().get("period_id").equals("period-2026-07")));
    }

    @Test
    void rejectsNonUuidCausationBeforePublishingAnInvalidOutboxEvent() {
        assertThatThrownBy(() -> service.execute(base(FinanceCloseOperation.OPEN_ACCOUNTING_PERIOD)
                .causationId("finance-period-2026-07")
                .build(), PREPARER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("causationId must be a UUID");
        verifyNoInteractions(outbox, actorPrincipalPort);
    }

    @Test
    void reconciliationCreatesTrackedDifferenceInsteadOfClaimingSuccess() {
        when(mapper.selectStatementForUpdate(162L, "statement-01")).thenReturn(statement("IMPORTED", 1L));
        when(mapper.selectPeriodForUpdate(162L, "period-2026-07")).thenReturn(openPeriod());
        when(mapper.insertDifference(any())).thenReturn(1);
        when(mapper.markStatementReconciled(eq(162L), eq("statement-01"), eq(1L),
                eq("EXCEPTION"), eq(200L), eq(PREPARER), any(), any())).thenReturn(1);

        FinanceCloseResult result = service.execute(base(
                FinanceCloseOperation.RECONCILE_CHANNEL_STATEMENT)
                .statement(FinanceCloseCommand.StatementDefinition.builder()
                        .statementId("statement-01").expectedVersion(1L)
                        .reasonCode("CHANNEL_RECONCILIATION").build())
                .build(), PREPARER);

        assertThat(result.getStatus()).isEqualTo("OPEN");
        assertThat(result.getDifferenceId()).isNotBlank();
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("finance.reconciliation_difference.opened")
                        && event.getPayload().get("difference_amount_minor").equals(200L)));
    }

    @Test
    void settlementRequiresIndependentConfirmer() {
        SettlementBatch batch = new SettlementBatch().setSettlementBatchId("settlement-01")
                .setPeriodId("period-2026-07").setExpectedAmountMinor(9700L)
                .setStatus("PREPARED").setPreparedByPrincipalId(PREPARER).setVersion(1L);
        when(mapper.selectSettlementForUpdate(162L, "settlement-01")).thenReturn(batch);

        assertThatThrownBy(() -> service.execute(base(FinanceCloseOperation.CONFIRM_SETTLEMENT)
                .settlement(FinanceCloseCommand.SettlementDefinition.builder()
                        .settlementBatchId("settlement-01").settledAmountMinor(9700L)
                        .bankReference("BANK-202607-01").settlementEvidenceSha256("a".repeat(64))
                        .expectedVersion(1L).build())
                .build(), PREPARER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("independent");
        verify(mapper, never()).settleBatch(anyLong(), anyString(), anyLong(), anyLong(),
                anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void closesOnlyWhenStatementsSettlementsAndJournalsAreComplete() {
        when(mapper.selectPeriodForUpdate(162L, "period-2026-07")).thenReturn(openPeriod());
        when(mapper.countStatements(162L, "period-2026-07")).thenReturn(1);
        when(mapper.countUnreconciledStatements(162L, "period-2026-07")).thenReturn(0);
        when(mapper.countOpenDifferences(162L, "period-2026-07")).thenReturn(0);
        when(mapper.countSettlementBatches(162L, "period-2026-07")).thenReturn(1);
        when(mapper.countUnsettledBatches(162L, "period-2026-07")).thenReturn(0);
        when(mapper.countJournalEntries(162L, "period-2026-07")).thenReturn(1);
        when(mapper.countUnpostedJournalEntries(162L, "period-2026-07")).thenReturn(0);
        when(mapper.closePeriod(eq(162L), eq("period-2026-07"), eq(1L), eq(APPROVER),
                eq("b".repeat(64)), eq("MONTHLY_CLOSE"), any())).thenReturn(1);

        FinanceCloseResult result = service.execute(base(FinanceCloseOperation.CLOSE_ACCOUNTING_PERIOD)
                .period(FinanceCloseCommand.PeriodDefinition.builder()
                        .periodId("period-2026-07").expectedVersion(1L)
                        .evidenceSha256("b".repeat(64)).reasonCode("MONTHLY_CLOSE").build())
                .build(), APPROVER);

        assertThat(result.getStatus()).isEqualTo("CLOSED");
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("finance.accounting_period.closed")
                        && event.getPayload().get("current_status").equals("CLOSED")));
    }

    private static AccountingPeriod openPeriod() {
        return new AccountingPeriod().setPeriodId("period-2026-07").setPeriodCode("2026-07")
                .setPeriodStart(LocalDate.of(2026, 7, 1)).setPeriodEnd(LocalDate.of(2026, 7, 31))
                .setCurrencyCode("CNY").setStatus("OPEN").setOpenedByPrincipalId(PREPARER)
                .setVersion(1L);
    }

    private static ChannelStatement statement(String status, long version) {
        return new ChannelStatement().setStatementId("statement-01").setPeriodId("period-2026-07")
                .setNetSettlementAmountMinor(9900L).setExpectedBusinessNetAmountMinor(9700L)
                .setStatus(status).setVersion(version);
    }

    private static FinanceCloseCommand.FinanceCloseCommandBuilder base(
            FinanceCloseOperation operation) {
        return FinanceCloseCommand.builder().operation(operation)
                .idempotencyKey("finance-" + operation)
                .runId("finance-run-001")
                .correlationId("22222222-2222-4222-8222-222222222222")
                .occurredAt(Instant.parse("2026-07-29T00:00:00Z"));
    }
}
