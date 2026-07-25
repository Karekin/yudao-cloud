package cn.iocoder.yudao.module.cloudmold.catalog.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogMetadataUpdateResult {
    private Long operationId;
    private CatalogEntityType entityType;
    private String entityId;
    private String businessCode;
    private String currentStatus;
    private Long aggregateVersion;
    private Boolean duplicate;
}
