package cn.iocoder.yudao.module.cloudmold.listing.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionApi;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionView;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventorySkuAvailabilityView;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3AvailabilityQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.ListingOfferView;
import cn.iocoder.yudao.module.cloudmold.listing.api.PublishedListingPageQuery;
import cn.iocoder.yudao.module.cloudmold.listing.api.PublishedListingPageView;
import cn.iocoder.yudao.module.cloudmold.listing.api.PublishedListingView;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.ListingHeaderDO;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.ListingHeaderMapper;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.ListingOfferMapper;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.ListingOfferDO;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceApi;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppListingQueryServiceTest {

    private final ListingHeaderMapper headerMapper = mock(ListingHeaderMapper.class);
    private final ListingOfferMapper offerMapper = mock(ListingOfferMapper.class);
    private final CatalogSkuProjectionApi catalogSkuProjectionApi = mock(CatalogSkuProjectionApi.class);
    private final InventoryV3AvailabilityQueryApi inventoryAvailabilityQueryApi = mock(InventoryV3AvailabilityQueryApi.class);
    private final QualityConsumerEvidenceApi qualityConsumerEvidenceApi = mock(QualityConsumerEvidenceApi.class);

    private final AppListingQueryService service = new AppListingQueryService(
            headerMapper, offerMapper, catalogSkuProjectionApi, inventoryAvailabilityQueryApi, qualityConsumerEvidenceApi);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void listPublishedShouldReturnExactSellableTotalAfterFiltering() {
        ListingHeaderDO invalid = header("listing-invalid", 2);
        ListingHeaderDO valid = header("listing-valid", 1);
        when(headerMapper.selectCurrentPublished(7L, "coat", "YSHOPPING", 0L, 200))
                .thenReturn(List.of(invalid, valid));
        when(headerMapper.selectCurrentPublished(7L, "coat", "YSHOPPING", 2L, 200))
                .thenReturn(List.of());
        when(offerMapper.selectByListingRevision(7L, "listing-invalid", 2))
                .thenReturn(List.of(offer("offer-invalid", "sku-invalid", true)));
        when(offerMapper.selectByListingRevision(7L, "listing-valid", 1))
                .thenReturn(List.of(offer("offer-valid", "sku-valid", true)));
        when(catalogSkuProjectionApi.getActiveSku("sku-invalid")).thenReturn(null);
        when(catalogSkuProjectionApi.getActiveSku("sku-valid")).thenReturn(sku("sku-valid"));
        when(qualityConsumerEvidenceApi.getLatestBySku("sku-valid"))
                .thenReturn(QualityConsumerEvidenceView.builder().canonicalSkuId("sku-valid").status("VERIFIED").build());
        when(inventoryAvailabilityQueryApi.getBySku(eq("sku-valid"), any()))
                .thenReturn(InventorySkuAvailabilityView.builder()
                        .canonicalSkuId("sku-valid").allocatableQuantity(BigDecimal.ONE).inventoryVersion(3L).build());

        PublishedListingPageView result = service.listPublished(PublishedListingPageQuery.builder()
                .keyword("coat").channelCode("YSHOPPING").pageNo(1).pageSize(20).build());

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getList()).extracting(PublishedListingView::getListingId)
                .containsExactly("listing-valid");
    }

    @Test
    void listPublishedShouldPaginateAgainstSellableResultsInsteadOfRawPublishedCount() {
        ListingHeaderDO first = header("listing-1", 1);
        ListingHeaderDO second = header("listing-2", 1);
        ListingHeaderDO third = header("listing-3", 1);
        when(headerMapper.selectCurrentPublished(7L, null, "YSHOPPING", 0L, 200))
                .thenReturn(List.of(first, second, third));
        when(headerMapper.selectCurrentPublished(7L, null, "YSHOPPING", 3L, 200))
                .thenReturn(List.of());
        when(offerMapper.selectByListingRevision(anyLong(), anyString(), anyInt())).thenAnswer(invocation ->
                List.of(offer("offer-" + invocation.getArgument(1), "sku-" + invocation.getArgument(1), true)));
        when(catalogSkuProjectionApi.getActiveSku(anyString())).thenAnswer(invocation -> sku(invocation.getArgument(0)));
        when(qualityConsumerEvidenceApi.getLatestBySku(anyString()))
                .thenReturn(QualityConsumerEvidenceView.builder().status("VERIFIED").build());
        when(inventoryAvailabilityQueryApi.getBySku(anyString(), any())).thenAnswer(invocation ->
                InventorySkuAvailabilityView.builder()
                        .canonicalSkuId(invocation.getArgument(0))
                        .allocatableQuantity(BigDecimal.ONE)
                        .inventoryVersion(1L).build());

        PublishedListingPageView result = service.listPublished(PublishedListingPageQuery.builder()
                .channelCode("YSHOPPING").pageNo(2).pageSize(2).build());

        assertThat(result.getTotal()).isEqualTo(3L);
        assertThat(result.getList()).extracting(PublishedListingView::getListingId)
                .containsExactly("listing-3");
    }

    private static ListingHeaderDO header(String listingId, int revision) {
        return new ListingHeaderDO()
                .setListingId(listingId)
                .setListingNo("NO-" + listingId)
                .setTitle("Title " + listingId)
                .setCanonicalSpuId("spu-" + listingId)
                .setRevision(revision)
                .setVersion(1L)
                .setChannelCode("YSHOPPING");
    }

    private static ListingOfferDO offer(String offerId, String skuId, boolean enabled) {
        return new ListingOfferDO()
                .setListingOfferId(offerId)
                .setCanonicalSkuId(skuId)
                .setRevision(1)
                .setPriceMinor(29900L)
                .setCurrencyCode("CNY")
                .setEnabled(enabled);
    }

    private static CatalogSkuProjectionView sku(String skuId) {
        CatalogSkuProjectionView view = new CatalogSkuProjectionView();
        view.setCanonicalSkuId(skuId);
        return view;
    }
}
