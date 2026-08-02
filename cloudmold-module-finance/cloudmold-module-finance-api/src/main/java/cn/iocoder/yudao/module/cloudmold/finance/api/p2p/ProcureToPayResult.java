package cn.iocoder.yudao.module.cloudmold.finance.api.p2p;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcureToPayResult implements Serializable {
    private Long operationId;
    private Boolean duplicate;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String status;
    private String supplierInvoiceId;
    private String matchRunId;
    private String exceptionId;
    private String apOpenItemId;
    private String paymentInstructionId;
    private String journalEntryId;
}
