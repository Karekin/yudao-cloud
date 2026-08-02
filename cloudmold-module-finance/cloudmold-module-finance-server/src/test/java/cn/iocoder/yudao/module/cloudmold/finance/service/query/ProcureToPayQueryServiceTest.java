package cn.iocoder.yudao.module.cloudmold.finance.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.ProcureToPayAdminVOs.*;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.ProcureToPayMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProcureToPayQueryServiceTest {
    private final ProcureToPayMapper mapper = mock(ProcureToPayMapper.class);
    private final ProcureToPayQueryService service = new ProcureToPayQueryService(mapper);

    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test
    void journalDetailIsTenantScopedAndIncludesImmutableDimensions() {
        TenantContextHolder.setTenantId(162L);
        JournalDetail detail = new JournalDetail();
        JournalLine line = new JournalLine(); line.setJournalLineId("line-1");
        JournalDimension dimension = new JournalDimension();
        dimension.setDimensionType("cost-center"); dimension.setDimensionValue("plant-a");
        when(mapper.selectJournalDetail(162L, "journal-1")).thenReturn(detail);
        when(mapper.selectJournalLineViews(162L, "journal-1")).thenReturn(List.of(line));
        when(mapper.selectJournalLineDimensionViews(162L, "line-1")).thenReturn(List.of(dimension));

        assertThat(service.journal("journal-1").getLines().get(0).getDimensions()).containsExactly(dimension);
        verify(mapper, never()).selectJournalDetail(argThat(id -> id == null || id != 162L), anyString());
    }

    @Test
    void invoiceDetailComposesLineageApPaymentsAndJournalReferences() {
        TenantContextHolder.setTenantId(162L);
        SupplierInvoiceDetail detail = new SupplierInvoiceDetail();
        SupplierInvoiceMatchLine line = new SupplierInvoiceMatchLine();
        line.setLineageInvoiceLineId("invoice-line-1"); line.setLineagePurchaseOrderId("po-1");
        line.setLineagePurchaseOrderItemId("po-item-1"); line.setLineageDeliveryScheduleId("schedule-1");
        line.setLineageReceiptLineId("receipt-line-1"); line.setLineageQualityDispositionId("quality-1");
        line.setLineageInventoryMovementId("movement-1");
        ApInstallmentPageItem installment = new ApInstallmentPageItem(); installment.setOpenAmountMinor("7000");
        SupplierPaymentPageItem payment = new SupplierPaymentPageItem(); payment.setTotalAmountMinor("3000");
        when(mapper.selectInvoiceDetail(162L, "invoice-1")).thenReturn(detail);
        when(mapper.selectInvoiceLineViews(162L, "invoice-1")).thenReturn(List.of(line));
        when(mapper.selectInvoiceInstallments(162L, "invoice-1")).thenReturn(List.of(installment));
        when(mapper.selectInvoicePayments(162L, "invoice-1")).thenReturn(List.of(payment));
        when(mapper.selectInvoiceJournalIds(162L, "invoice-1")).thenReturn(List.of("journal-1", "journal-2"));

        SupplierInvoiceDetail result = service.invoice("invoice-1");

        assertThat(result.getInvoiceLines().get(0).getLineage().getInventoryMovementId()).isEqualTo("movement-1");
        assertThat(result.getApInstallments()).containsExactly(installment);
        assertThat(result.getPaymentInstructions()).containsExactly(payment);
        assertThat(result.getJournalEntryIds()).containsExactly("journal-1", "journal-2");
    }
}
