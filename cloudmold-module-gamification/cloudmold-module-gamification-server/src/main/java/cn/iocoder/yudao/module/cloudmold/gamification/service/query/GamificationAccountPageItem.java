package cn.iocoder.yudao.module.cloudmold.gamification.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 游戏币账户分页项")
@Data
public class GamificationAccountPageItem {

    private String accountId;
    private String gameId;
    private String ownerType;
    private String ownerRef;
    private String assetClass;
    private String currencyCode;
    private Long balanceMicrounits;
    private String status;
    private Long aggregateVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
