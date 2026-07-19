package cn.iocoder.yudao.module.cloudmold.risk.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 风控审核案例分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RiskReviewPageReqVO extends PageParam {

    @Schema(description = "案例 ID")
    private String caseId;

    @Schema(description = "集群 ID")
    private String clusterId;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "审核员主体")
    private String reviewerPrincipalId;

    @Schema(description = "创建时间起")
    private LocalDateTime createdAtFrom;

    @Schema(description = "创建时间止")
    private LocalDateTime createdAtTo;
}
