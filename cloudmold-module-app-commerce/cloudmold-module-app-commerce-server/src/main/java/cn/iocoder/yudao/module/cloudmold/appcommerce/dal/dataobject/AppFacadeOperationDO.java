package cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppFacadeOperationDO {
    private Long operationId;
    private Long tenantId;
    private String buyerPrincipalId;
    private String operationType;
    private String idempotencyKey;
    private String requestHash;
    private String attemptToken;
    private Integer status;
    private String resultJson;
    private LocalDateTime firstOccurredAt;
}
