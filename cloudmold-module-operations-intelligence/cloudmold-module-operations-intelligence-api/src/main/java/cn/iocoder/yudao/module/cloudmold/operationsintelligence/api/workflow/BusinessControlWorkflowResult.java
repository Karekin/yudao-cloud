package cn.iocoder.yudao.module.cloudmold.operationsintelligence.api.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessControlWorkflowResult {

    public enum Status { WAITING, NEEDS_DATA, RUNNING, SUCCEEDED, FAILED }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KpiReading {
        private String metricId;
        private String metricName;
        private String status;
        private Double value;
        private String unit;
        private String freshness;
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TargetDeviation {
        private String metricId;
        private String metricName;
        private String direction;
        private String severity;
        private Double targetValue;
        private Double actualValue;
        private Double deltaValue;
        private String unit;
        private String explanation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Anomaly {
        private String code;
        private String title;
        private String severity;
        private String summary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuggestedWorkOrder {
        private String code;
        private String title;
        private String ownerRole;
        private String reason;
        private String recommendedAction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Artifact {
        private String type;
        private String id;
        private String status;
        private String label;
    }

    private String workflowType;
    private String workflowInstanceKey;
    private String businessKey;
    private Status status;
    private String phase;
    private Boolean terminal;
    private Boolean actionRequired;
    private String summary;
    private String snapshotId;
    private String generatedAt;
    private String evidenceScope;
    private String tenantLabel;
    private List<String> blockers;
    private List<String> nextActions;
    private List<KpiReading> readings;
    private List<TargetDeviation> targetDeviations;
    private List<Anomaly> anomalies;
    private List<SuggestedWorkOrder> suggestedWorkOrders;
    private List<Artifact> artifacts;
}
