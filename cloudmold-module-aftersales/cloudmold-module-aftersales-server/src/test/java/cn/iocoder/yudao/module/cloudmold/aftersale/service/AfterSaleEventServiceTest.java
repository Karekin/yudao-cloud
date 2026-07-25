package cn.iocoder.yudao.module.cloudmold.aftersale.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.AfterSaleCaseDO;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.AfterSaleHistoryDO;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.AfterSaleItemDO;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.AfterSaleRefundHistoryDO;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql.AfterSaleHistoryMapper;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql.AfterSaleRefundHistoryMapper;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql.AfterSaleResolutionSagaHistoryMapper;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AfterSaleEventServiceTest {

    @Test
    void shouldEmitReasonDigestWithoutRawReasonText() {
        AfterSaleHistoryMapper historyMapper = mock(AfterSaleHistoryMapper.class);
        AfterSaleRefundHistoryMapper refundHistoryMapper = mock(AfterSaleRefundHistoryMapper.class);
        AfterSaleResolutionSagaHistoryMapper sagaHistoryMapper = mock(AfterSaleResolutionSagaHistoryMapper.class);
        OutboxAppender outboxAppender = mock(OutboxAppender.class);
        AfterSaleEventService service = new AfterSaleEventService(historyMapper, refundHistoryMapper,
                sagaHistoryMapper, outboxAppender);
        LocalDateTime now = LocalDateTime.of(2026, 7, 25, 10, 0);
        Instant occurredAt = now.toInstant(ZoneOffset.UTC);
        AfterSaleCaseDO sale = new AfterSaleCaseDO()
                .setAfterSaleId("after-sale-1").setTenantId(1L).setAfterSaleNo("AS-1001").setRunId("run-1")
                .setStatus("REQUESTED").setRefundStatus("PENDING").setVersion(2L)
                .setCorrelationId("correlation-1").setCausationId("causation-1")
                .setAfterSaleType("RETURN_REFUND").setReasonCode("SIZE_NOT_FIT").setResponsibility("BUYER")
                .setReason("buyer wrote a free-form reason with contact details")
                .setOrderId("order-1").setBuyerId("buyer-1").setApprovedAmountMinor(1000L).setCurrencyCode("CNY")
                .setPaymentId("payment-1").setResolutionSagaId("saga-1");
        AfterSaleItemDO item = new AfterSaleItemDO()
                .setAfterSaleItemId("item-1").setOrderItemId("order-item-1").setCanonicalSkuId("sku-1")
                .setQuantity(BigDecimal.ONE).setLineAmountMinor(1200L).setDiscountAmountMinor(200L)
                .setNetAmountMinor(1000L).setListingId("listing-1").setListingOfferId("offer-1");

        service.appendCase(11L, sale, item, "OPEN", occurredAt, now);
        service.appendRefund(sale, item, "PENDING", "SUCCEEDED", 3L, 99L, occurredAt, now);

        ArgumentCaptor<AppendDomainEventCommand> captor = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender, times(2)).append(captor.capture());
        String expectedDigest = DigestUtil.sha256Hex("buyer wrote a free-form reason with contact details");
        captor.getAllValues().forEach(event -> {
            assertThat(event.getPayload()).containsEntry("reason_code", "SIZE_NOT_FIT")
                    .containsEntry("has_reason_text", true)
                    .containsEntry("reason_text_digest_sha256", expectedDigest);
            String json = JsonUtils.toJsonString(event.getPayload());
            assertThat(json).doesNotContain("buyer wrote a free-form reason with contact details");
            assertThat(event.getPayload()).doesNotContainKey("reason");
        });
        verify(historyMapper).insert(any(AfterSaleHistoryDO.class));
        verify(refundHistoryMapper).insert(any(AfterSaleRefundHistoryDO.class));
    }
}
