package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppRecommendationDecisionDO;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppRecommendationItemDO;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.mysql.AppRecommendationMapper;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppRecommendationServiceTest {

    private final AppProductReadService productReadService = mock(AppProductReadService.class);
    private final AppRecommendationMapper recommendationMapper = mock(AppRecommendationMapper.class);
    private final CommerceBehaviorCommandApi commandApi = mock(CommerceBehaviorCommandApi.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final AppRecommendationService service = new AppRecommendationService(
            productReadService, recommendationMapper, commandApi, outboxAppender);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(11L);
        ReflectionTestUtils.setField(service, "recommendationTtlSeconds", 900);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-1", "a".repeat(64), false));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldBuildDeterministicRecommendationFromSellableProductsOnly() {
        when(recommendationMapper.selectActiveDecision(anyLong(), anyString(), anyString(), anyString(), any()))
                .thenReturn(null);
        when(recommendationMapper.insertDecision(any())).thenReturn(1);
        when(recommendationMapper.insertItem(any())).thenReturn(1);
        when(productReadService.page(null, 1, 12)).thenReturn(AppProductPageView.builder()
                .list(List.of(
                        product("listing-1", BigDecimal.TEN, 39900L, true, true),
                        product("listing-2", BigDecimal.ONE, 19900L, true, true),
                        product("listing-3", BigDecimal.ZERO, 9900L, true, false)))
                .total(3L).pageNo(1).pageSize(12).build());

        AppRecommendationDecisionView view = service.recommend(
                "f3734897-1a03-4887-bf9e-34a898d03594", "HOME_FEED", 3);

        assertThat(view.getPolicyVersion()).isEqualTo("LOCAL_DETERMINISTIC_V1");
        assertThat(view.getItems()).hasSize(2);
        assertThat(view.getItems().get(0).getListingId()).isEqualTo("listing-1");
        assertThat(view.getItems().get(0).getRank()).isEqualTo(1);
        assertThat(view.getItems().get(1).getListingId()).isEqualTo("listing-2");
        verify(outboxAppender, times(2)).append(any());
    }

    @Test
    void shouldReuseActiveDecisionBeforeRegenerating() {
        AppRecommendationDecisionDO decision = new AppRecommendationDecisionDO();
        decision.setDecisionId("decision-1");
        decision.setSceneCode("HOME_FEED");
        decision.setPolicyVersion("LOCAL_DETERMINISTIC_V1");
        decision.setDecisionToken("recommend:12345678");
        decision.setResultSetToken("recommend:12345678");
        decision.setTtlSeconds(900);
        decision.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        when(recommendationMapper.selectActiveDecision(anyLong(), anyString(), anyString(), anyString(), any()))
                .thenReturn(decision);
        when(recommendationMapper.selectItems(11L, "decision-1")).thenReturn(List.of(item("listing-1", 1)));

        AppRecommendationDecisionView view = service.recommend(
                "f3734897-1a03-4887-bf9e-34a898d03594", "HOME_FEED", 3);

        assertThat(view.getItems()).singleElement().extracting(AppRecommendationDecisionView.Item::getListingId)
                .isEqualTo("listing-1");
        verify(recommendationMapper, never()).insertDecision(any());
        verify(outboxAppender, never()).append(any());
    }

    private static AppProductView product(String listingId, BigDecimal available, long priceMinor,
                                          boolean enabled, boolean inStock) {
        return AppProductView.builder()
                .listingId(listingId)
                .listingNo("NO-" + listingId)
                .title("title-" + listingId)
                .primaryImageUrl("https://img/" + listingId)
                .merchantId("merchant-1")
                .shopId("shop-1")
                .canonicalSpuId("spu-1")
                .listingVersion(2L)
                .skus(List.of(AppProductView.Sku.builder()
                        .canonicalSkuId("sku-" + listingId)
                        .listingOfferId("offer-" + listingId)
                        .priceMinor(priceMinor)
                        .currencyCode("CNY")
                        .enabled(enabled)
                        .availableQuantity(available)
                        .inStock(inStock)
                        .inventoryVersion(5L)
                        .qualityStatus("VERIFIED")
                        .build()))
                .build();
    }

    private static AppRecommendationItemDO item(String listingId, int rank) {
        AppRecommendationItemDO item = new AppRecommendationItemDO();
        item.setRankNo(rank);
        item.setReasonCode("VERIFIED_IN_STOCK");
        item.setListingId(listingId);
        item.setListingNo("NO-" + listingId);
        item.setListingTitle("title-" + listingId);
        item.setPrimaryImageUrl("https://img/" + listingId);
        item.setMerchantId("merchant-1");
        item.setShopId("shop-1");
        item.setCanonicalSpuId("spu-1");
        item.setCanonicalSkuId("sku-" + listingId);
        item.setListingOfferId("offer-" + listingId);
        item.setPriceMinor(39900L);
        item.setCurrencyCode("CNY");
        item.setInventoryVersion(5L);
        item.setAvailableQuantity(BigDecimal.TEN);
        item.setQualityStatus("VERIFIED");
        return item;
    }
}
