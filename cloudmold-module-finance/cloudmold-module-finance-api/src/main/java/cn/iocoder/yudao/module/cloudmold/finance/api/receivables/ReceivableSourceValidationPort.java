package cn.iocoder.yudao.module.cloudmold.finance.api.receivables;

public interface ReceivableSourceValidationPort {

    ReceivableSourceView requireActiveSalesReceivableSource(Long tenantId, String customerId, String salesContractId);

}
