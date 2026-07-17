package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

@Data
public class LegacyTradeProductIdentityOperationDO {
    private Long operationId;
    private Long tenantId;
    private String requestHash;
    private String attemptToken;
    private Integer status;
    private String identityRunId;
    private String resultJson;
}
