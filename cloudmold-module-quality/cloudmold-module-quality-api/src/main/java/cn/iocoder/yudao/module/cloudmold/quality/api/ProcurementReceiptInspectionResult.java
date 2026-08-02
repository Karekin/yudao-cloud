package cn.iocoder.yudao.module.cloudmold.quality.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcurementReceiptInspectionResult implements Serializable {
    private Long operationId;
    private boolean duplicate;
    private String inspectionId;
    private Long aggregateVersion;
    private String status;
    private String finalDecision;
    private BigDecimal receivedQuantity;
    private BigDecimal sampledQuantity;
    private BigDecimal acceptedQuantity;
    private BigDecimal rejectedQuantity;
    private BigDecimal quarantinedQuantity;
}
