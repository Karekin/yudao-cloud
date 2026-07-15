package cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_engagement_favorite_behavior")
public class FavoriteBehaviorDO {
    @TableId(type = IdType.INPUT)
    private String behaviorId;
    private Long tenantId;
    private String favoriteId;
    private String principalId;
    private String canonicalSpuId;
    private String behaviorType;
    private Long favoriteVersion;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
