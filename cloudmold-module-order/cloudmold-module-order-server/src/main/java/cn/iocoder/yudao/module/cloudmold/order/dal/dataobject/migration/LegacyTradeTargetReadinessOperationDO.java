package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

@Data
public class LegacyTradeTargetReadinessOperationDO {
    private Long operationId;
    private Long tenantId;
    private String requestHash;
    private String attemptToken;
    private Integer status;
    private String targetReadinessRunId;
    private String resultJson;
}
