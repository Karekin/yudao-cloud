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
public class DreamPlantRelationView implements java.io.Serializable {
    private String mapKey;
    private String relationId;
    private Long version;
    private String fromAssetId;
    private DreamPlantAssetType fromAssetType;
    private String toAssetId;
    private DreamPlantAssetType toAssetType;
    private String relationType;
    private String status;
    private String detailsJson;
    private String detailsSha256;
    private String sourceRef;
    private String evidenceRef;
    private Instant effectiveAt;
    private Instant observedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
