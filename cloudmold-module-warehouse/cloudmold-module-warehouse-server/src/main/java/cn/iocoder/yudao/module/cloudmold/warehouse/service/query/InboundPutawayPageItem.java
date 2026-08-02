package cn.iocoder.yudao.module.cloudmold.warehouse.service.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InboundPutawayPageItem(
        String putawayId,
        String receiptId,
        String receiptNo,
        String procurementOrderId,
        String supplierId,
        String warehouseId,
        String status,
        Long version,
        Long lineCount,
        BigDecimal totalPutawayQuantity,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
