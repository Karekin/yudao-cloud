package cn.iocoder.yudao.module.cloudmold.gamification.service;

import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.*;
import cn.iocoder.yudao.module.cloudmold.gamification.api.GamificationCommand;
import cn.iocoder.yudao.module.cloudmold.gamification.dal.dataobject.GamificationRecords.*;
import cn.iocoder.yudao.module.cloudmold.gamification.dal.mysql.GamificationStoreMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.*;

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
}
