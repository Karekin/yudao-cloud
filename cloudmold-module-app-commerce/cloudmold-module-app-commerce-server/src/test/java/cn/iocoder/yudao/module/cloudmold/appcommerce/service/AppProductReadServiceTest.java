package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionApi;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionView;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventorySkuAvailabilityView;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3AvailabilityQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.AppListingQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.ListingOfferView;
import cn.iocoder.yudao.module.cloudmold.listing.api.PublishedListingPageQuery;
import cn.iocoder.yudao.module.cloudmold.listing.api.PublishedListingPageView;
import cn.iocoder.yudao.module.cloudmold.listing.api.PublishedListingView;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceApi;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AppProductReadServiceTest {

    private final AppListingQueryApi listingQueryApi = mock(AppListingQueryApi.class);
    private final CatalogSkuProjectionApi catalogSkuProjectionApi = mock(CatalogSkuProjectionApi.class);
    private final InventoryV3AvailabilityQueryApi inventoryAvailabilityQueryApi = mock(InventoryV3AvailabilityQueryApi.class);
    private final QualityConsumerEvidenceApi qualityConsumerEvidenceApi = mock(QualityConsumerEvidenceApi.class);

    private final AppProductReadService service = new AppProductReadService(listingQueryApi, catalogSkuProjectionApi,
            inventoryAvailabilityQueryApi, qualityConsumerEvidenceApi);

    @Test
    void pageShouldAlwaysUseYShoppingChannelForConsumerCatalog() {
        ReflectionTestUtils.setField(service, "appChannel", "YSHOPPING_INTERNAL");
        when(listingQueryApi.listPublished(any())).thenReturn(PublishedListingPageView.builder()
                .list(List.of()).total(0L).pageNo(2).pageSize(12).build());

        AppProductPageView result = service.page("  coat  ", 2, 12);

        ArgumentCaptor<PublishedListingPageQuery> query = ArgumentCaptor.forClass(PublishedListingPageQuery.class);
        verify(listingQueryApi).listPublished(query.capture());
        assertThat(query.getValue().getChannelCode()).isEqualTo("YSHOPPING_INTERNAL");
        assertThat(query.getValue().getKeyword()).isEqualTo("  coat  ");
        assertThat(result.getTotal()).isZero();
    }

    @Test
    void pageShouldTrustSellableListingContractAndKeepTotalAligned() {
        ReflectionTestUtils.setField(service, "appChannel", "YSHOPPING_INTERNAL");
        when(listingQueryApi.listPublished(any())).thenReturn(PublishedListingPageView.builder()
                .list(List.of(PublishedListingView.builder()
                        .listingId("listing-1")
                        .listingNo("LIST-1")
                        .title("Wool Coat")
                        .canonicalSpuId("spu-1")
                        .offers(List.of(ListingOfferView.builder()
                                .listingOfferId("offer-1")
                                .canonicalSkuId("sku-1")
                                .priceMinor(29900L)
                                .currencyCode("CNY")
                                .enabled(true)
                                .build()))
                        .build()))
                .total(1L).pageNo(1).pageSize(20).build());
        CatalogSkuProjectionView projection = new CatalogSkuProjectionView();
        projection.setCanonicalSkuId("sku-1");
        projection.setSkuCode("SKU-1");
        when(catalogSkuProjectionApi.getActiveSku("sku-1")).thenReturn(projection);
        when(inventoryAvailabilityQueryApi.getBySku(eq("sku-1"), any())).thenReturn(InventorySkuAvailabilityView.builder()
                .canonicalSkuId("sku-1").allocatableQuantity(BigDecimal.ONE).inventoryVersion(3L).build());
        when(qualityConsumerEvidenceApi.getLatestBySku("sku-1")).thenReturn(QualityConsumerEvidenceView.builder()
                .canonicalSkuId("sku-1").status("VERIFIED").build());

        AppProductPageView result = service.page(null, 1, 20);

        assertThat(result.getList()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1L);
    }

    @Test
    void detailShouldDisableUnverifiedSkuEvenWhenInternalEvidenceExists() {
        when(listingQueryApi.requirePublished("listing-1")).thenReturn(PublishedListingView.builder()
                .listingId("listing-1")
                .listingNo("LIST-1")
                .title("Wool Coat")
                .canonicalSpuId("spu-1")
                .offers(List.of(ListingOfferView.builder()
                        .listingOfferId("offer-1")
                        .canonicalSkuId("sku-1")
                        .priceMinor(29900L)
                        .currencyCode("CNY")
                        .enabled(true)
                        .build()))
                .build());
        CatalogSkuProjectionView projection = new CatalogSkuProjectionView();
        projection.setCanonicalSkuId("sku-1");
        projection.setSkuCode("SKU-1");
        projection.setColorCode("BLACK");
        projection.setColorName("Black");
        projection.setSizeCode("L");
        projection.setSizeName("Large");
        projection.setPrimaryBarcode("690000000001");
        when(catalogSkuProjectionApi.getActiveSku("sku-1")).thenReturn(projection);
        when(inventoryAvailabilityQueryApi.getBySku(eq("sku-1"), any())).thenReturn(InventorySkuAvailabilityView.builder()
                .canonicalSkuId("sku-1").allocatableQuantity(new BigDecimal("5.000000")).inventoryVersion(3L).build());
        when(qualityConsumerEvidenceApi.getLatestBySku("sku-1")).thenReturn(QualityConsumerEvidenceView.builder()
                .canonicalSkuId("sku-1").status("PENDING")
                .evidenceToken("https://unsafe.example/evidence/1")
                .build());

        AppProductView result = service.detail("listing-1");

        assertThat(result.getQualitySummary().getStatus()).isEqualTo("PARTIALLY_VERIFIED");
        assertThat(result.getSkus()).singleElement().satisfies(sku -> {
            assertThat(sku.getEnabled()).isFalse();
            assertThat(sku.getInStock()).isFalse();
            assertThat(sku.getQualityStatus()).isEqualTo("PENDING");
        });
    }

    @Test
    void detailShouldFailClosedWhenQualityEvidenceIsMissing() {
        when(listingQueryApi.requirePublished("listing-1")).thenReturn(PublishedListingView.builder()
                .listingId("listing-1")
                .canonicalSpuId("spu-1")
                .offers(List.of(ListingOfferView.builder()
                        .listingOfferId("offer-1")
                        .canonicalSkuId("sku-1")
                        .priceMinor(29900L)
                        .currencyCode("CNY")
                        .enabled(true)
                        .build()))
                .build());
        CatalogSkuProjectionView projection = new CatalogSkuProjectionView();
        projection.setCanonicalSkuId("sku-1");
        projection.setSkuCode("SKU-1");
        when(catalogSkuProjectionApi.getActiveSku("sku-1")).thenReturn(projection);
        when(inventoryAvailabilityQueryApi.getBySku(eq("sku-1"), any())).thenReturn(InventorySkuAvailabilityView.builder()
                .canonicalSkuId("sku-1").allocatableQuantity(BigDecimal.ONE).inventoryVersion(3L).build());

        assertThatThrownBy(() -> service.detail("listing-1"))
                .hasMessage("canonical SKU quality evidence is unavailable");
    }
}
