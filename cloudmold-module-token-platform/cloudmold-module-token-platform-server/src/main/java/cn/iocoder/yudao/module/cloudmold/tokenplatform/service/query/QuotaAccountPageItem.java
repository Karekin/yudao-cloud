package cn.iocoder.yudao.module.cloudmold.tokenplatform.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold AI Token 配额账户分页项")
@Data
public class QuotaAccountPageItem {

    private String accountId;
    private String principalId;
    private Long balanceMicrounits;
    private String status;
    private Long aggregateVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
