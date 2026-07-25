package cn.iocoder.yudao.module.cloudmold.listing.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.listing.api.*;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.ListingHeaderDO;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.ListingOfferDO;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.ListingHeaderMapper;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.ListingOfferMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AppListingQueryService implements AppListingQueryApi {

    private static final int MAX_PAGE_SIZE = 50;

    private final ListingHeaderMapper headerMapper;
    private final ListingOfferMapper offerMapper;

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
        long total = headerMapper.countCurrentPublished(tenantId, keyword, channelCode);
        if (total == 0) {
            return PublishedListingPageView.builder().list(List.of()).total(0L)
                    .pageNo(pageNo).pageSize(pageSize).build();
        }
        long offset = (long) (pageNo - 1) * pageSize;
        List<PublishedListingView> list = headerMapper.selectCurrentPublished(
                        tenantId, keyword, channelCode, offset, pageSize)
                .stream().map(header -> toView(tenantId, header)).toList();
        return PublishedListingPageView.builder().list(list).total(total)
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
