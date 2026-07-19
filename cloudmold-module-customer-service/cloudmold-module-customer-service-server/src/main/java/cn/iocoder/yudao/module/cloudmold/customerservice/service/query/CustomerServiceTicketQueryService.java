package cn.iocoder.yudao.module.cloudmold.customerservice.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.customerservice.controller.admin.vo.CustomerServiceTicketPageReqVO;
import cn.iocoder.yudao.module.cloudmold.customerservice.dal.mysql.CustomerServiceTicketPageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CustomerServiceTicketQueryService {

    private final CustomerServiceTicketPageMapper pageMapper;

    public PageResult<CustomerServiceTicketPageItem> getPage(CustomerServiceTicketPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String ticketId = normalize(request.getTicketId());
        String ticketNo = normalize(request.getTicketNo());
        String customerPrincipalId = normalize(request.getCustomerPrincipalId());
        String assignedAgentPrincipalId = normalize(request.getAssignedAgentPrincipalId());
        String channelCode = normalizeUpper(request.getChannelCode());
        String priority = normalizeUpper(request.getPriority());
        String status = normalizeUpper(request.getStatus());
        String categoryCode = normalize(request.getCategoryCode());
        long total = pageMapper.countPage(tenantId, ticketId, ticketNo, customerPrincipalId, assignedAgentPrincipalId,
                channelCode, priority, status, categoryCode, request.getCreatedAtFrom(), request.getCreatedAtTo());
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(pageMapper.selectPage(tenantId, ticketId, ticketNo, customerPrincipalId,
                assignedAgentPrincipalId, channelCode, priority, status, categoryCode,
                request.getCreatedAtFrom(), request.getCreatedAtTo(), offset, request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
