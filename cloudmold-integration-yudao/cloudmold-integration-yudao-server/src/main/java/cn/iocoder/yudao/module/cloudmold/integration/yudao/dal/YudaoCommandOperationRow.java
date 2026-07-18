package cn.iocoder.yudao.module.cloudmold.integration.yudao.dal;

import lombok.Data;

/** Durable replay ledger row for a governed legacy-yudao command. */
@Data
public class YudaoCommandOperationRow {
    private Long operationId;
    private Long tenantId;
    private String operationType;
    private String idempotencyKey;
    private String requestHash;
    private String attemptToken;
    private Integer status;
    private String resultJson;
}
