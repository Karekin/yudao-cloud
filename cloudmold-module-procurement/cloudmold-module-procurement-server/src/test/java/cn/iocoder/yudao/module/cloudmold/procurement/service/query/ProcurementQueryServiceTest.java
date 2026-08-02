package cn.iocoder.yudao.module.cloudmold.procurement.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementOrderView;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.ProcurementOrder;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseOrderDeliverySchedule;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseOrderItem;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcurementQueryServiceTest {
    private final ProcurementMapper mapper = mock(ProcurementMapper.class);
    private final ProcurementQueryService service = new ProcurementQueryService(mapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(31L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void returnsMultipleItemsForCurrentOrder() {
        when(mapper.selectCurrentHeader(31L, "order-01")).thenReturn(
                new ProcurementOrder()
                        .setOrderId("order-01")
                        .setOrderCode("PO-CM-STD-001")
                        .setSupplierId("supplier-01")
                        .setCurrencyCode("CNY")
                        .setHeaderNetAmountMinor(20000L)
                        .setHeaderTaxAmountMinor(2600L)
                        .setHeaderGrossAmountMinor(22600L)
                        .setStatus("CREATED"));
        when(mapper.selectItems(31L, "order-01")).thenReturn(List.of(
                new PurchaseOrderItem()
                        .setItemId("item-10")
                        .setOrderId("order-01")
                        .setLineNumber(10)
                        .setCanonicalSkuId("sku-01")
                        .setOrderedQuantity(new BigDecimal("12"))
                        .setUomCode("EA")
                        .setTaxCode("VAT13")
                        .setTaxRateBps(1300)
                        .setUnitNetPriceMinor(new BigDecimal("1000.000000"))
                        .setLineNetAmountMinor(12000L)
                        .setLineTaxAmountMinor(1560L)
                        .setLineGrossAmountMinor(13560L),
                new PurchaseOrderItem()
                        .setItemId("item-20")
                        .setOrderId("order-01")
                        .setLineNumber(20)
                        .setCanonicalSkuId("sku-02")
                        .setOrderedQuantity(new BigDecimal("8"))
                        .setUomCode("EA")
                        .setTaxCode("VAT13")
                        .setTaxRateBps(1300)
                        .setUnitNetPriceMinor(new BigDecimal("1000.000000"))
                        .setLineNetAmountMinor(8000L)
                        .setLineTaxAmountMinor(1040L)
                        .setLineGrossAmountMinor(9040L)));
        when(mapper.selectSchedules(31L, "order-01")).thenReturn(List.of(
                new PurchaseOrderDeliverySchedule()
                        .setScheduleId("schedule-10")
                        .setItemId("item-10")
                        .setScheduleNumber(1)
                        .setRequiredDeliveryDate(LocalDate.of(2026, 8, 3))
                        .setCanonicalWarehouseId("warehouse-01")
                        .setScheduledQuantity(new BigDecimal("12")),
                new PurchaseOrderDeliverySchedule()
                        .setScheduleId("schedule-20")
                        .setItemId("item-20")
                        .setScheduleNumber(1)
                        .setRequiredDeliveryDate(LocalDate.of(2026, 8, 5))
                        .setCanonicalWarehouseId("warehouse-02")
                        .setScheduledQuantity(new BigDecimal("8"))));

        ProcurementOrderView result = service.requireCurrent("order-01");

        assertThat(result.getSupplierId()).isEqualTo("supplier-01");
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getSchedules()).hasSize(1);
    }
}
