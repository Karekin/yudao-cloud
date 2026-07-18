package cn.iocoder.yudao.module.cloudmold.dreamplant.api;

import java.util.List;

public interface DreamPlantQueryApi {
    DreamPlantWorldMapSnapshot getPublishedWorldMap(String mapKey);
    DreamPlantWorldMapSnapshot getPublicWorldMap(String mapKey);
    DreamPlantExplorationView getExploration(String explorationRunId);
    List<DreamPlantExplorationView> listExplorations(String status, Integer limit);
}
