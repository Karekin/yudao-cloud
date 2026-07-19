package cn.iocoder.yudao.module.cloudmold.operationsintelligence.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 运营告警工单分页项")
@Data
public class OperationsAlertPageItem {

    private String alertId;
    private String alertCode;
    private String sourceType;
    private String sourceRef;
    private String severity;
    private String category;
    private String subcategory;
    private String status;
    private String currentActorPrincipalId;
    private Long aggregateVersion;
    private LocalDateTime openedAt;
    private LocalDateTime terminalAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
