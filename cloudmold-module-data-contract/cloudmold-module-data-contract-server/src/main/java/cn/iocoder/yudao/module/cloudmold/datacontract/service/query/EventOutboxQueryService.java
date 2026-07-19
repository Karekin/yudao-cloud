package cn.iocoder.yudao.module.cloudmold.datacontract.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.controller.admin.vo.EventOutboxPageReqVO;
import cn.iocoder.yudao.module.cloudmold.datacontract.dal.mysql.EventOutboxPageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EventOutboxQueryService {

    private final EventOutboxPageMapper pageMapper;

    public PageResult<EventOutboxPageItem> getPage(EventOutboxPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String eventId = normalize(request.getEventId());
        String eventType = normalize(request.getEventType());
        Integer status = request.getStatus();
        String aggregateType = normalize(request.getAggregateType());
        String aggregateId = normalize(request.getAggregateId());
        long total = pageMapper.countPage(tenantId, eventId, eventType, status, aggregateType, aggregateId,
                request.getRecordedAtFrom(), request.getRecordedAtTo());
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(pageMapper.selectPage(tenantId, eventId, eventType, status, aggregateType, aggregateId,
                request.getRecordedAtFrom(), request.getRecordedAtTo(), offset, request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
