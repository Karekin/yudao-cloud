package cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_listing_review_decision")
public class ListingReviewDecisionDO {
    @TableId(type = IdType.AUTO)
    private Long decisionId;
    private Long tenantId;
    private String listingId;
    private Integer revision;
    private String stage;
    private Integer attemptNo;
    private String decision;
    private String reason;
    private Long operationId;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
