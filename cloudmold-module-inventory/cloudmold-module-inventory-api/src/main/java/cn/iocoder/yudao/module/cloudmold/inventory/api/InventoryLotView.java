package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Data
public class InventoryLotView {
    private String lotId;
    private String ownerType;
    private String ownerId;
    private String canonicalSkuId;
    private String lotCode;
    private LocalDate manufacturedOn;
    private LocalDate expiresOn;
    private Instant receivedAt;
    private String status;
    private Long version;
    private String allocationEligibility;
    private Instant eligibilityAt;

    private String mappingId;
    private String mappedSourceSystem;
    private String mappedSourceType;
    private String mappedSourceId;
    private Instant mappingValidFrom;
    private Instant mappingValidTo;
    private String mappingStatus;
    private Long mappingVersion;
}
