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
public class DreamPlantSyncView implements java.io.Serializable {
    private String mapKey;
    private String syncKey;
    private Long version;
    private String sourceSystem;
    private String sourceNamespace;
    private String sourceCursor;
    private String sourceCheckpointRef;
    private String targetProjection;
    private String status;
    private String detailsJson;
    private String detailsSha256;
    private String sourceRef;
    private String evidenceRef;
    private Instant checkpointAt;
    private Instant createdAt;
    private Instant updatedAt;
}
