package cn.iocoder.yudao.module.cloudmold.risk.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 风控审核案例行")
@Data
public class RiskReviewPageItem {

    private String caseId;
    private String clusterId;
    private String status;
    private String reviewerPrincipalId;
    private Long aggregateVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
