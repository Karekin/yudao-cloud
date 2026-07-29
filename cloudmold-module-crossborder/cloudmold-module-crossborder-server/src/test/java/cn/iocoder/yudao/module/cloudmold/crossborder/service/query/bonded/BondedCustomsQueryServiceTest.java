package cn.iocoder.yudao.module.cloudmold.crossborder.service.query.bonded;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.bonded.BondedCustomsRecords.CaseRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.mysql.bonded.BondedCustomsCaseMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BondedCustomsQueryServiceTest {
    private final BondedCustomsCaseMapper mapper = mock(BondedCustomsCaseMapper.class);
    private final BondedCustomsQueryService service = new BondedCustomsQueryService(mapper);

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void rejectsNonTerminalCase() {
        TenantContextHolder.setTenantId(162L);
        when(mapper.selectCase(162L, "bonded-01")).thenReturn(new CaseRecord()
                .setCaseId("bonded-01").setTenantId(162L).setStatus("CUSTOMS_ACCEPTED")
                .setTripleMatchStatus("TRIPLE_MATCHED").setCustomsStatus("CUSTOMS_ACCEPTED")
                .setBondedReleaseStatus("PENDING").setDeliveryStatus("PENDING"));

        assertThatThrownBy(() -> service.get("bonded-01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bonded customs case is not terminal");
    }
}
