package cn.iocoder.yudao.module.cloudmold.quality.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 鉴别质检工作项分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class QualityPageReqVO extends PageParam {
    @Schema(description = "工作项类型 STANDARD/CERTIFICATION/INSPECTION_TASK/CAPA")
    private String itemType;

    @Schema(description = "状态")
    private String status;
}
