package cn.iocoder.yudao.module.cloudmold.procurement.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.procurement.api.SourcingAwardView;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementSourcingRecords.Award;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementSourcingMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourcingQueryServiceTest {
    private final ProcurementSourcingMapper mapper = mock(ProcurementSourcingMapper.class);
    private final SourcingQueryService service = new SourcingQueryService(mapper);

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void returnsStronglyTypedAwardReadback() {
        TenantContextHolder.setTenantId(31L);
        when(mapper.selectAward(31L, "award-01")).thenReturn(new Award()
                .setAwardId("award-01")
                .setEventId("event-01")
                .setVersion(4L)
                .setStatus("APPROVED"));

        SourcingAwardView result = service.requireAward("award-01");

        assertThat(result).isEqualTo(SourcingAwardView.builder()
                .awardId("award-01")
                .eventId("event-01")
                .version(4L)
                .status("APPROVED")
                .build());
    }
}
