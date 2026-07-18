package cn.iocoder.yudao.module.cloudmold.metadata.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Immutable, tenant-scoped dataset contract returned to domain consumers.
 * Connection endpoints and credentials are intentionally excluded.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataDatasetReference {
    private String datasetId;
    private Long datasetVersion;
    private String dataSourceId;
    private Long dataSourceVersion;
    private String datasetType;
    private String qualifiedName;
    private String layerCode;
    private String grainCode;
    private String schemaSha256;
    private List<FieldReference> fields;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldReference {
        private Integer ordinalPosition;
        private String fieldCode;
        private String dataType;
        private Boolean nullable;
        private Boolean primaryKeyPart;
        private String semanticType;
        private String classification;
    }
}
