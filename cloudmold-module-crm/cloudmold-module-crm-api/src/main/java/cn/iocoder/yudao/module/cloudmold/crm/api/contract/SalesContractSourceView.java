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
public class SalesContractSourceView implements Serializable {
    private String salesContractId;
    private String customerId;
    private String currencyCode;
    private Long totalAmountMinor;
    private String status;
}
