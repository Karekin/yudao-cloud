package cn.iocoder.yudao.module.cloudmold.integration.yudao.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class LegacyCatalogProjectionDO {
    private Long projectionId;
    private Long tenantId;
    private String targetSystem;
    private String targetEntity;
    private String canonicalType;
    private String canonicalId;
    private Long aggregateVersion;
    private String payload;
    private String payloadHash;
    private Integer status;
    private String lastErrorSummary;
}
