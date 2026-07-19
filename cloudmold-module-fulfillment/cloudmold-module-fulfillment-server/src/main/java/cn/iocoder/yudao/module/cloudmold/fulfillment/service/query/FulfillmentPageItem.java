package cn.iocoder.yudao.module.cloudmold.fulfillment.service.query;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 规范 Fulfillment 分页项")
@Data
public class FulfillmentPageItem {

    private String fulfillmentId;
    private String fulfillmentNo;
    private String orderId;
    private String orderNo;
    private String sellerId;
    private String warehouseId;
    private String status;
    private Long itemCount;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal totalQuantity;
    private String firstSliceShipmentId;
    private String firstSliceShipmentStatus;
    private String carrierCode;
    private String waybillNo;
    private String deliveryPromiseVersionRef;
    private LocalDateTime promisedDeliveryAt;
    private String cancellationRef;
    private Long aggregateVersion;
    private LocalDateTime updatedAt;
}
