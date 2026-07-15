package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Data
public class InventoryLotCommand {
    private InventoryLotOperation operation;
    private String idempotencyKey;
    private String sourceEventId;
    private String runId;
    private String migrationRunId;

    private String lotId;
    private Long expectedLotVersion;
    private String ownerType;
    private String ownerId;
    private String canonicalSkuId;
    private String lotCode;
    private LocalDate manufacturedOn;
    private LocalDate expiresOn;
    private Instant receivedAt;

    private String mappingId;
    private Long expectedMappingVersion;
    private String mappedSourceSystem;
    private String mappedSourceType;
    private String mappedSourceId;
    private Instant validFrom;
    private Instant validTo;
    private String verificationRef;

    private String reasonCode;
    private String evidenceRef;
    private String recallReference;
    private String traceId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
