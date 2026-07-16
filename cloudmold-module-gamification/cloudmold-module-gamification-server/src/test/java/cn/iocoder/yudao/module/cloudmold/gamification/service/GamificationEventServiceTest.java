package cn.iocoder.yudao.module.cloudmold.gamification.service;

import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.*;
import cn.iocoder.yudao.module.cloudmold.gamification.api.GamificationCommand;
import cn.iocoder.yudao.module.cloudmold.gamification.dal.dataobject.GamificationRecords.*;
import cn.iocoder.yudao.module.cloudmold.gamification.dal.mysql.GamificationStoreMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GamificationEventServiceTest {
    @Test
    void currencyEventProvesDoubleEntryAndGameOnlyBoundary() {
        OutboxAppender outbox = mock(OutboxAppender.class);
        GamificationEventService service = new GamificationEventService(mock(GamificationStoreMapper.class), outbox);
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 1, 0);
        CurrencyTransaction transaction = new CurrencyTransaction().setLedgerTransactionId("tx-01").setTenantId(9L)
                .setGameId("game-01").setCurrencyCode("GAME_COIN_LUCKY").setBusinessType("GIFT")
                .setBusinessId("gift-01").setAmountMicrounits(500L).setOccurredAt(now).setCreatedAt(now);
        Account from = new Account().setAccountId("account-from").setBalanceMicrounits(500L);
        Account to = new Account().setAccountId("account-to").setBalanceMicrounits(1500L);
        GamificationCommand command = new GamificationCommand().setRunId("run-1").setCorrelationId("corr-1");

        service.appendCurrencyLedger(transaction, from, to, command, now.toInstant(ZoneOffset.UTC));

        ArgumentCaptor<AppendDomainEventCommand> captor = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outbox).append(captor.capture());
        AppendDomainEventCommand event = captor.getValue();
        assertEquals("gamification.virtual_currency.ledger_posted", event.getEventType());
        assertEquals("GAME_ONLY", event.getHeaders().get("asset_boundary"));
        assertEquals("GAME_VIRTUAL_CURRENCY", event.getPayload().get("asset_class"));
        assertEquals(2, event.getPayload().get("entry_count"));
        assertEquals(0, event.getPayload().get("entry_delta_sum_microunits"));
        assertFalse(event.getPayload().containsKey("money_amount"));
        assertFalse(event.getPayload().containsKey("coupon_id"));
        assertFalse(event.getPayload().containsKey("token_amount"));
    }

    @Test
    void secureDrawStoresOnlyHashAndAlwaysSelectsWithinWeight() {
        SecureGamificationRandomSource source = new SecureGamificationRandomSource();
        for (int i = 0; i < 256; i++) {
            GamificationRandomSource.Selection selection = source.select(37);
            assertTrue(selection.ticket() >= 1 && selection.ticket() <= 37);
            assertTrue(selection.entropySha256().matches("[0-9a-f]{64}"));
        }
        assertThrows(IllegalArgumentException.class, () -> source.select(0));
    }

    @Test
    void definitionAndCrossDomainEventsCarryCompleteReconciliationKeys() {
        GamificationStoreMapper mapper = mock(GamificationStoreMapper.class);
        OutboxAppender outbox = mock(OutboxAppender.class);
        GamificationEventService service = new GamificationEventService(mapper, outbox);
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 2, 0);
        Instant occurredAt = now.toInstant(ZoneOffset.UTC);
        GamificationCommand command = new GamificationCommand().setRunId("run-2").setCorrelationId("corr-2");

        service.appendGame(1L, new Game().setTenantId(9L).setGameId("game-01").setGameCode("LUCKY")
                        .setStatus("PUBLISHED").setCurrentVersion(1L).setVirtualCurrencyCode("GAME_COIN_LUCKY")
                        .setAssistDailyLimit(3).setMaxRoundsPerSession(20).setSessionTtlSeconds(900),
                new GameVersion().setDefinitionSha256("a".repeat(64)), "DRAFT", command, occurredAt, now);
        service.appendSeasonSeries(new SeasonSeries().setTenantId(9L).setGameId("game-01")
                .setSeasonSeriesId("series-01").setSeriesVersion(1L).setSeriesCode("SERIES")
                .setSeriesName("Series One").setDefinitionSha256("b".repeat(64)), command, occurredAt);
        service.appendSeason(new Season().setTenantId(9L).setGameId("game-01").setSeasonId("season-01")
                .setSeasonVersion(1L).setSeasonSeriesId("series-01").setSeriesVersion(1L)
                .setSeasonCode("S1").setSeasonName("Season One").setStatus("PUBLISHED")
                .setStartsAt(now).setEndsAt(now.plusDays(30)).setDefinitionSha256("c".repeat(64)), command,
                occurredAt);
        service.appendCollectibleDefinition(new CollectibleDefinition().setTenantId(9L).setGameId("game-01")
                .setCollectibleDefinitionId("collectible-01").setCollectibleVersion(1L)
                .setCollectibleCode("GK_ONE").setCollectibleKind("GK").setCollectibleName("GK One")
                .setDefinitionSha256("d".repeat(64)), command, occurredAt);
        service.appendRedemption(2L, new RedemptionIntent().setTenantId(9L).setGameId("game-01")
                .setRedemptionIntentId("redemption-01").setVersion(1L).setPrincipalId("player-01")
                .setCollectibleDefinitionId("collectible-01").setCollectibleVersion(1L).setQuantity(2L)
                .setAdapterCode("MALL_REDEMPTION_V1").setExternalIntentRef("intent-01").setStatus("PENDING"),
                null, command, occurredAt, now);
        service.appendClaim(3L, new RewardClaim().setTenantId(9L).setGameId("game-01")
                .setRewardClaimId("claim-01").setVersion(1L).setPrincipalId("player-01")
                .setRewardDefinitionId("reward-01").setRewardVersion(1L).setSourceType("TASK")
                .setSourceId("task-01").setStatus("CLAIMABLE").setClaimExpiresAt(now.plusDays(1)),
                null, command, occurredAt, now);

        ArgumentCaptor<AppendDomainEventCommand> captor = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outbox, times(6)).append(captor.capture());
        List<AppendDomainEventCommand> events = captor.getAllValues();
        assertEquals(900, events.get(0).getPayload().get("session_ttl_seconds"));
        assertEquals("Series One", events.get(1).getPayload().get("series_name"));
        assertEquals("Season One", events.get(2).getPayload().get("season_name"));
        assertEquals("c".repeat(64), events.get(2).getPayload().get("definition_sha256"));
        assertEquals("GK One", events.get(3).getPayload().get("collectible_name"));
        assertEquals(2L, events.get(4).getPayload().get("quantity"));
        assertEquals("collectible-01", events.get(4).getPayload().get("collectible_definition_id"));
        assertEquals(false, events.get(4).getPayload().get("contains_external_balance"));
        assertFalse(events.get(4).getPayload().containsKey("external_amount"));
        assertEquals("TASK", events.get(5).getPayload().get("source_type"));
        assertEquals("task-01", events.get(5).getPayload().get("source_id"));
        assertTrue(events.get(5).getPayload().containsKey("claimed_at"));
    }
}
