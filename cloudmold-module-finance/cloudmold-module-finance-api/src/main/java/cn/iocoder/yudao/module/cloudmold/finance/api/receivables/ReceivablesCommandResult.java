package cn.iocoder.yudao.module.cloudmold.finance.api.receivables;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceivablesCommandResult implements Serializable {
    private Long operationId;
    private Boolean duplicate;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String status;
    private String receivablePlanId;
    private String receiptId;
    private Long allocatedAmountMinor;
    private List<String> allocationIds;
    private List<String> affectedReceivablePlanIds;
}
