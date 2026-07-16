package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

@Data
public class LegacyTradeBenefitMigrationOperationDO {
    private Long operationId;
    private Long tenantId;
    private String idempotencyKey;
    private String sourceEventId;
    private String commandType;
    private String requestHash;
    private String attemptToken;
    private Integer status;
    private String migrationRunId;
    private String resultJson;
}
