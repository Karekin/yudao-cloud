package cn.iocoder.yudao.module.cloudmold.catalog.api.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssortmentWaveReadinessResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Status { NEEDS_CATALOG, WAITING, READY }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CatalogSnapshot implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private Integer planningYear;
        private String seasonCode;
        private String waveCode;
        private Integer styleCount;
        private Integer activeStyleCount;
        private Integer spuCount;
        private Integer activeSpuCount;
        private Integer skuCount;
        private Integer activeSkuCount;
        private String lastCatalogUpdatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Blocker implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String code;
        private String title;
        private String severity;
        private String summary;
        private String requiredSourceOfTruth;
        private String unblockAction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Artifact implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String type;
        private String id;
        private String label;
        private String status;
    }

    private String workflowType;
    private String workflowInstanceKey;
    private Status status;
    private String phase;
    private Boolean terminal;
    private Boolean actionRequired;
    private String summary;
    private CatalogSnapshot catalogSnapshot;
    private List<Blocker> blockers;
    private List<String> nextActions;
    private List<Artifact> artifacts;
}
