package cn.iocoder.yudao.module.cloudmold.fulfillment.service.query;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 规范 Fulfillment 详情订单行")
@Data
public class FulfillmentDetailItem {

    private String fulfillmentItemId;
    private String orderItemId;
    private String canonicalSkuId;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal quantity;
    private String reservationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
