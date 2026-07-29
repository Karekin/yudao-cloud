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
public class AsnLineDefinition {
    private String asnLineId;
    private Integer lineNo;
    private String canonicalSkuId;
    private String ownerType;
    private String ownerId;
    private String baseUomCode;
    /** 预期收货数量 */
    private BigDecimal expectedQuantity;
    /** 单价（最小货币单位），从采购行透传给库存 RECEIVE 成本证据 */
    private Long unitCostAmountMinor;
    private String currencyCode;
    /** 收货暂存库位：RECEIVE 落 NON_SELLABLE/PENDING_QC 余额到此 location */
    private String stagingLocationId;
}
