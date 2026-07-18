package cn.iocoder.yudao.module.cloudmold.dreamplant.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DreamPlantAssetCommand implements java.io.Serializable {
    private String mapKey;
    private String assetId;
    private DreamPlantAssetType assetType;
    private String idempotencyKey;
    private String runTraceId;
    private Long expectedVersion;
    private String displayName;
    private String status;
    private String lifecycleStage;
    private String ownerPrincipalId;
    private String canonicalKey;
    private String detailsJson;
    private String detailsSha256;
    private String sourceRef;
    private String evidenceRef;
    private Instant effectiveAt;
    private Instant observedAt;
    private Instant occurredAt;
}
