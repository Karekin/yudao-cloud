package cn.iocoder.yudao.module.cloudmold.datacontract.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("cloudmold_event_inbox")
@Data
public class CloudmoldEventInboxDO {

    @TableId
    private String consumerId;
    private String eventId;
    private Long tenantId;
    private String eventType;
    private Integer schemaVersion;
    private String payloadHash;
    private LocalDateTime processedAt;
    private String resultHash;

}
