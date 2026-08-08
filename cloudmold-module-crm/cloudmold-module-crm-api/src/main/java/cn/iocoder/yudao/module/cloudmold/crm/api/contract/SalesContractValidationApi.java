package cn.iocoder.yudao.module.cloudmold.crm.api.contract;

public interface SalesContractValidationApi {

    SalesContractSourceView requireReceivableSource(String customerId, String salesContractId);
}
