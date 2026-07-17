package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class LegacyTradeProductIdentityQualificationSourceDO {
    private Long tenantId;
    private String sourceMigrationRunId;
    private String policyVersion;
    private Boolean itemEvidenceComplete;
    private String itemEvidenceId;
    private Long legacyOrderItemId;
    private Long legacySpuId;
    private Long legacySkuId;
    private String sourceItemEvidenceHash;
    private Boolean deleted;
    private Boolean orderDeleted;
}
