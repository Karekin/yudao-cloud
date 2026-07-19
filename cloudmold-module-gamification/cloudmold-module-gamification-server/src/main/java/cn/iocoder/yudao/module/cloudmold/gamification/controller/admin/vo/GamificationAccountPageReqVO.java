package cn.iocoder.yudao.module.cloudmold.gamification.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 游戏币账户分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class GamificationAccountPageReqVO extends PageParam {

    @Schema(description = "账户 ID")
    private String accountId;

    @Schema(description = "游戏 ID")
    private String gameId;

    @Schema(description = "持有方类型 PLAYER/TREASURY")
    private String ownerType;

    @Schema(description = "持有方引用，支持模糊匹配")
    private String ownerRef;

    @Schema(description = "货币代码")
    private String currencyCode;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "创建时间起")
    private LocalDateTime createdAtFrom;

    @Schema(description = "创建时间止")
    private LocalDateTime createdAtTo;
}
