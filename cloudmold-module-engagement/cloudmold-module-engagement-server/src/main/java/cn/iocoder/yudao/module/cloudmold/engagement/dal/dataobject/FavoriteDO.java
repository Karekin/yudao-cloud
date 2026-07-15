package cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_engagement_favorite")
public class FavoriteDO {
    @TableId(type = IdType.INPUT)
    private String favoriteId;
    private Long tenantId;
    private String principalId;
    private String canonicalSpuId;
    private String status;
    private Long version;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
