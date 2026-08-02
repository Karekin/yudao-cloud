package cn.iocoder.yudao.module.cloudmold.procurement.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.procurement.api.SourcingAwardView;
import cn.iocoder.yudao.module.cloudmold.procurement.api.SourcingQueryApi;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementSourcingRecords.Award;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementSourcingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SourcingQueryService implements SourcingQueryApi {
    private final ProcurementSourcingMapper mapper;

    @Override
    public SourcingAwardView requireAward(String awardId) {
        if (awardId == null || awardId.isBlank()) {
            throw new IllegalArgumentException("awardId is required");
        }
        Award award = mapper.selectAward(TenantContextHolder.getRequiredTenantId(), awardId);
        if (award == null) {
            throw new IllegalArgumentException("procurement award not found");
        }
        return SourcingAwardView.builder()
                .awardId(award.getAwardId())
                .eventId(award.getEventId())
                .version(award.getVersion())
                .status(award.getStatus())
                .build();
    }
}
