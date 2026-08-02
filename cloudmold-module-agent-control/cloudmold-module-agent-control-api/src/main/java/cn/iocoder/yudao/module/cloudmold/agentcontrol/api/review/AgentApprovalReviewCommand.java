package cn.iocoder.yudao.module.cloudmold.agentcontrol.api.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentApprovalReviewCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private String idempotencyKey;
    private String approvalId;
    private String decision;
    private String reason;
    private String modelId;
    private String modelRunId;
    private String evidenceSha256;
}
