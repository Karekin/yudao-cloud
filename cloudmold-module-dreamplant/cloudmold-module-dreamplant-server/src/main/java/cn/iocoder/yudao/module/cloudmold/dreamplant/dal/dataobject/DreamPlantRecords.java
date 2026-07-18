package cn.iocoder.yudao.module.cloudmold.dreamplant.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

public final class DreamPlantRecords {
    private DreamPlantRecords() {
    }

    @Data
    @Accessors(chain = true)
    public static class Operation {
        private Long operationId;
        private Long tenantId;
        private String idempotencyKey;
        private String commandType;
        private String requestHash;
        private String attemptToken;
        private Integer status;
        private String aggregateId;
        private String resultJson;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class WorldMap {
        private Long tenantId;
        private String mapKey;
        private Long currentVersion;
        private Boolean publiclyReadable;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class Snapshot {
        private Long tenantId;
        private String mapKey;
        private Long version;
        private String schemaVersion;
        private String payloadJson;
        private String payloadSha256;
        private Boolean canonicalHashVerified;
        private String sourceRef;
        private LocalDateTime publishedAt;
        private Long operationId;
    }

    @Data
    @Accessors(chain = true)
    public static class ExplorationRun {
        private Long tenantId;
        private String explorationRunId;
        private String mapKey;
        private String intent;
        private String contextJson;
        private String requestedByPrincipalId;
        private String status;
        private Long version;
        private String outcomeJson;
        private String evidenceRef;
        private Boolean outcomeHashVerified;
        private Integer attemptCount;
        private Integer maxAttempts;
        private LocalDateTime nextRetryAt;
        private String leaseOwner;
        private LocalDateTime leaseUntil;
        private String lastErrorCode;
        private String lastErrorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Long operationId;
    }
}
