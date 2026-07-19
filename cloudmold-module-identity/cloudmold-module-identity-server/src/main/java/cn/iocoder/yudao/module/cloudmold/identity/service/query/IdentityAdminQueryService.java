package cn.iocoder.yudao.module.cloudmold.identity.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.identity.controller.admin.vo.IdentityOperationPageReqVO;
import cn.iocoder.yudao.module.cloudmold.identity.controller.admin.vo.IdentityPrincipalPageReqVO;
import cn.iocoder.yudao.module.cloudmold.identity.controller.admin.vo.IdentitySourcePageReqVO;
import cn.iocoder.yudao.module.cloudmold.identity.dal.mysql.IdentityQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * CloudMold 规范身份只读查询服务。显式按当前租户隔离；走纯 SQL 分页。
 * 类名刻意区别于 {@code service/IdentityQueryService}（IdentityQueryApi 实现）以避免 Spring bean 名冲突。
 */
@Service
@RequiredArgsConstructor
public class IdentityAdminQueryService {

    private final IdentityQueryMapper queryMapper;

    public PageResult<IdentityPrincipalPageItem> getPrincipalPage(IdentityPrincipalPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String principalType = normalize(request.getPrincipalType());
        String status = normalize(request.getStatus());
        long total = queryMapper.countPrincipalPage(tenantId, principalType, status);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(queryMapper.selectPrincipalPage(tenantId, principalType,
                status, offset, request.getPageSize()), total);
    }

    public PageResult<IdentitySourcePageItem> getSourcePage(IdentitySourcePageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String principalId = normalize(request.getPrincipalId());
        String sourceSystem = normalize(request.getSourceSystem());
        String sourceType = normalize(request.getSourceType());
        String status = normalize(request.getStatus());
        long total = queryMapper.countSourcePage(tenantId, principalId, sourceSystem, sourceType, status);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(queryMapper.selectSourcePage(tenantId, principalId, sourceSystem,
                sourceType, status, offset, request.getPageSize()), total);
    }

    public PageResult<IdentityOperationPageItem> getOperationPage(IdentityOperationPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String principalId = normalize(request.getPrincipalId());
        String commandType = normalize(request.getCommandType());
        Integer status = request.getStatus(); // operation.status 是 tinyint，不做 normalize
        long total = queryMapper.countOperationPage(tenantId, principalId, commandType, status);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(queryMapper.selectOperationPage(tenantId, principalId, commandType,
                status, offset, request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
