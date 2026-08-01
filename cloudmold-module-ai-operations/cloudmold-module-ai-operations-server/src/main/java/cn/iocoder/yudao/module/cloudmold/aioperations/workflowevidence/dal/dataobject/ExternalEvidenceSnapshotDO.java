package cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_ai_ops_external_evidence_snapshot")
public class ExternalEvidenceSnapshotDO {
    @TableId(type = IdType.INPUT)
    private String snapshotId;
    private Long tenantId;
    private String lineageId;
    private String workflowId;
    private String workflowVersion;
    private String proposalId;
    private String sourceType;
    private String sourceClass;
    private String url;
    private LocalDateTime publishedAt;
    private LocalDateTime fetchedAt;
    private String summaryText;
    private String contentHashSha256;
    private BigDecimal confidence;
    private String region;
    private String applicability;
    private String severity;
    private String dqcStatus;
    private String recordStatus;
    private LocalDateTime freshUntil;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private Boolean releaseEligible;
    private String actorSubject;
    private LocalDateTime createdAt;
}
