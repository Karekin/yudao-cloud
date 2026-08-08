package cn.iocoder.yudao.module.cloudmold.crm.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.crm.api.*;
import cn.iocoder.yudao.module.cloudmold.crm.dal.mysql.CrmQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CrmQueryService implements CrmQueryApi {

    private final CrmQueryMapper mapper;

    @Override
    public CrmWorkbenchView getWorkbench(String ownerPrincipalId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String owner = requireText(ownerPrincipalId, "ownerPrincipalId");
        LocalDateTime now = LocalDateTime.now();
        return CrmWorkbenchView.builder()
                .ownerPrincipalId(owner)
                .ownedCustomerCount(mapper.countOwnedCustomers(tenantId, owner))
                .poolCustomerCount(mapper.countPoolCustomers(tenantId))
                .ownedLeadCount(mapper.countOwnedLeads(tenantId, owner))
                .openOpportunityCount(mapper.countOpenOpportunities(tenantId, owner))
                .dueFollowUpCount(mapper.countDueFollowUps(tenantId, owner, now, now.plusDays(7)))
                .overdueFollowUpCount(mapper.countOverdueFollowUps(tenantId, owner, now))
                .upcomingFollowUps(mapper.selectUpcomingFollowUps(tenantId, owner, now, 10))
                .build();
    }

    public CrmCustomerView requireCustomer(String customerId) {
        String requiredId = requireText(customerId, "customerId");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        List<CrmCustomerView> rows = mapper.selectCustomers(tenantId, requiredId, null, null,
                null, null, null, null, null, null, null, null, 0, 1);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("customer does not exist");
        }
        return rows.get(0);
    }

    @Override
    public PageResult<CrmCustomerView> getCustomers(CrmCustomerPageQuery query) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        long total = mapper.countCustomers(tenantId, normalize(query.getCustomerId()), normalize(query.getCustomerCode()),
                normalize(query.getKeyword()), normalizeUpper(query.getLifecycleStatus()), normalizeUpper(query.getPoolStatus()),
                normalize(query.getOwnerPrincipalId()), normalizeUpper(query.getSourceCode()), normalizeUpper(query.getIndustryCode()),
                normalizeUpper(query.getRegionCode()), query.getCreatedAtFrom(), query.getCreatedAtTo());
        if (total == 0) return PageResult.empty();
        return new PageResult<>(mapper.selectCustomers(tenantId, normalize(query.getCustomerId()), normalize(query.getCustomerCode()),
                normalize(query.getKeyword()), normalizeUpper(query.getLifecycleStatus()), normalizeUpper(query.getPoolStatus()),
                normalize(query.getOwnerPrincipalId()), normalizeUpper(query.getSourceCode()), normalizeUpper(query.getIndustryCode()),
                normalizeUpper(query.getRegionCode()), query.getCreatedAtFrom(), query.getCreatedAtTo(), offset(query), query.getPageSize()), total);
    }

    @Override
    public PageResult<CrmLeadView> getLeads(CrmLeadPageQuery query) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        long total = mapper.countLeads(tenantId, normalize(query.getLeadId()), normalize(query.getLeadCode()),
                normalize(query.getKeyword()), normalizeUpper(query.getStatus()), normalize(query.getOwnerPrincipalId()),
                normalizeUpper(query.getSourceCode()), query.getCreatedAtFrom(), query.getCreatedAtTo());
        if (total == 0) return PageResult.empty();
        return new PageResult<>(mapper.selectLeads(tenantId, normalize(query.getLeadId()), normalize(query.getLeadCode()),
                normalize(query.getKeyword()), normalizeUpper(query.getStatus()), normalize(query.getOwnerPrincipalId()),
                normalizeUpper(query.getSourceCode()), query.getCreatedAtFrom(), query.getCreatedAtTo(), offset(query),
                query.getPageSize()), total);
    }

    @Override
    public PageResult<CrmContactView> getContacts(CrmContactPageQuery query) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        long total = mapper.countContacts(tenantId, normalize(query.getContactId()), normalize(query.getCustomerId()),
                normalize(query.getKeyword()), normalizeUpper(query.getStatus()), query.getIsPrimary(),
                query.getCreatedAtFrom(), query.getCreatedAtTo());
        if (total == 0) return PageResult.empty();
        return new PageResult<>(mapper.selectContacts(tenantId, normalize(query.getContactId()), normalize(query.getCustomerId()),
                normalize(query.getKeyword()), normalizeUpper(query.getStatus()), query.getIsPrimary(),
                query.getCreatedAtFrom(), query.getCreatedAtTo(), offset(query), query.getPageSize()), total);
    }

    @Override
    public PageResult<CrmOpportunityView> getOpportunities(CrmOpportunityPageQuery query) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        long total = mapper.countOpportunities(tenantId, normalize(query.getOpportunityId()), normalize(query.getOpportunityCode()),
                normalize(query.getCustomerId()), normalize(query.getKeyword()), normalizeUpper(query.getStage()),
                normalize(query.getOwnerPrincipalId()), query.getExpectedCloseDateFrom(), query.getExpectedCloseDateTo(),
                query.getCreatedAtFrom(), query.getCreatedAtTo());
        if (total == 0) return PageResult.empty();
        return new PageResult<>(mapper.selectOpportunities(tenantId, normalize(query.getOpportunityId()), normalize(query.getOpportunityCode()),
                normalize(query.getCustomerId()), normalize(query.getKeyword()), normalizeUpper(query.getStage()),
                normalize(query.getOwnerPrincipalId()), query.getExpectedCloseDateFrom(), query.getExpectedCloseDateTo(),
                query.getCreatedAtFrom(), query.getCreatedAtTo(), offset(query), query.getPageSize()), total);
    }

    @Override
    public PageResult<CrmFollowUpView> getFollowUps(CrmFollowUpPageQuery query) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        long total = mapper.countFollowUps(tenantId, normalizeUpper(query.getSubjectType()), normalize(query.getSubjectId()),
                normalizeUpper(query.getMethodCode()), normalize(query.getActorPrincipalId()),
                query.getOccurredAtFrom(), query.getOccurredAtTo());
        if (total == 0) return PageResult.empty();
        return new PageResult<>(mapper.selectFollowUps(tenantId, normalizeUpper(query.getSubjectType()), normalize(query.getSubjectId()),
                normalizeUpper(query.getMethodCode()), normalize(query.getActorPrincipalId()),
                query.getOccurredAtFrom(), query.getOccurredAtTo(), offset(query), query.getPageSize()), total);
    }

    @Override
    public PageResult<CrmCustomerView> getPool(CrmPoolPageQuery query) {
        CrmCustomerPageQuery delegated = new CrmCustomerPageQuery();
        delegated.setKeyword(query.getKeyword());
        delegated.setSourceCode(query.getSourceCode());
        delegated.setIndustryCode(query.getIndustryCode());
        delegated.setRegionCode(query.getRegionCode());
        delegated.setCreatedAtFrom(query.getCreatedAtFrom());
        delegated.setCreatedAtTo(query.getCreatedAtTo());
        delegated.setPoolStatus("IN_POOL");
        delegated.setPageNo(query.getPageNo());
        delegated.setPageSize(query.getPageSize());
        return getCustomers(delegated);
    }

    private static long offset(cn.iocoder.yudao.framework.common.pojo.PageParam query) {
        return (long) (query.getPageNo() - 1) * query.getPageSize();
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }

    private static String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
