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
public class ReceiptLineDefinition {
    private String asnLineId;
    private String canonicalSkuId;
    private String ownerType;
    private String ownerId;
    private String baseUomCode;
    /** 实收数量 */
    private BigDecimal receivedQuantity;
    private String stagingLocationId;
    private Long unitCostAmountMinor;
    private String currencyCode;
    /** 是否需要质检（true 则收货后建 inspection-task，接通切片 D） */
    private Boolean qcRequired;
    /** 质检任务引用（回写） */
    private String qualityTaskId;
}
