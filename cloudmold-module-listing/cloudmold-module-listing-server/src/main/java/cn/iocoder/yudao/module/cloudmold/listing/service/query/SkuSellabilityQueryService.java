package cn.iocoder.yudao.module.cloudmold.listing.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventorySkuAvailabilityView;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3AvailabilityQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.ListingQueryMapper;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceApi;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceView;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class SkuSellabilityQueryService {

    private final ListingQueryMapper queryMapper;
    private final CatalogSkuProjectionApi catalogApi;
    private final QualityConsumerEvidenceApi qualityApi;
    private final InventoryV3AvailabilityQueryApi inventoryApi;

    public SkuSellabilityQueryService(ListingQueryMapper queryMapper,
                                      CatalogSkuProjectionApi catalogApi,
                                      QualityConsumerEvidenceApi qualityApi,
                                      InventoryV3AvailabilityQueryApi inventoryApi) {
        this.queryMapper = queryMapper;
        this.catalogApi = catalogApi;
        this.qualityApi = qualityApi;
        this.inventoryApi = inventoryApi;
    }

    public SkuSellabilityView getBySku(String canonicalSkuId) {
        require(StringUtils.hasText(canonicalSkuId), "canonicalSkuId is required");
        String skuId = canonicalSkuId.trim();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        boolean catalogActive = catalogApi.getActiveSku(skuId) != null;
        QualityConsumerEvidenceView evidence = qualityApi.getLatestBySku(skuId);
        InventorySkuAvailabilityView availability = inventoryApi.getBySku(skuId, Instant.now());
        List<SkuPublicationItem> publications = queryMapper.selectSkuPublications(tenantId, skuId);

        String qualityStatus = evidence == null || !StringUtils.hasText(evidence.getStatus())
                ? "UNVERIFIED" : evidence.getStatus();
        BigDecimal allocatable = availability == null || availability.getAllocatableQuantity() == null
                ? BigDecimal.ZERO : availability.getAllocatableQuantity();
        List<SkuPublicationItem> publishedOffers = publications.stream()
                .filter(item -> Boolean.TRUE.equals(item.getCurrentlyPublished())
                        && Boolean.TRUE.equals(item.getOfferEnabled()))
                .toList();

        List<String> blockers = new ArrayList<>();
        if (!catalogActive) blockers.add("CATALOG_INACTIVE");
        if (!"VERIFIED".equals(qualityStatus)) blockers.add("QUALITY_NOT_VERIFIED");
        if (allocatable.signum() <= 0) blockers.add("NO_ALLOCATABLE_INVENTORY");
        if (publishedOffers.isEmpty()) blockers.add("NO_PUBLISHED_OFFER");

        return SkuSellabilityView.builder()
                .canonicalSkuId(skuId)
                .sellable(blockers.isEmpty())
                .blockingReasonCodes(List.copyOf(blockers))
                .quality(toQuality(evidence, qualityStatus))
                .inventory(toInventory(availability, allocatable))
                .listing(SkuSellabilityView.Listing.builder()
                        .publishedListingCount((int) publications.stream()
                                .filter(item -> Boolean.TRUE.equals(item.getCurrentlyPublished()))
                                .map(SkuPublicationItem::getListingId).distinct().count())
                        .enabledOfferCount(publishedOffers.size())
                        .channels(publishedOffers.stream().map(SkuPublicationItem::getChannelCode)
                                .filter(StringUtils::hasText).distinct().sorted().toList())
                        .publications(publications)
                        .build())
                .build();
    }

    private static SkuSellabilityView.Quality toQuality(QualityConsumerEvidenceView evidence,
                                                        String status) {
        return SkuSellabilityView.Quality.builder()
                .status(status)
                .inspectionTaskId(evidence == null ? null : evidence.getInspectionTaskId())
                .inspectionStatus(evidence == null ? null : evidence.getInspectionStatus())
                .decision(evidence == null ? null : evidence.getDecision())
                .inspectedAt(evidence == null ? null : evidence.getInspectedAt())
                .completedAt(evidence == null ? null : evidence.getCompletedAt())
                .standardId(evidence == null ? null : evidence.getStandardId())
                .standardCode(evidence == null ? null : evidence.getStandardCode())
                .standardVersion(evidence == null ? null : evidence.getStandardVersion())
                .build();
    }

    private static SkuSellabilityView.Inventory toInventory(InventorySkuAvailabilityView availability,
                                                            BigDecimal allocatable) {
        return SkuSellabilityView.Inventory.builder()
                .allocatableQuantity(allocatable)
                .baseUomCode(availability == null ? null : availability.getBaseUomCode())
                .inventoryVersion(availability == null ? null : availability.getInventoryVersion())
                .balanceCount(availability == null || availability.getBalanceCount() == null
                        ? 0 : availability.getBalanceCount())
                .build();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
