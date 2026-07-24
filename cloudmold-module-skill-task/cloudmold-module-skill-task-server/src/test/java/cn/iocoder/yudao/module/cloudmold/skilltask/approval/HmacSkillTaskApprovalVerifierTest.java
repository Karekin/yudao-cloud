package cn.iocoder.yudao.module.cloudmold.skilltask.approval;

import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalRefCodec;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalScope;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacSkillTaskApprovalVerifierTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void verifiesAReferenceBoundToTheExactExecutionScope() throws Exception {
        HmacSkillTaskApprovalVerifier verifier = verifier(SECRET, Duration.ofHours(4));
        SkillTaskApprovalContext context = context(null);
        long expires = CLOCK.instant().plus(Duration.ofHours(1)).getEpochSecond();
        String reference = reference(context, "approval-0718", expires);

        SkillTaskApprovalEvidence evidence = verifier.verify(context(reference));

        assertThat(evidence.approvalId()).isEqualTo("approval-0718");
        assertThat(evidence.referenceSha256()).hasSize(64);
        assertThat(evidence.expiresAt()).isEqualTo(Instant.ofEpochSecond(expires));
        assertThat(evidence.verifier()).isEqualTo("cma1:hmac-sha256");
    }

    @Test
    void failsClosedWhenTheApprovalAuthorityIsNotConfigured() {
        HmacSkillTaskApprovalVerifier verifier = verifier("", Duration.ofHours(4));

        assertThatThrownBy(() -> verifier.verify(context("cma1:approval-0718:1:" + "0".repeat(64))))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("no approval authority");
    }

    @Test
    void rejectsScopeDriftExpiryAndExcessiveValidity() throws Exception {
        HmacSkillTaskApprovalVerifier verifier = verifier(SECRET, Duration.ofHours(4));
        SkillTaskApprovalContext original = context(null);
        long validExpiry = CLOCK.instant().plus(Duration.ofHours(1)).getEpochSecond();
        String valid = reference(original, "approval-0718", validExpiry);

        assertThatThrownBy(() -> verifier.verify(new SkillTaskApprovalContext(9L, 42L, 2,
                original.skillId(), original.skillVersion(), original.inputSha256(), original.riskLevel(), valid)))
                .isInstanceOf(SecurityException.class).hasMessageContaining("scope");

        long expiredAt = CLOCK.instant().minusSeconds(31).getEpochSecond();
        assertThatThrownBy(() -> verifier.verify(context(reference(original, "approval-old", expiredAt))))
                .isInstanceOf(SecurityException.class).hasMessageContaining("expired");

        long tooLong = CLOCK.instant().plus(Duration.ofHours(5)).getEpochSecond();
        assertThatThrownBy(() -> verifier.verify(context(reference(original, "approval-long", tooLong))))
                .isInstanceOf(SecurityException.class).hasMessageContaining("maximum");
    }

    @Test
    void rejectsAnInvalidSignatureAndWeakConfiguredSecret() {
        HmacSkillTaskApprovalVerifier verifier = verifier(SECRET, Duration.ofHours(4));
        assertThatThrownBy(() -> verifier.verify(context("cma1:approval-0718:1784380000:" + "0".repeat(64))))
                .isInstanceOf(SecurityException.class).hasMessageContaining("signature");

        assertThatThrownBy(() -> verifier("too-short", Duration.ofHours(4)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> SkillTaskApprovalRefCodec.sign("too-short".getBytes(StandardCharsets.UTF_8), "msg"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> SkillTaskApprovalRefCodec.issue("too-short".getBytes(StandardCharsets.UTF_8),
                new SkillTaskApprovalScope(8L, 42L, 2, "skill.full-chain", "1.0.0", "a".repeat(64), "R3"),
                "approval-0718", CLOCK.instant().plus(Duration.ofMinutes(5))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
    }

    private static HmacSkillTaskApprovalVerifier verifier(String secret, Duration maxValidity) {
        SkillTaskProperties properties = new SkillTaskProperties();
        properties.getApproval().setHmacSecret(secret);
        properties.getApproval().setMaxValidity(maxValidity);
        return new HmacSkillTaskApprovalVerifier(properties, CLOCK);
    }

    private static SkillTaskApprovalContext context(String reference) {
        return new SkillTaskApprovalContext(8L, 42L, 2, "skill.full-chain", "1.0.0",
                "a".repeat(64), "R3", reference);
    }

    private static String reference(SkillTaskApprovalContext context, String approvalId, long expires) throws Exception {
        String message = HmacSkillTaskApprovalVerifier.message(context, approvalId, expires);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "cma1:" + approvalId + ":" + expires + ":"
                + HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }
}
