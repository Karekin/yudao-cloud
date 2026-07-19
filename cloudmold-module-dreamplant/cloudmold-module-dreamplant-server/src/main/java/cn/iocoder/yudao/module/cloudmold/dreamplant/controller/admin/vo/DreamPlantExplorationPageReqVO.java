package cn.iocoder.yudao.module.cloudmold.dreamplant.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold DreamPlant 探索运行分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DreamPlantExplorationPageReqVO extends PageParam {

    @Schema(description = "探索运行 ID")
    private String explorationRunId;

    @Schema(description = "世界地图键")
    private String mapKey;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "提交者主体")
    private String requestedByPrincipalId;

    @Schema(description = "创建时间起")
    private LocalDateTime createdAtFrom;

    @Schema(description = "创建时间止")
    private LocalDateTime createdAtTo;
}
