package cn.iocoder.yudao.module.cloudmold.gamification.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.gamification.api.*;
import cn.iocoder.yudao.module.cloudmold.gamification.dal.dataobject.GamificationRecords.*;
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

    @Test
    void redemptionAcceptsOnlyOpaqueAdapterReferencesAndRejectsMixedBalanceFields() {
        when(mapper.selectCollectibleDefinition(41L, "collectible-01", 1L)).thenReturn(
                new CollectibleDefinition().setCollectibleDefinitionId("collectible-01").setCollectibleVersion(1L)
                        .setTenantId(41L).setGameId("game-01"));
        GamificationCommand command = base(GamificationOperation.CREATE_REDEMPTION_INTENT)
                .setIdempotencyKey("redemption-mixed-001").setPrincipalId("player-01")
                .setCollectibleDefinitionId("collectible-01").setCollectibleVersion(1L).setCollectibleQuantity(1L)
                .setAdapterCode("MALL_REDEMPTION_V1").setExternalIntentRef("mall-intent-01")
                .setAssetAmountMicrounits(100L);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.execute(command));
        assertTrue(error.getMessage().contains("never external balances or money"));
        verify(mapper, never()).insertRedemptionIntent(any());
        verify(mapper, never()).insertCurrencyTransaction(any());
    }

    @Test
    void successfulRedemptionDebitsCollectibleOwnershipAndAppendsNegativeLedgerEntry() {
        when(mapper.selectRedemptionForUpdate(41L, "redemption-01")).thenReturn(new RedemptionIntent()
                .setRedemptionIntentId("redemption-01").setTenantId(41L).setGameId("game-01")
                .setPrincipalId("player-01").setCollectibleDefinitionId("collectible-01")
                .setCollectibleVersion(1L).setQuantity(2L).setAdapterCode("MALL_REDEMPTION_V1")
                .setExternalIntentRef("mall-intent-01").setStatus("PENDING").setVersion(1L));
        when(mapper.selectCollectibleDefinition(41L, "collectible-01", 1L)).thenReturn(
                new CollectibleDefinition().setCollectibleDefinitionId("collectible-01")
                        .setCollectibleVersion(1L).setTenantId(41L).setGameId("game-01"));
        when(mapper.selectCollectibleOwnershipForUpdate(41L, "game-01", "player-01", "collectible-01", 1L))
                .thenReturn(new CollectibleOwnership().setOwnershipId("ownership-01").setTenantId(41L)
                        .setGameId("game-01").setPrincipalId("player-01")
                        .setCollectibleDefinitionId("collectible-01").setCollectibleVersion(1L)
                        .setQuantity(3L).setVersion(4L));
        when(mapper.applyCollectibleDelta(eq(41L), eq("ownership-01"), eq(4L), eq(-2L), any()))
                .thenReturn(1);
        when(mapper.insertCollectibleEntry(any())).thenReturn(1);
        when(mapper.completeRedemption(eq(41L), eq("redemption-01"), eq(1L), eq("mall-result-01"),
                eq("SUCCEEDED"), any())).thenReturn(1);

        GamificationView result = service.execute(base(GamificationOperation.RECORD_REDEMPTION_RESULT)
                .setIdempotencyKey("redemption-result-01").setRedemptionIntentId("redemption-01")
                .setExpectedVersion(1L).setExternalResultRef("mall-result-01")
                .setRedemptionOutcome("SUCCEEDED"));

        assertEquals("SUCCEEDED", result.getRedemptionStatus());
        verify(mapper).applyCollectibleDelta(eq(41L), eq("ownership-01"), eq(4L), eq(-2L), any());
        verify(mapper).insertCollectibleEntry(argThat(entry -> entry.getDeltaQuantity().equals(-2L)
                && entry.getBalanceAfterQuantity().equals(1L)
                && entry.getSourceType().equals("REDEMPTION")
                && entry.getSourceId().equals("redemption-01")));
    }

    @Test
    void claimRewardGrantsExactlyOnce() {
        RewardClaim claimable = new RewardClaim().setRewardClaimId("claim-01").setTenantId(41L).setGameId("game-01")
                .setPrincipalId("player-01").setRewardDefinitionId("reward-01").setRewardVersion(1L)
                .setStatus("CLAIMABLE").setVersion(1L).setClaimExpiresAt(java.time.LocalDateTime.now().plusHours(1));
        RewardClaim claimed = new RewardClaim().setRewardClaimId("claim-01").setTenantId(41L).setStatus("CLAIMED").setVersion(2L);
        when(mapper.selectRewardClaimForUpdate(41L, "claim-01")).thenReturn(claimable, claimed);
        when(mapper.selectGame(41L, "game-01")).thenReturn(new Game().setGameId("game-01").setTenantId(41L)
                .setStatus("PUBLISHED").setCurrentVersion(1L).setVirtualCurrencyCode("GAME_COIN_GEM"));
        when(mapper.selectRewardDefinition(41L, "reward-01", 1L)).thenReturn(new RewardDefinition()
                .setRewardDefinitionId("reward-01").setRewardVersion(1L).setTenantId(41L).setGameId("game-01")
                .setRewardKind("FRAGMENT").setAssetClass("GAME_FRAGMENT").setAssetCode("GAME_FRAGMENT_CARD")
                .setFragmentQuantity(1L));
        when(mapper.insertRewardGrant(any())).thenReturn(1);
        when(mapper.insertFragmentBalance(any())).thenReturn(1);
        when(mapper.insertFragmentEntry(any())).thenReturn(1);
        when(mapper.transitionRewardClaim(eq(41L), eq("claim-01"), eq(1L), eq("CLAIMED"),
                anyString(), any(), any())).thenReturn(1);

        GamificationView result = service.execute(base(GamificationOperation.CLAIM_REWARD)
                .setIdempotencyKey("claim-first-0001").setRewardClaimId("claim-01").setExpectedVersion(1L));
        assertEquals("CLAIMED", result.getRewardClaimStatus());

        assertThrows(IllegalStateException.class, () -> service.execute(base(GamificationOperation.CLAIM_REWARD)
                .setIdempotencyKey("claim-again-0001").setRewardClaimId("claim-01").setExpectedVersion(2L)));
        verify(mapper, times(1)).insertRewardGrant(any());
        verify(mapper, times(1)).insertFragmentEntry(any());
    }

    private static GamificationCommand base(GamificationOperation operation) {
        return new GamificationCommand().setOperation(operation).setIdempotencyKey("idem-key-0001")
                .setRunId("run-001").setCorrelationId("corr-001");
    }
}
