package cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_after_sale_resolution_saga_history")
public class AfterSaleResolutionSagaHistoryDO {
    @TableId(type = IdType.AUTO)
    private Long historyId;
    private Long tenantId;
    private String sagaId;
    private Long aggregateVersion;
    private String previousStatus;
    private String currentStatus;
    private String activeStep;
    private Integer attemptCount;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime nextRetryAt;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
