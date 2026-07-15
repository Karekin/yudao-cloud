package cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.unpublish;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_listing_unpublish_saga")
public class ListingUnpublishSagaDO {
    @TableId(type = IdType.INPUT)
    private String sagaId;
    private Long tenantId;
    private String idempotencyKey;
    private String requestHash;
    private String runId;
    private String sourceEventId;
    private String sourceEntityType;
    private Long sourceAggregateVersion;
    private String merchantId;
    private String shopId;
    private String status;
    private String activeStep;
    private Integer expectedListingCount;
    private Integer unpublishedListingCount;
    private Integer skippedListingCount;
    private Integer attemptCount;
    private Integer maxAttempts;
    private LocalDateTime nextRetryAt;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private Long version;
    private String reason;
    private String correlationId;
    private String causationId;
    private LocalDateTime occurredAt;
    private String lastErrorCode;
    private String lastErrorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
