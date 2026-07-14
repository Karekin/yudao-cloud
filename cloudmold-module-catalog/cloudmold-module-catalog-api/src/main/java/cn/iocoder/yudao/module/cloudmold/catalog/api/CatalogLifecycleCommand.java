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
public class CatalogLifecycleCommand {
    private CatalogEntityType entityType;
    private String entityId;
    private CatalogLifecycleAction action;
    private Long expectedVersion;
    private String idempotencyKey;
    private String reason;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
