package cn.iocoder.yudao.module.cloudmold.integration.yudao.api;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LegacyCatalogProjectionResult {
    private String canonicalSkuId;
    private Long aggregateVersion;
    private List<LegacyCatalogProjectionItem> projections;
}
