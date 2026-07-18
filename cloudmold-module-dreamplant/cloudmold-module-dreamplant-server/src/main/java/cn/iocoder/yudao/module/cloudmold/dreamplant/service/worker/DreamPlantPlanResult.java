package cn.iocoder.yudao.module.cloudmold.dreamplant.service.worker;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DreamPlantPlanResult {

    String plannerCode;
    String solutionId;
    String operationAgentId;
    String worldMapSchemaVersion;
    String worldMapJson;
    String worldMapSha256;
    String outcomeStatus;
    String outcomeJson;
    String evidenceRef;

}
