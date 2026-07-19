package cn.iocoder.yudao.module.cloudmold.dreamplant.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold DreamPlant 探索运行行")
@Data
public class DreamPlantExplorationPageItem {

    private String explorationRunId;
    private String mapKey;
    private String intent;
    private String requestedByPrincipalId;
    private String status;
    private Long aggregateVersion;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
