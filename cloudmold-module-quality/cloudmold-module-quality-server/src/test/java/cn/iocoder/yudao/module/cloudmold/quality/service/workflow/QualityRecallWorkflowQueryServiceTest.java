package cn.iocoder.yudao.module.cloudmold.quality.service.workflow;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.quality.api.workflow.QualityRecallWorkflowResult.Status;
import cn.iocoder.yudao.module.cloudmold.quality.dal.dataobject.QualityRecords.*;
import cn.iocoder.yudao.module.cloudmold.quality.dal.mysql.QualityMapper;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QualityRecallWorkflowQueryServiceTest {
    private final QualityMapper mapper = mock(QualityMapper.class);
    private final InventoryLotQueryApi lotApi = mock(InventoryLotQueryApi.class);
    private final InventoryV3AvailabilityQueryApi availabilityApi = mock(InventoryV3AvailabilityQueryApi.class);
    private final QualityRecallWorkflowQueryService service =
            new QualityRecallWorkflowQueryService(mapper, lotApi, availabilityApi);

    @BeforeEach void setUp() { TenantContextHolder.setTenantId(23L); }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test
    void missingLotAuthorityRemainsWaiting() {
        stubQualityFacts();
        when(lotApi.requireCurrent(eq("lot-1"), any(Instant.class)))
                .thenThrow(new IllegalArgumentException("missing"));

        assertThat(service.inspect("r-1")).satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(Status.WAITING);
            assertThat(result.getBlockers()).contains("Inventory SoR 中不存在召回批次");
        });
    }

    @Test
    void succeedsOnlyAfterRecallAndZeroAllocatableInventory() {
        stubQualityFacts();
        when(lotApi.requireCurrent(eq("lot-1"), any(Instant.class))).thenReturn(new InventoryLotView()
                .setLotId("lot-1").setLotCode("LOT-001").setStatus("RECALLED").setVersion(4L));
        when(availabilityApi.listByLot(eq("lot-1"), any(Instant.class))).thenReturn(List.of(
                new InventoryV3AvailabilityView().setBalanceId("b-1")
                        .setAllocationEligibility("RECALLED").setAllocatableQuantity(BigDecimal.ZERO)
                        .setAggregateVersion(8L)));

        assertThat(service.inspect("r-1")).satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(Status.SUCCEEDED);
            assertThat(result.getSummary()).contains("质检、CAPA、召回和库存隔离");
        });
    }

    private void stubQualityFacts() {
        when(mapper.selectRecallAction(23L, "r-1")).thenReturn(new RecallAction()
                .setRecallActionId("r-1").setInspectionTaskId("q-1").setCanonicalSkuId("sku-1")
                .setLotId("lot-1").setStatus("RESOLVED").setVersion(3L));
        when(mapper.selectInspectionTask(23L, "q-1")).thenReturn(new InspectionTask()
                .setTaskId("q-1").setStatus("COMPLETED").setVersion(5L));
        when(mapper.selectCapasByInspectionTask(23L, "q-1")).thenReturn(List.of(
                new Capa().setCapaId("capa-1").setStatus("VERIFIED").setVersion(2L)));
    }
}
