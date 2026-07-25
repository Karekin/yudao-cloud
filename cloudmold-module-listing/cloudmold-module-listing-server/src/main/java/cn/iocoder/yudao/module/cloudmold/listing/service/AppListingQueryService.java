package cn.iocoder.yudao.module.cloudmold.listing.service;

import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionApi;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionView;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventorySkuAvailabilityView;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3AvailabilityQueryApi;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.listing.api.*;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.ListingHeaderDO;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.ListingOfferDO;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.ListingHeaderMapper;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.ListingOfferMapper;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AppListingQueryService implements AppListingQueryApi {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int SELLABLE_SCAN_BATCH_SIZE = 200;

    private final ListingHeaderMapper headerMapper;
    private final ListingOfferMapper offerMapper;
    private final CatalogSkuProjectionApi catalogSkuProjectionApi;
    private final InventoryV3AvailabilityQueryApi inventoryAvailabilityQueryApi;
    private final QualityConsumerEvidenceApi qualityConsumerEvidenceApi;

    @Override
    public PublishedListingPageView listPublished(PublishedListingPageQuery query) {
        require(query != null, "published listing query is required");
        int pageNo = query.getPageNo() == null ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() == null ? 20 : query.getPageSize();
        require(pageNo > 0, "pageNo must be positive");
        require(pageSize > 0 && pageSize <= MAX_PAGE_SIZE, "pageSize must be between 1 and 50");
        String keyword = normalize(query.getKeyword());
        String channelCode = normalize(query.getChannelCode());
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        List<PublishedListingView> sellable = collectSellable(tenantId, keyword, channelCode);
        if (sellable.isEmpty()) {
            return PublishedListingPageView.builder().list(List.of()).total(0L)
                    .pageNo(pageNo).pageSize(pageSize).build();
        }
        int fromIndex = Math.min((pageNo - 1) * pageSize, sellable.size());
        int toIndex = Math.min(fromIndex + pageSize, sellable.size());
        List<PublishedListingView> list = sellable.subList(fromIndex, toIndex);
        return PublishedListingPageView.builder().list(list).total((long) sellable.size())
                .pageNo(pageNo).pageSize(pageSize).build();
    }

    @Override
    public PublishedListingView requirePublished(String listingId) {
        require(StringUtils.hasText(listingId), "listingId is required");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ListingHeaderDO header = headerMapper.selectCurrentPublishedById(tenantId, listingId.trim());
        require(header != null, "published listing does not exist");
        return toView(tenantId, header);
    }

    private PublishedListingView toView(Long tenantId, ListingHeaderDO header) {
        List<ListingOfferView> offers = offerMapper.selectByListingRevision(
                        tenantId, header.getListingId(), header.getRevision()).stream()
                .filter(offer -> Boolean.TRUE.equals(offer.getEnabled()))
                .map(AppListingQueryService::toOffer).toList();
        require(!offers.isEmpty(), "published listing has no enabled offer");
        return PublishedListingView.builder().listingId(header.getListingId()).listingNo(header.getListingNo())
                .title(header.getTitle()).primaryImageUrl(header.getPrimaryImageUrl())
                .merchantId(header.getMerchantId()).shopId(header.getShopId())
                .channelCode(header.getChannelCode()).canonicalSpuId(header.getCanonicalSpuId())
                .listingRevision(header.getRevision()).listingVersion(header.getVersion())
                .offers(offers).build();
    }

    private List<PublishedListingView> collectSellable(Long tenantId, String keyword, String channelCode) {
        List<PublishedListingView> result = new java.util.ArrayList<>();
        long offset = 0L;
        while (true) {
            List<ListingHeaderDO> batch = headerMapper.selectCurrentPublished(
                    tenantId, keyword, channelCode, offset, SELLABLE_SCAN_BATCH_SIZE);
            if (batch.isEmpty()) {
                return result;
            }
            for (ListingHeaderDO header : batch) {
                PublishedListingView sellable = toSellableView(tenantId, header);
                if (sellable != null) {
                    result.add(sellable);
                }
            }
            offset += batch.size();
        }
    }

    private PublishedListingView toSellableView(Long tenantId, ListingHeaderDO header) {
        PublishedListingView listing = toView(tenantId, header);
        List<ListingOfferView> sellableOffers = listing.getOffers().stream()
                .filter(this::isOfferSellable)
                .toList();
        if (sellableOffers.isEmpty()) {
            return null;
        }
        return PublishedListingView.builder()
                .listingId(listing.getListingId())
                .listingNo(listing.getListingNo())
                .title(listing.getTitle())
                .primaryImageUrl(listing.getPrimaryImageUrl())
                .merchantId(listing.getMerchantId())
                .shopId(listing.getShopId())
                .channelCode(listing.getChannelCode())
                .canonicalSpuId(listing.getCanonicalSpuId())
                .listingRevision(listing.getListingRevision())
                .listingVersion(listing.getListingVersion())
                .offers(sellableOffers)
                .build();
    }

    private boolean isOfferSellable(ListingOfferView offer) {
        if (!Boolean.TRUE.equals(offer.getEnabled())) {
            return false;
        }
        CatalogSkuProjectionView sku = catalogSkuProjectionApi.getActiveSku(offer.getCanonicalSkuId());
        if (sku == null || !Objects.equals(sku.getCanonicalSkuId(), offer.getCanonicalSkuId())) {
            return false;
        }
        var quality = qualityConsumerEvidenceApi.getLatestBySku(offer.getCanonicalSkuId());
        if (quality == null || !"VERIFIED".equals(quality.getStatus())) {
            return false;
        }
        InventorySkuAvailabilityView inventory = inventoryAvailabilityQueryApi.getBySku(
                offer.getCanonicalSkuId(), Instant.now());
        return inventory != null
                && inventory.getAllocatableQuantity() != null
                && inventory.getAllocatableQuantity().compareTo(BigDecimal.ZERO) > 0;
    }

    private static ListingOfferView toOffer(ListingOfferDO offer) {
        return ListingOfferView.builder().listingOfferId(offer.getListingOfferId())
                .canonicalSkuId(offer.getCanonicalSkuId()).revision(offer.getRevision())
                .priceMinor(offer.getPriceMinor()).currencyCode(offer.getCurrencyCode())
                .enabled(offer.getEnabled()).externalOfferId(offer.getExternalOfferId()).build();
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
