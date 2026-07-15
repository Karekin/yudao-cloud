package cn.iocoder.yudao.module.cloudmold.listing.api.unpublish;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingUnpublishSagaCommand {
    private ListingUnpublishSagaOperation operation;
    private String idempotencyKey;
    private String runId;
    private String sagaId;
    private Long expectedVersion;
    private String sourceEventId;
    private String sourceEntityType;
    private Long sourceAggregateVersion;
    private String merchantId;
    private String shopId;
    private String reason;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
