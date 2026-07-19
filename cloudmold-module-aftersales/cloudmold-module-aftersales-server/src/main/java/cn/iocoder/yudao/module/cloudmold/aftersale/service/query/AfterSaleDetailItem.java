package cn.iocoder.yudao.module.cloudmold.aftersale.service.query;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 规范 AfterSale 详情售后行")
@Data
public class AfterSaleDetailItem {

    private String afterSaleItemId;
    private String orderItemId;
    private String canonicalSkuId;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal quantity;
    private Long lineAmountMinor;
    private Long discountAmountMinor;
    private Long netAmountMinor;
    private String listingId;
    private String listingOfferId;
    private LocalDateTime createdAt;
}
