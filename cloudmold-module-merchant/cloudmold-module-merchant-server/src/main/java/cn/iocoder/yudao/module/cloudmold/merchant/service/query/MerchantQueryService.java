package cn.iocoder.yudao.module.cloudmold.merchant.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.merchant.controller.admin.vo.MerchantPageReqVO;
import cn.iocoder.yudao.module.cloudmold.merchant.controller.admin.vo.MerchantShopPageReqVO;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.mysql.MerchantQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * CloudMold 规范商家只读查询服务。
 * 显式按当前租户隔离；走纯 SQL 分页，不依赖 MyBatis-Plus tenant 插件。
 */
@Service
@RequiredArgsConstructor
public class MerchantQueryService {

    private final MerchantQueryMapper queryMapper;

    public PageResult<MerchantPageItem> getMerchantPage(MerchantPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String merchantCode = normalize(request.getMerchantCode());
        String legalName = normalize(request.getLegalName());
        String status = normalize(request.getStatus());
        long total = queryMapper.countMerchantPage(tenantId, merchantCode, legalName, status);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(queryMapper.selectMerchantPage(tenantId, merchantCode,
                legalName, status, offset, request.getPageSize()), total);
    }

    public PageResult<MerchantShopPageItem> getShopPage(MerchantShopPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String merchantId = normalize(request.getMerchantId());
        String channelCode = normalize(request.getChannelCode());
        String status = normalize(request.getStatus());
        long total = queryMapper.countShopPage(tenantId, merchantId, channelCode, status);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(queryMapper.selectShopPage(tenantId, merchantId, channelCode,
                status, offset, request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
