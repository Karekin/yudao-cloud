package cn.iocoder.yudao.module.cloudmold.listing.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionApi;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionView;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventorySkuAvailabilityView;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3AvailabilityQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.ListingQueryMapper;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceApi;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkuSellabilityQueryServiceTest {

    private final ListingQueryMapper mapper = mock(ListingQueryMapper.class);
    private final CatalogSkuProjectionApi catalogApi = mock(CatalogSkuProjectionApi.class);
    private final QualityConsumerEvidenceApi qualityApi = mock(QualityConsumerEvidenceApi.class);
    private final InventoryV3AvailabilityQueryApi inventoryApi = mock(InventoryV3AvailabilityQueryApi.class);
    private final SkuSellabilityQueryService service =
            new SkuSellabilityQueryService(mapper, catalogApi, qualityApi, inventoryApi);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(162L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldExposeVerifiedSellableSkuWithOperationalEvidence() {
        String skuId = "56a17cb1-1bc9-4f78-8d31-ea9774b894a6";
        CatalogSkuProjectionView catalog = new CatalogSkuProjectionView();
        catalog.setCanonicalSkuId(skuId);
        when(catalogApi.getActiveSku(skuId)).thenReturn(catalog);
        when(qualityApi.getLatestBySku(skuId)).thenReturn(QualityConsumerEvidenceView.builder()
                .canonicalSkuId(skuId).status("VERIFIED").decision("PASS")
                .inspectionTaskId("task-1").inspectionStatus("COMPLETED")
                .standardCode("DEWU-LUXURY-BAG").standardVersion(1L).build());
        when(inventoryApi.getBySku(org.mockito.ArgumentMatchers.eq(skuId), any(Instant.class)))
                .thenReturn(InventorySkuAvailabilityView.builder().canonicalSkuId(skuId)
                        .allocatableQuantity(new BigDecimal("1")).baseUomCode("PCS")
                        .inventoryVersion(3L).balanceCount(1).build());
        when(mapper.selectSkuPublications(162L, skuId)).thenReturn(List.of(
                publication("listing-1", true, true, "YSHOPPING")));

        SkuSellabilityView result = service.getBySku(skuId);

        assertThat(result.isSellable()).isTrue();
        assertThat(result.getBlockingReasonCodes()).isEmpty();
        assertThat(result.getQuality().getStatus()).isEqualTo("VERIFIED");
        assertThat(result.getQuality().getDecision()).isEqualTo("PASS");
        assertThat(result.getQuality().getStandardCode()).isEqualTo("DEWU-LUXURY-BAG");
        assertThat(result.getInventory().getAllocatableQuantity()).isEqualByComparingTo("1");
        assertThat(result.getListing().getPublishedListingCount()).isEqualTo(1);
        assertThat(result.getListing().getEnabledOfferCount()).isEqualTo(1);
        assertThat(result.getListing().getChannels()).containsExactly("YSHOPPING");
    }

    @Test
    void shouldExplainEveryBlockingGateWithoutInventingSuccess() {
        String skuId = "sku-blocked";
        when(catalogApi.getActiveSku(skuId)).thenReturn(null);
        when(qualityApi.getLatestBySku(skuId)).thenReturn(QualityConsumerEvidenceView.builder()
                .canonicalSkuId(skuId).status("REJECTED").decision("FAIL").build());
        when(inventoryApi.getBySku(org.mockito.ArgumentMatchers.eq(skuId), any(Instant.class)))
                .thenReturn(InventorySkuAvailabilityView.builder().canonicalSkuId(skuId)
                        .allocatableQuantity(BigDecimal.ZERO).baseUomCode("PCS").build());
        when(mapper.selectSkuPublications(162L, skuId)).thenReturn(List.of(
                publication("listing-disabled", false, true, "YSHOPPING")));

        SkuSellabilityView result = service.getBySku(skuId);

        assertThat(result.isSellable()).isFalse();
        assertThat(result.getBlockingReasonCodes()).containsExactly(
                "CATALOG_INACTIVE",
                "QUALITY_NOT_VERIFIED",
                "NO_ALLOCATABLE_INVENTORY",
                "NO_PUBLISHED_OFFER");
        assertThat(result.getQuality().getStatus()).isEqualTo("REJECTED");
        assertThat(result.getListing().getPublishedListingCount()).isEqualTo(1);
        assertThat(result.getListing().getEnabledOfferCount()).isZero();
    }

    private static SkuPublicationItem publication(String listingId, boolean enabled,
                                                  boolean currentlyPublished, String channelCode) {
        return new SkuPublicationItem()
                .setListingId(listingId)
                .setListingNo("CML-" + listingId)
                .setChannelCode(channelCode)
                .setListingStatus("PUBLISHED")
                .setOfferEnabled(enabled)
                .setCurrentlyPublished(currentlyPublished)
                .setPriceMinor(100_00L)
                .setCurrencyCode("CNY");
    }
}
