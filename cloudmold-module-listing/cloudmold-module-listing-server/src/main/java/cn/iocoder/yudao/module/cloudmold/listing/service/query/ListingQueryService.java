package cn.iocoder.yudao.module.cloudmold.listing.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.listing.controller.admin.vo.ListingPageReqVO;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.ListingQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ListingQueryService {

    private final ListingQueryMapper queryMapper;

    public PageResult<ListingPageItem> getListingPage(ListingPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String listingId = normalize(request.getListingId());
        String listingNo = normalize(request.getListingNo());
        String title = normalize(request.getTitle());
        String merchantId = normalize(request.getMerchantId());
        String shopId = normalize(request.getShopId());
        String channelCode = normalizeUpper(request.getChannelCode());
        String canonicalSpuId = normalize(request.getCanonicalSpuId());
        String status = normalizeUpper(request.getStatus());
        long total = queryMapper.countListingPage(tenantId, listingId, listingNo, title, merchantId, shopId,
                channelCode, canonicalSpuId, status);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(queryMapper.selectListingPage(tenantId, listingId, listingNo, title, merchantId,
                shopId, channelCode, canonicalSpuId, status, offset, request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
