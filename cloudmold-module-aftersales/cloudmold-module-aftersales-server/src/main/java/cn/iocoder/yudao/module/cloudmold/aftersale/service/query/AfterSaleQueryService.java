package cn.iocoder.yudao.module.cloudmold.aftersale.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aftersale.controller.admin.vo.AfterSalePageReqVO;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql.AfterSalePageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AfterSaleQueryService {

    private final AfterSalePageMapper pageMapper;

    public PageResult<AfterSalePageItem> getPage(AfterSalePageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String afterSaleId = normalize(request.getAfterSaleId());
        String afterSaleNo = normalize(request.getAfterSaleNo());
        String orderId = normalize(request.getOrderId());
        String orderNo = normalize(request.getOrderNo());
        String canonicalSkuId = normalize(request.getCanonicalSkuId());
        String caseStatus = normalizeUpper(request.getCaseStatus());
        String refundStatus = normalizeUpper(request.getRefundStatus());
        String afterSaleType = normalizeUpper(request.getAfterSaleType());
        String reasonCode = normalizeUpper(request.getReasonCode());
        String responsibility = normalizeUpper(request.getResponsibility());
        long total = pageMapper.countPage(tenantId, afterSaleId, afterSaleNo, orderId, orderNo, canonicalSkuId,
                caseStatus, refundStatus, afterSaleType, reasonCode, responsibility);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(pageMapper.selectPage(tenantId, afterSaleId, afterSaleNo, orderId, orderNo,
                canonicalSkuId, caseStatus, refundStatus, afterSaleType, reasonCode, responsibility, offset,
                request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
