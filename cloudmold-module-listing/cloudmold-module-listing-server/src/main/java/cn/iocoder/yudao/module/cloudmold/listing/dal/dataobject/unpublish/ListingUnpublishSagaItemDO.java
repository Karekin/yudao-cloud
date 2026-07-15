package cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.unpublish;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_listing_unpublish_saga_item")
public class ListingUnpublishSagaItemDO {
    @TableId(type = IdType.INPUT)
    private String sagaItemId;
    private Long tenantId;
    private String sagaId;
    private String listingId;
    private Long listingVersionAtRequest;
    private String unpublishIdempotencyKey;
    private String status;
    private Integer attemptCount;
    private Long listingOperationId;
    private String finalListingStatus;
    private Long finalListingVersion;
    private String lastErrorCode;
    private String lastErrorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
