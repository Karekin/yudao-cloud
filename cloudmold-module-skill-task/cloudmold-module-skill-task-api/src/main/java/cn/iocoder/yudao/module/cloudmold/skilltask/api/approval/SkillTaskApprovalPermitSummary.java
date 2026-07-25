package cn.iocoder.yudao.module.cloudmold.skilltask.api.approval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillTaskApprovalPermitSummary implements Serializable {

    private String permitVersion;
    private String keyId;
    private String permitId;
    private String workOrderId;
    private String approvalId;
    private String verifier;
    private String referenceSha256;
    private Instant issuedAt;
    private Instant expiresAt;
}
