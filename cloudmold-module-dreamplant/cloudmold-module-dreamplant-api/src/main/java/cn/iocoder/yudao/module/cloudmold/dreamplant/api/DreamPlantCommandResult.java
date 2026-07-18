package cn.iocoder.yudao.module.cloudmold.dreamplant.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DreamPlantCommandResult implements java.io.Serializable {
    private Long operationId;
    private Boolean duplicate;
    private String mapKey;
    private Long mapVersion;
    private String explorationRunId;
    private Long explorationVersion;
    private String status;
}
