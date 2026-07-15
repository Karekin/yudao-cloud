package cn.iocoder.yudao.module.cloudmold.merchant.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.merchant.api.*;
import cn.iocoder.yudao.module.cloudmold.merchant.api.deposit.*;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.mysql.MerchantDepositStoreMapper;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MerchantDepositCommandServiceImplTest {

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
    void postsImmutableLedgerAndSuspendsOnlyWhenEnforcedCoverageCrossesTwentyPercent() {
        MerchantDepositResult assessed = harness.execute(command(MerchantDepositOperation.ASSESS_REQUIRED,
                "deposit-1", null, 10_000L));
        assertThat(assessed.getCoverageStatus()).isEqualTo("SALES_BLOCKED");
        assertThat(assessed.getEnforcementStatus()).isEqualTo("SHADOW");

        MerchantDepositResult paid = harness.execute(command(MerchantDepositOperation.PAY,
                "deposit-2", assessed.getAccountVersion(), 10_000L));
        MerchantDepositResult enforced = harness.execute(command(MerchantDepositOperation.ACTIVATE_ENFORCEMENT,
                "deposit-3", paid.getAccountVersion(), 0L));
        MerchantDepositResult frozen = harness.execute(command(MerchantDepositOperation.FREEZE,
                "deposit-4", enforced.getAccountVersion(), 1_000L));
        MerchantDepositResult unfrozen = harness.execute(command(MerchantDepositOperation.UNFREEZE,
                "deposit-5", frozen.getAccountVersion(), 1_000L));
        MerchantDepositResult restricted = harness.execute(command(MerchantDepositOperation.DEDUCT,
                "deposit-6", unfrozen.getAccountVersion(), 4_500L));

        assertThat(restricted.getHeldAmountMinor()).isEqualTo(5_500L);
        assertThat(restricted.getCoverageStatus()).isEqualTo("BID_RESTRICTED");
        verifyNoInteractions(harness.merchantCommandApi);

        MerchantDepositCommand blockedCommand = command(MerchantDepositOperation.DEDUCT,
                "deposit-7", restricted.getAccountVersion(), 3_600L);
        MerchantDepositResult blocked = harness.execute(blockedCommand);

        assertThat(blocked.getHeldAmountMinor()).isEqualTo(1_900L);
        assertThat(blocked.getPaidAmountMinor()).isEqualTo(10_000L);
        assertThat(blocked.getDeductedAmountMinor()).isEqualTo(8_100L);
        assertThat(blocked.getCoverageStatus()).isEqualTo("SALES_BLOCKED");
        assertThat(blocked.getMerchantStatus()).isEqualTo("SUSPENDED");
        assertThat(blocked.getListingUnpublishSagaId()).isEqualTo("deposit-saga");
        assertThat(harness.ledger).hasSize(7);
        assertThat(harness.events).hasSize(7);
        assertThat(harness.ledger).extracting(MerchantDepositLedgerEntryDO::getAccountVersion)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);

        assertThat(harness.suspensions).singleElement().satisfies(suspend -> {
            assertThat(suspend.getOperation()).isEqualTo(MerchantOperation.SUSPEND_MERCHANT);
            assertThat(suspend.getCausationId()).isEqualTo(blocked.getDepositEventId());
            assertThat(suspend.getReason()).startsWith("MERCHANT_DEPOSIT_SALES_BLOCKED:");
        });

        MerchantDepositResult replay = harness.execute(blockedCommand);
        assertThat(replay.isDuplicate()).isTrue();
        assertThat(replay.getLedgerEntryId()).isEqualTo(blocked.getLedgerEntryId());
        assertThat(harness.ledger).hasSize(7);
        assertThat(harness.events).hasSize(7);
        assertThat(harness.suspensions).hasSize(1);
    }

    @Test
    void frozenDepositMustBeExplicitlyUnfrozenBeforeDeduction() {
        MerchantDepositResult assessed = harness.execute(command(MerchantDepositOperation.ASSESS_REQUIRED,
                "freeze-1", null, 10_000L));
        MerchantDepositResult paid = harness.execute(command(MerchantDepositOperation.PAY,
                "freeze-2", assessed.getAccountVersion(), 5_000L));
        MerchantDepositResult frozen = harness.execute(command(MerchantDepositOperation.FREEZE,
                "freeze-3", paid.getAccountVersion(), 4_000L));

        assertThatThrownBy(() -> harness.execute(command(MerchantDepositOperation.DEDUCT,
                "freeze-4", frozen.getAccountVersion(), 2_000L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unfreeze an evidenced amount");
        assertThat(harness.account.getHeldAmountMinor()).isEqualTo(5_000L);
        assertThat(harness.account.getFrozenAmountMinor()).isEqualTo(4_000L);
        assertThat(harness.ledger).hasSize(3);
    }

    @Test
    void enforcesExactIntegerThresholdBoundaries() {
        assertThat(MerchantDepositCommandServiceImpl.coverage(6_000, 10_000)).isEqualTo("SUFFICIENT");
        assertThat(MerchantDepositCommandServiceImpl.coverage(5_999, 10_000)).isEqualTo("BID_RESTRICTED");
        assertThat(MerchantDepositCommandServiceImpl.coverage(2_000, 10_000)).isEqualTo("BID_RESTRICTED");
        assertThat(MerchantDepositCommandServiceImpl.coverage(1_999, 10_000)).isEqualTo("SALES_BLOCKED");
    }

    @Test
    void eventUsesMinorUnitsAndContainsNoPaymentCredential() {
        MerchantDepositResult result = harness.execute(command(MerchantDepositOperation.ASSESS_REQUIRED,
                "privacy-1", null, 88_800L));
        AppendDomainEventCommand event = harness.events.get(0);

        assertThat(event.getEventType()).isEqualTo("merchant.deposit.ledger_posted");
        assertThat(event.getAggregateId()).isEqualTo(result.getAccountId());
        assertThat(event.getPayload()).containsEntry("amount_minor", 88_800L).containsEntry("currency", "CNY")
                .containsEntry("evidence_ref", "evidence:privacy-1");
        assertThat(event.getPayload().keySet()).noneMatch(key -> key.matches(
                "(?i).*(bank|account_number|phone|identity|voucher_url|credential).*"));
    }

    private static MerchantDepositCommand command(MerchantDepositOperation operation, String key,
                                                   Long expectedVersion, Long amountMinor) {
        return new MerchantDepositCommand().setOperation(operation).setIdempotencyKey(key).setRunId("deposit-run")
                .setMerchantId("merchant-1").setExpectedAccountVersion(expectedVersion).setAmountMinor(amountMinor)
                .setCurrency("cny").setPolicyVersion("YSHOPPING-V1").setBusinessReference("reference:" + key)
                .setReasonCode("CONTROLLED_TEST").setEvidenceRef("evidence:" + key)
                .setSourceSystem("test").setTraceId("trace-" + key)
                .setOccurredAt(Instant.parse("2026-07-15T04:00:00Z"));
    }

    private static final class Harness {
        final MerchantDepositStoreMapper mapper = mock(MerchantDepositStoreMapper.class);
        final MerchantCommandApi merchantCommandApi = mock(MerchantCommandApi.class);
        final OutboxAppender outboxAppender = mock(OutboxAppender.class);
        final MerchantDepositCommandServiceImpl service = new MerchantDepositCommandServiceImpl(
                mapper, merchantCommandApi, outboxAppender);
        final AtomicLong sequence = new AtomicLong();
        final ThreadLocal<Long> lastOperation = new ThreadLocal<>();
        final Map<String, Long> operationByKey = new HashMap<>();
        final Map<Long, MerchantDepositOperationDO> operations = new HashMap<>();
        final List<MerchantDepositLedgerEntryDO> ledger = new ArrayList<>();
        final List<AppendDomainEventCommand> events = new ArrayList<>();
        final List<MerchantCommand> suspensions = new ArrayList<>();
        final MerchantAccountDO merchant = new MerchantAccountDO().setTenantId(7L).setMerchantId("merchant-1")
                .setLegalEntityId("legal-1").setStatus("ACTIVE").setVersion(2L);
        MerchantDepositAccountDO account;

        Harness() {
            stubOperations();
            when(mapper.selectMerchantForUpdate(7L, "merchant-1")).thenReturn(merchant);
            when(mapper.selectAccountForUpdate(7L, "merchant-1", "CNY")).thenAnswer(i -> account);
            doAnswer(i -> { account = i.getArgument(0); return 1; }).when(mapper).insertAccount(any());
            when(mapper.updateAccount(anyLong(), anyString(), anyLong(), anyLong(), anyLong(), anyLong(),
                    anyLong(), anyLong(), anyString(), anyString(), anyString(), any())).thenReturn(1);
            doAnswer(i -> { ledger.add(i.getArgument(0)); return 1; }).when(mapper).insertLedgerEntry(any());
            doAnswer(i -> { events.add(i.getArgument(0)); return null; }).when(outboxAppender).append(any());
            when(merchantCommandApi.execute(any())).thenAnswer(i -> {
                MerchantCommand command = i.getArgument(0);
                suspensions.add(command);
                return new MerchantCommandResult().setMerchantId("merchant-1").setMerchantStatus("SUSPENDED")
                        .setMerchantVersion(3L).setListingUnpublishSagaId("deposit-saga")
                        .setAffectedListingCount(2);
            });
        }

        MerchantDepositResult execute(MerchantDepositCommand command) {
            return service.execute(command);
        }

        private void stubOperations() {
            doAnswer(i -> {
                String key = i.getArgument(1);
                String hash = i.getArgument(3);
                String attempt = i.getArgument(4);
                Long id = operationByKey.get(key);
                if (id == null) {
                    id = sequence.incrementAndGet();
                    operationByKey.put(key, id);
                    operations.put(id, new MerchantDepositOperationDO().setOperationId(id).setTenantId(7L)
                            .setIdempotencyKey(key).setCommandType(i.getArgument(2)).setRequestHash(hash)
                            .setAttemptToken(attempt).setStatus(0));
                }
                lastOperation.set(id);
                return 1;
            }).when(mapper).insertOrResolveOperation(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
            when(mapper.selectLastInsertId()).thenAnswer(i -> lastOperation.get());
            when(mapper.selectOperationForUpdate(anyLong(), eq(7L)))
                    .thenAnswer(i -> operations.get(i.getArgument(0)));
            when(mapper.markOperationSucceeded(anyLong(), eq(7L), anyString(), anyString(), any()))
                    .thenAnswer(i -> {
                        MerchantDepositOperationDO operation = operations.get(i.getArgument(0));
                        operation.setStatus(10).setAccountId(i.getArgument(2)).setResultJson(i.getArgument(3));
                        return 1;
                    });
        }
    }
}
