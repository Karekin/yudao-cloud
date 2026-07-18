package cn.iocoder.yudao.module.cloudmold.dreamplant.service.worker;

import cn.iocoder.yudao.module.cloudmold.dreamplant.dal.dataobject.DreamPlantRecords.ExplorationRun;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DreamPlantPlannerInput {

    ExplorationRun explorationRun;
    DreamPlantWorldMapPort.PublishedWorldMap publishedWorldMap;

}
