package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PutawayLineDefinition {
    private String putawayLineId;
    private String receiptLineId;
    private String sourceLocationId;
    private String targetLocationId;
    private String lotId;
    private BigDecimal quantity;
    private Long expectedReceiptLineVersion;
}
