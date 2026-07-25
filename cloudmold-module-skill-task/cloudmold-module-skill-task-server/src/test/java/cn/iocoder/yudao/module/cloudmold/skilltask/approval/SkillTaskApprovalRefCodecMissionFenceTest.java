package cn.iocoder.yudao.module.cloudmold.skilltask.approval;

import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalPermitClaims;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalRefCodec;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillTaskApprovalRefCodecMissionFenceTest {

    @Test
    void roundTripsCompleteMissionFence() {
        SkillTaskApprovalPermitClaims claims = missionClaims();

        String reference = SkillTaskApprovalRefCodec.issueClaims("x".repeat(32).getBytes(StandardCharsets.UTF_8),
                claims);

        assertThat(SkillTaskApprovalRefCodec.parseClaims(reference).claims())
                .extracting(SkillTaskApprovalPermitClaims::missionRunId,
                        SkillTaskApprovalPermitClaims::fencingToken,
                        SkillTaskApprovalPermitClaims::leaseOwner,
                        SkillTaskApprovalPermitClaims::leaseEpoch)
                .containsExactly("run-2", 9L, "operator:42", 3L);
    }

    @Test
    void rejectsPartiallyPopulatedMissionFence() {
        String[] fields = SkillTaskApprovalRefCodec.claimsPayload(missionClaims()).split("\n", -1);
        fields[17] = "";
        String payload = String.join("\n", fields);
        String reference = String.join(":", SkillTaskApprovalRefCodec.CLAIMS_VERSION, "local-hmac",
                Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(payload.getBytes(StandardCharsets.UTF_8)),
                "00".repeat(32));

        assertThatThrownBy(() -> SkillTaskApprovalRefCodec.parseClaims(reference))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("mission lease fence must contain all fields");
    }

    private SkillTaskApprovalPermitClaims missionClaims() {
        return new SkillTaskApprovalPermitClaims(SkillTaskApprovalRefCodec.CLAIMS_VERSION, "local-hmac",
                SkillTaskApprovalRefCodec.DEFAULT_ISSUER, SkillTaskApprovalRefCodec.DEFAULT_AUDIENCE,
                "permit-12345678", "work-order-1", "approval-1", "a".repeat(64),
                "inventory.allocate", "1.0.0", "b".repeat(64), "c".repeat(64), "R2", "1:42", 8L,
                Instant.parse("2026-07-25T10:00:00Z"), Instant.parse("2026-07-25T10:00:00Z"),
                Instant.parse("2026-07-25T10:05:00Z"), "run-2", 9L, "operator:42", 3L);
    }
}
