package cn.iocoder.yudao.module.cloudmold.finance.api.receivables;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceivableSourceView implements Serializable {
    private String customerId;
    private String salesContractId;
    private String currencyCode;
    private Long contractAmountMinor;
}
