package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_asn_line")
public class AsnLineDO {
    private String asnLineId;
    private Long tenantId;
    private String asnId;
    private Integer lineNo;
    private String canonicalSkuId;
    private String ownerType;
    private String ownerId;
    private String baseUomCode;
    private BigDecimal expectedQuantity;
    private Long unitCostAmountMinor;
    private String currencyCode;
    private String stagingLocationId;
    private LocalDateTime createdAt;
}
