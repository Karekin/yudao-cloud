package cn.iocoder.yudao.module.cloudmold.integration.yudao.api;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LegacyCatalogProjectionItem {
    private Long projectionId;
    private LegacyCatalogTargetSystem targetSystem;
    private String targetEntity;
    private String canonicalSkuId;
    private Long aggregateVersion;
    private String payloadHash;
    private String state;
    private Boolean changed;
}
