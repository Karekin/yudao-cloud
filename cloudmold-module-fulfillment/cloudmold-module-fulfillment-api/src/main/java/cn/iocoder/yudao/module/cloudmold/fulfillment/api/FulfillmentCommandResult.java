package cn.iocoder.yudao.module.cloudmold.fulfillment.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FulfillmentCommandResult {
    private Long operationId;
    private String fulfillmentId;
    private String fulfillmentNo;
    private String shipmentId;
    private String orderId;
    private String previousStatus;
    private String currentStatus;
    private String cancellationSagaId;
    private Long aggregateVersion;
    private String sellerId;
    private String warehouseId;
    private String carrierCode;
    private String waybillNo;
    private List<FulfillmentLineView> items;
    private Boolean duplicate;
}
