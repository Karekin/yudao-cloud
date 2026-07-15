package cn.iocoder.yudao.module.cloudmold.aioperations.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_ai_ops_outcome_feedback")
public class AiOutcomeFeedbackDO {
    @TableId(type = IdType.INPUT)
    private String feedbackId;
    private Long tenantId;
    private String feedbackKey;
    private String runId;
    private String feedbackType;
    private String outcomeCode;
    private String evaluatorType;
    private String evidenceRef;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
