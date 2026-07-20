package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockoutDiagnosisQuery;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockoutDiagnosisResult.SizeStockFact;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryStockoutDiagnosisMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryStockoutDiagnosisServiceTest {

    private final InventoryStockoutDiagnosisMapper mapper = mock(InventoryStockoutDiagnosisMapper.class);
    private final InventoryStockoutDiagnosisService service = new InventoryStockoutDiagnosisService(mapper);

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void diagnosesEveryActiveSizeIncludingSkuWithoutBalance() {
        TenantContextHolder.setTenantId(17L);
        when(mapper.selectActiveSpuIdentity(17L, "spu-1")).thenReturn("SPU-001|STYLE-001");
        when(mapper.selectSizeStockFacts(17L, "spu-1", "warehouse-1")).thenReturn(List.of(
                fact("sku-s", "S", "0", "0", "0"),
                fact("sku-m", "M", "8", "2", "0"),
                fact("sku-l", "L", "20", "2", "3")));

        var result = service.diagnose(InventoryStockoutDiagnosisQuery.builder().canonicalSpuId("spu-1")
                .warehouseId("warehouse-1").lowStockThreshold(new BigDecimal("6")).build());

        assertThat(result.getOutcomeCode()).isEqualTo("STOCKOUT_DETECTED");
        assertThat(result.getStockoutCount()).isEqualTo(1);
        assertThat(result.getLowStockCount()).isEqualTo(1);
        assertThat(result.getSizeStockFacts()).extracting(SizeStockFact::getSeverity)
                .containsExactly("P1_STOCKOUT", "P2_LOW_STOCK", "HEALTHY");
    }

    @Test
    void rejectsClientTechnicalParametersAndUnknownSpu() {
        TenantContextHolder.setTenantId(17L);
        assertThatThrownBy(() -> service.diagnose(InventoryStockoutDiagnosisQuery.builder()
                .canonicalSpuId("spu-1?tenantId=99").build())).isInstanceOf(IllegalArgumentException.class);
        when(mapper.selectActiveSpuIdentity(17L, "spu-unknown")).thenReturn(null);
        assertThatThrownBy(() -> service.diagnose(InventoryStockoutDiagnosisQuery.builder()
                .canonicalSpuId("spu-unknown").build())).hasMessageContaining("does not exist");
    }

    private static SizeStockFact fact(String skuId, String size, String onHand, String reserved, String inTransit) {
        return SizeStockFact.builder().canonicalSkuId(skuId).skuCode("SKU-" + size).colorCode("BLACK")
                .colorName("黑色").sizeCode(size).sizeName(size).onHandQuantity(new BigDecimal(onHand))
                .reservedQuantity(new BigDecimal(reserved)).inTransitQuantity(new BigDecimal(inTransit))
                .allocatableQuantity(new BigDecimal(onHand).subtract(new BigDecimal(reserved)))
                .maxInventoryVersion(1L).build();
    }

}
