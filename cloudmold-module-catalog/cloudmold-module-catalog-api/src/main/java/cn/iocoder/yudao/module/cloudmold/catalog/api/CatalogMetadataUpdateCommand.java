package cn.iocoder.yudao.module.cloudmold.catalog.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogMetadataUpdateCommand {
    private CatalogEntityType entityType;
    private String entityId;
    private Long expectedVersion;
    private String idempotencyKey;
    private String reason;
    private String styleCode;
    private String styleName;
    private String planningCategoryRef;
    private String brandRef;
    private Integer planningYear;
    private String seasonCode;
    private String waveCode;
    private String spuCode;
    private String productName;
    private String salesCategoryRef;
    private String skuCode;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
