package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LegacyTradeProductIdentityItemSourceDO {
    private Long tenantId;
    private String sourceMigrationRunId;
    private String candidateId;
    private String itemEvidenceId;
    private Long legacyOrderId;
    private Long legacyOrderItemId;
    private Long legacySpuId;
    private Long legacySkuId;
    private String sourceItemEvidenceHash;
    private Boolean deleted;
    private Boolean orderDeleted;
    private Integer sourceParentCardinality;
    private Long currentSpuId;
    private Integer currentSpuStatus;
    private Boolean currentSpuDeleted;
    private LocalDateTime currentSpuCreatedAt;
    private LocalDateTime currentSpuUpdatedAt;
    private Long currentSkuId;
    private Long currentSkuSpuId;
    private Boolean currentSkuDeleted;
    private LocalDateTime currentSkuCreatedAt;
    private LocalDateTime currentSkuUpdatedAt;
    private Integer qualificationCount;
    private String qualificationId;
    private Long qualifiedLegacyOrderItemId;
    private Long historicalSpuId;
    private Long historicalSkuId;
    private String qualificationSourceItemEvidenceHash;
    private String historicalProductSnapshotHash;
}
