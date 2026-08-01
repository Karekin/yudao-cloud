package cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_ai_ops_workflow_evidence_operation")
public class WorkflowEvidenceOperationDO {
    @TableId(type = IdType.AUTO)
    private Long operationId;
    private Long tenantId;
    private String operationType;
    private String idempotencyKey;
    private String requestHash;
    private String attemptToken;
    private Integer status;
    private String aggregateType;
    private String aggregateId;
    private String resultJson;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
