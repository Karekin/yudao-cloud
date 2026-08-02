package cn.iocoder.yudao.module.cloudmold.supplyplanning.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplyPlanningResult {
    private Long operationId;
    private boolean duplicate;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String status;
    private String businessObjectType;
    private String businessObjectId;
    private String businessObjectNo;
    private String businessStatus;
    private String nextWaitingEventCode;
    private String nextWaitingEventLabel;
}
