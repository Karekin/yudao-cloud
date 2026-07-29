package cn.iocoder.yudao.module.cloudmold.crossborder.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.CrossBorderRecords.CaseRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.mysql.CrossBorderCaseMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CrossBorderQueryServiceTest {
    private final CrossBorderCaseMapper mapper = mock(CrossBorderCaseMapper.class);
    private final CrossBorderQueryService service = new CrossBorderQueryService(mapper);

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void rejectsTerminalQueryWhenCaseIsStillInFlight() {
        TenantContextHolder.setTenantId(162L);
        when(mapper.selectCase(162L, "case-01")).thenReturn(new CaseRecord()
                .setCaseId("case-01").setTenantId(162L).setStatus("CUSTOMS_SUBMITTED").setVersion(10L));
        when(mapper.selectHistory(162L, "case-01")).thenReturn(java.util.List.of());

        assertThatThrownBy(() -> service.get("case-01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cross-border case is not terminal");
    }
}
