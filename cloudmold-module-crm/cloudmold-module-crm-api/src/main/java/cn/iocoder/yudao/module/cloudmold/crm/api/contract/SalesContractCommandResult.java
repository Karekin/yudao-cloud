package cn.iocoder.yudao.module.cloudmold.crm.api.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesContractCommandResult implements Serializable {
    private Long operationId;
    private boolean duplicate;
    private String salesContractId;
    private String status;
    private Long version;
    private Long totalAmountMinor;
    private String currencyCode;
    private String approvalProcessInstanceId;
}
