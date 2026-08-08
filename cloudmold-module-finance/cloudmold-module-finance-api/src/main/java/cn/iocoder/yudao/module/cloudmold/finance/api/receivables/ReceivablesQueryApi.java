package cn.iocoder.yudao.module.cloudmold.finance.api.receivables;

import java.util.List;

public interface ReceivablesQueryApi {

    List<ReceivablesSummaryView> summarizeByCustomer(String customerId);

    List<ReceivablesSummaryView> summarizeBySalesContract(String salesContractId);

}
