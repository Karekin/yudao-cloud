package cn.iocoder.yudao.module.cloudmold.gamification.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.gamification.api.*;
import cn.iocoder.yudao.module.cloudmold.gamification.dal.dataobject.GamificationRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.gamification.dal.mysql.GamificationStoreMapper;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GamificationCommandServiceImplTest {
    private GamificationStoreMapper mapper;
    private GamificationEventService events;
    private GamificationCommandServiceImpl service;
    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> attemptToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(41L);
        mapper = mock(GamificationStoreMapper.class);
        events = mock(GamificationEventService.class);
        service = new GamificationCommandServiceImpl(mapper, events, mock(GamificationRandomSource.class));
        when(mapper.insertOrResolveOperation(eq(41L), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenReturn(71L);
        when(mapper.selectOperationForUpdate(71L, 41L)).thenAnswer(invocation -> new Operation()
                .setOperationId(71L).setTenantId(41L).setRequestHash(requestHash.get())
                .setAttemptToken(attemptToken.get()).setStatus(0));
        when(mapper.insertGame(any())).thenReturn(1);
        when(mapper.markOperationSucceeded(eq(71L), eq(41L), anyString(), anyString(), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createGameIsTenantScopedAndEmitsTransactionalEvent() {
        GamificationView result = service.execute(base(GamificationOperation.CREATE_GAME)
                .setGameCode("LUCKY_DRAW").setGameName("Lucky draw"));

        assertNotNull(result.getGameId());
        assertEquals(0L, result.getGameVersion());
        assertEquals("DRAFT", result.getGameStatus());
        verify(mapper).insertGame(argThat(game -> game.getTenantId().equals(41L)
                && game.getGameCode().equals("LUCKY_DRAW")));
        verify(events).appendGameCreated(eq(71L), any(), any(), any(), any());
    }

    @Test
    void rejectsMallPointTokenCouponAndFiatAliasesBeforeMutation() {
        for (String forbidden : new String[]{"MALL_POINT", "GAME_TOKEN", "COUPON_CNY", "RMB", "GAME_COIN_CNY"}) {
            GamificationCommand command = base(GamificationOperation.PUBLISH_GAME_VERSION)
                    .setIdempotencyKey("publish-" + forbidden).setGameId("game-01").setExpectedVersion(0L)
                    .setVirtualCurrencyCode(forbidden).setAssistDailyLimit(2).setMaxRoundsPerSession(10)
                    .setSessionTtlSeconds(600);
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> service.execute(command));
            assertFalse(error.getMessage().isBlank(), forbidden);
        }
        verify(mapper, never()).selectGameForUpdate(anyLong(), anyString());
    }

    private static GamificationCommand base(GamificationOperation operation) {
        return new GamificationCommand().setOperation(operation).setIdempotencyKey("idem-key-0001")
                .setRunId("run-001").setCorrelationId("corr-001");
    }
}
