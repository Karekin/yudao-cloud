package cn.iocoder.yudao.module.cloudmold.engagement.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementQueryApi;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.mysql.*;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EngagementQueryServiceImplTest {

    private final FavoriteMapper favoriteMapper = mock(FavoriteMapper.class);
    private final NotificationCampaignMapper campaignMapper = mock(NotificationCampaignMapper.class);
    private final NotificationDeliveryMapper deliveryMapper = mock(NotificationDeliveryMapper.class);
    private final CommunityMapper communityMapper = mock(CommunityMapper.class);
    private final EngagementQueryServiceImpl service = new EngagementQueryServiceImpl(favoriteMapper, campaignMapper,
            deliveryMapper, communityMapper);

    @BeforeEach
    void setUp() { TenantContextHolder.setTenantId(12L); }

    @AfterEach
    void tearDown() { TenantContextHolder.clear(); }

    @Test
    void shouldScopeFavoriteLookupToCurrentTenant() {
        when(favoriteMapper.selectByBusinessKey(12L, "principal-1", "spu-1")).thenReturn(new FavoriteDO()
                .setFavoriteId("favorite-1").setPrincipalId("principal-1").setCanonicalSpuId("spu-1")
                .setStatus("ACTIVE").setVersion(2L).setUpdatedAt(LocalDateTime.of(2026, 7, 16, 1, 2)));

        EngagementQueryApi.FavoriteView view = service.getFavorite("principal-1", "spu-1");

        assertThat(view.getFavoriteId()).isEqualTo("favorite-1");
        assertThat(view.getVersion()).isEqualTo(2L);
        verify(favoriteMapper).selectByBusinessKey(12L, "principal-1", "spu-1");
    }

    @Test
    void shouldExposePersistedModerationDecision() {
        when(communityMapper.selectModerationCase(12L, "case-1")).thenReturn(new CommunityModerationCaseDO()
                .setModerationCaseId("case-1").setContentId("content-1").setReporterPrincipalId("reporter-1")
                .setModeratorPrincipalId("moderator-1").setStatus("ACTIONED").setDecision("HIDE")
                .setDecisionReasonCode("POLICY_SPAM").setVersion(2L));

        EngagementQueryApi.ModerationCaseView view = service.getModerationCase("case-1");

        assertThat(view.getDecision()).isEqualTo("HIDE");
        assertThat(view.getStatus()).isEqualTo("ACTIONED");
    }
}
