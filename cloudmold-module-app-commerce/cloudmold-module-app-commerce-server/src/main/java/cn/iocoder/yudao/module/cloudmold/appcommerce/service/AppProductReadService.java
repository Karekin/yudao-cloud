package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionApi;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionView;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventorySkuAvailabilityView;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3AvailabilityQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.*;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceApi;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceView;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppProductReadService {

    private final AppListingQueryApi listingQueryApi;
    private final CatalogSkuProjectionApi catalogSkuProjectionApi;
    private final InventoryV3AvailabilityQueryApi inventoryAvailabilityQueryApi;
    private final QualityConsumerEvidenceApi qualityConsumerEvidenceApi;

    @Value("${cloudmold.app-commerce.channel-code:YSHOPPING}")
    private String appChannel;

    public AppProductPageView page(String keyword, int pageNo, int pageSize) {
        PublishedListingPageView result = listingQueryApi.listPublished(PublishedListingPageQuery.builder()
                .keyword(keyword).channelCode(appChannel).pageNo(pageNo).pageSize(pageSize).build());
        List<AppProductView> products = result.getList().stream().map(this::assemble).toList();
        return AppProductPageView.builder().list(products)
                .total(result.getTotal()).pageNo(result.getPageNo()).pageSize(result.getPageSize()).build();
    }

    public AppProductView detail(String listingId) {
        return assemble(listingQueryApi.requirePublished(listingId));
    }

    private AppProductView assemble(PublishedListingView listing) {
        require(listing != null && listing.getOffers() != null && !listing.getOffers().isEmpty(),
                "published listing has no enabled offer");
        List<SkuAssembly> assemblies = listing.getOffers().stream().map(offer -> assembleSku(offer)).toList();
        String qualityStatus = aggregateQuality(assemblies);
        List<AppProductView.Media> media = listing.getPrimaryImageUrl() == null ? List.of()
                : List.of(AppProductView.Media.builder().type("IMAGE").url(listing.getPrimaryImageUrl()).sort(1).build());
        return AppProductView.builder().listingId(listing.getListingId()).listingNo(listing.getListingNo())
                .title(listing.getTitle()).primaryImageUrl(listing.getPrimaryImageUrl()).media(media)
                .merchantId(listing.getMerchantId()).shopId(listing.getShopId())
                .canonicalSpuId(listing.getCanonicalSpuId()).listingRevision(listing.getListingRevision())
                .listingVersion(listing.getListingVersion())
                .qualitySummary(AppProductView.QualitySummary.builder().status(qualityStatus).build())
                .skus(assemblies.stream().map(SkuAssembly::view).toList()).build();
    }

    private SkuAssembly assembleSku(ListingOfferView offer) {
        CatalogSkuProjectionView catalog = catalogSkuProjectionApi.getActiveSku(offer.getCanonicalSkuId());
        require(catalog != null, "published listing references a non-active canonical SKU");
        InventorySkuAvailabilityView inventory = inventoryAvailabilityQueryApi.getBySku(
                offer.getCanonicalSkuId(), Instant.now());
        require(inventory != null, "canonical SKU inventory availability is unavailable");
        QualityConsumerEvidenceView quality = qualityConsumerEvidenceApi.getLatestBySku(offer.getCanonicalSkuId());
        require(quality != null, "canonical SKU quality evidence is unavailable");
        BigDecimal available = inventory.getAllocatableQuantity() == null
                ? BigDecimal.ZERO : inventory.getAllocatableQuantity();
        AppProductView.Sku view = AppProductView.Sku.builder()
                .canonicalSkuId(catalog.getCanonicalSkuId()).skuCode(catalog.getSkuCode())
                .colorCode(catalog.getColorCode()).colorName(catalog.getColorName())
                .sizeCode(catalog.getSizeCode()).sizeName(catalog.getSizeName())
                .barcode(catalog.getPrimaryBarcode()).listingOfferId(offer.getListingOfferId())
                .priceMinor(offer.getPriceMinor()).currencyCode(offer.getCurrencyCode())
                .enabled(Boolean.TRUE.equals(offer.getEnabled()) && "VERIFIED".equals(quality.getStatus()))
                .availableQuantity(available)
                .inStock(available.signum() > 0 && "VERIFIED".equals(quality.getStatus()))
                .inventoryVersion(inventory.getInventoryVersion()).qualityStatus(quality.getStatus()).build();
        return new SkuAssembly(view, quality);
    }

    private static String aggregateQuality(List<SkuAssembly> assemblies) {
        if (assemblies.stream().anyMatch(item -> "REJECTED".equals(item.evidence().getStatus()))) {
            return "REJECTED";
        }
        if (assemblies.stream().allMatch(item -> "VERIFIED".equals(item.evidence().getStatus()))) {
            return "VERIFIED";
        }
        return "PARTIALLY_VERIFIED";
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record SkuAssembly(AppProductView.Sku view, QualityConsumerEvidenceView evidence) {}
}
