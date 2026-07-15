package cn.iocoder.yudao.module.cloudmold.aioperations.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_ai_ops_application")
public class AiApplicationDO {
    @TableId(type = IdType.INPUT)
    private String applicationId;
    private Long tenantId;
    private String applicationCode;
    private String name;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
