package cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.unpublish;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_listing_unpublish_saga_history")
public class ListingUnpublishSagaHistoryDO {
    @TableId(type = IdType.AUTO)
    private Long historyId;
    private Long tenantId;
    private String sagaId;
    private Long aggregateVersion;
    private String previousStatus;
    private String currentStatus;
    private String activeStep;
    private Integer attemptCount;
    private Integer expectedListingCount;
    private Integer unpublishedListingCount;
    private Integer skippedListingCount;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime nextRetryAt;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
