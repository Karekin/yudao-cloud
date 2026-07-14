package cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_listing_status_history")
public class ListingStatusHistoryDO {
    @TableId(type = IdType.AUTO)
    private Long historyId;
    private Long tenantId;
    private String listingId;
    private Integer revision;
    private Long aggregateVersion;
    private String previousStatus;
    private String currentStatus;
    private Long operationId;
    private String reason;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
