package cn.iocoder.yudao.module.cloudmold.identity.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_identity_source_identity")
public class SourceIdentityDO {
    @TableId(type = IdType.INPUT)
    private String sourceIdentityId;
    private Long tenantId;
    private String principalId;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private String status;
    private Long version;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
