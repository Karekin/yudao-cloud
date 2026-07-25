package cn.iocoder.yudao.module.cloudmold.skilltask.approval;

import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties.VerificationKey;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalPermitClaims;
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
    void verifiesClaimsBoundCma3ReferenceAndExposesFrozenClosure() {
        HmacSkillTaskApprovalVerifier verifier = verifier(SECRET, Duration.ofHours(4));
        SkillTaskApprovalContext context = context(null);
        SkillTaskApprovalPermitClaims claims = new SkillTaskApprovalPermitClaims(
                SkillTaskApprovalRefCodec.CLAIMS_VERSION, SkillTaskApprovalRefCodec.LOCAL_HMAC_KEY_ID,
                SkillTaskApprovalRefCodec.DEFAULT_ISSUER, SkillTaskApprovalRefCodec.DEFAULT_AUDIENCE,
                "permit-0718", "wo-r3-1", "approval-0718", "f".repeat(64),
                context.skillId(), context.skillVersion(), context.definitionClosureSha256(),
                context.inputSha256(), context.riskLevel(), "2:42", context.tenantId(),
                CLOCK.instant(), CLOCK.instant(), CLOCK.instant().plusSeconds(300));

        SkillTaskApprovalEvidence evidence = verifier.verify(context(
                SkillTaskApprovalRefCodec.issueClaims(SECRET.getBytes(StandardCharsets.UTF_8), claims)));

        assertThat(evidence.permitId()).isEqualTo("permit-0718");
        assertThat(evidence.rootRequestIdentity()).isEqualTo("f".repeat(64));
        assertThat(evidence.definitionClosureSha256()).isEqualTo("c".repeat(64));
        assertThat(evidence.verifier()).isEqualTo("cma3:hmac-sha256-claims:local-hmac");
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
                original.skillId(), original.skillVersion(), original.definitionClosureSha256(),
                original.inputSha256(), original.riskLevel(), valid)))
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

    @Test
    void verifiesCurrentAndPreviousKeyDuringRotation() {
        SkillTaskProperties properties = keyedProperties();
        properties.getApproval().getKeys().put("risk-previous", key(SECRET, CLOCK.instant().minusSeconds(3600),
                CLOCK.instant().plusSeconds(3600), null));
        properties.getApproval().getKeys().put("risk-current", key("abcdef0123456789abcdef0123456789",
                CLOCK.instant().minusSeconds(60), CLOCK.instant().plusSeconds(7200), null));
        HmacSkillTaskApprovalVerifier verifier = new HmacSkillTaskApprovalVerifier(properties, CLOCK);
        SkillTaskApprovalContext context = context(null);

        String previous = SkillTaskApprovalRefCodec.issueKeyed(SECRET.getBytes(StandardCharsets.UTF_8),
                "risk-previous", scope(context), "approval-old-key", CLOCK.instant().minusSeconds(30),
                CLOCK.instant().plusSeconds(300));
        String current = SkillTaskApprovalRefCodec.issueKeyed(
                "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8),
                "risk-current", scope(context), "approval-new-key", CLOCK.instant(),
                CLOCK.instant().plusSeconds(600));

        assertThat(verifier.verify(context(previous)).verifier())
                .isEqualTo("cma2:hmac-sha256-keyed:risk-previous");
        assertThat(verifier.verify(context(current)).verifier())
                .isEqualTo("cma2:hmac-sha256-keyed:risk-current");
    }

    @Test
    void rejectsUnknownRevokedAndLifetimeViolatingKeyedReferences() {
        SkillTaskProperties properties = keyedProperties();
        properties.getApproval().getKeys().put("risk-revoked", key(SECRET,
                CLOCK.instant().minusSeconds(3600), CLOCK.instant().plusSeconds(3600), CLOCK.instant()));
        properties.getApproval().getKeys().put("risk-short", key(SECRET,
                CLOCK.instant().minusSeconds(60), CLOCK.instant().plusSeconds(120), null));
        HmacSkillTaskApprovalVerifier verifier = new HmacSkillTaskApprovalVerifier(properties, CLOCK);
        SkillTaskApprovalContext context = context(null);

        String unknown = SkillTaskApprovalRefCodec.issueKeyed(SECRET.getBytes(StandardCharsets.UTF_8),
                "risk-unknown", scope(context), "approval-unknown", CLOCK.instant(),
                CLOCK.instant().plusSeconds(60));
        assertThatThrownBy(() -> verifier.verify(context(unknown)))
                .isInstanceOf(SecurityException.class).hasMessageContaining("unknown");

        String revoked = SkillTaskApprovalRefCodec.issueKeyed(SECRET.getBytes(StandardCharsets.UTF_8),
                "risk-revoked", scope(context), "approval-revoked", CLOCK.instant().minusSeconds(10),
                CLOCK.instant().plusSeconds(60));
        assertThatThrownBy(() -> verifier.verify(context(revoked)))
                .isInstanceOf(SecurityException.class).hasMessageContaining("revoked");

        String tooLongForKey = SkillTaskApprovalRefCodec.issueKeyed(SECRET.getBytes(StandardCharsets.UTF_8),
                "risk-short", scope(context), "approval-key-life", CLOCK.instant(),
                CLOCK.instant().plusSeconds(300));
        assertThatThrownBy(() -> verifier.verify(context(tooLongForKey)))
                .isInstanceOf(SecurityException.class).hasMessageContaining("key lifetime");
    }

    @Test
    void rejectsRevokedAndTamperedCma3References() {
        SkillTaskProperties revoked = keyedProperties();
        revoked.getApproval().getKeys().put("risk-revoked", key(SECRET,
                CLOCK.instant().minusSeconds(60), CLOCK.instant().plusSeconds(600), CLOCK.instant()));
        HmacSkillTaskApprovalVerifier revokedVerifier = new HmacSkillTaskApprovalVerifier(revoked, CLOCK);
        SkillTaskApprovalContext context = context(null);
        SkillTaskApprovalPermitClaims claims = new SkillTaskApprovalPermitClaims(
                SkillTaskApprovalRefCodec.CLAIMS_VERSION, "risk-revoked",
                SkillTaskApprovalRefCodec.DEFAULT_ISSUER, SkillTaskApprovalRefCodec.DEFAULT_AUDIENCE,
                "permit-0718", "wo-r3-1", "approval-0718", "f".repeat(64),
                context.skillId(), context.skillVersion(), context.definitionClosureSha256(),
                context.inputSha256(), context.riskLevel(), "2:42", context.tenantId(),
                CLOCK.instant(), CLOCK.instant(), CLOCK.instant().plusSeconds(60));
        String revokedRef = SkillTaskApprovalRefCodec.issueClaims(SECRET.getBytes(StandardCharsets.UTF_8), claims);
        assertThatThrownBy(() -> revokedVerifier.verify(context(revokedRef)))
                .isInstanceOf(SecurityException.class).hasMessageContaining("revoked");

        HmacSkillTaskApprovalVerifier localVerifier = verifier(SECRET, Duration.ofHours(4));
        String valid = SkillTaskApprovalRefCodec.issueClaims(SECRET.getBytes(StandardCharsets.UTF_8),
                new SkillTaskApprovalPermitClaims(
                        SkillTaskApprovalRefCodec.CLAIMS_VERSION, SkillTaskApprovalRefCodec.LOCAL_HMAC_KEY_ID,
                        SkillTaskApprovalRefCodec.DEFAULT_ISSUER, SkillTaskApprovalRefCodec.DEFAULT_AUDIENCE,
                        "permit-0719", "wo-r3-1", "approval-0719", "e".repeat(64),
                        context.skillId(), context.skillVersion(), context.definitionClosureSha256(),
                        context.inputSha256(), context.riskLevel(), "2:42", context.tenantId(),
                        CLOCK.instant(), CLOCK.instant(), CLOCK.instant().plusSeconds(60)));
        String tampered = valid.substring(0, valid.length() - 1) + (valid.endsWith("a") ? "b" : "a");
        assertThatThrownBy(() -> localVerifier.verify(context(tampered)))
                .isInstanceOf(SecurityException.class).hasMessageContaining("signature");
    }

    @Test
    void rejectsLegacyReferenceWhenCompatibilityModeIsDisabled() throws Exception {
        SkillTaskProperties properties = keyedProperties();
        properties.getApproval().setHmacSecret(SECRET);
        HmacSkillTaskApprovalVerifier verifier = new HmacSkillTaskApprovalVerifier(properties, CLOCK);
        SkillTaskApprovalContext context = context(null);

        assertThatThrownBy(() -> verifier.verify(context(reference(context, "approval-legacy",
                CLOCK.instant().plusSeconds(60).getEpochSecond()))))
                .isInstanceOf(SecurityException.class).hasMessageContaining("legacy cma1 approvals are disabled");
    }

    private static HmacSkillTaskApprovalVerifier verifier(String secret, Duration maxValidity) {
        SkillTaskProperties properties = new SkillTaskProperties();
        properties.getApproval().setHmacSecret(secret);
        properties.getApproval().setMaxValidity(maxValidity);
        return new HmacSkillTaskApprovalVerifier(properties, CLOCK);
    }

    private static SkillTaskProperties keyedProperties() {
        SkillTaskProperties properties = new SkillTaskProperties();
        properties.getApproval().setLegacyHmacEnabled(false);
        properties.getApproval().setMaxValidity(Duration.ofHours(4));
        return properties;
    }

    private static VerificationKey key(String secret, Instant notBefore, Instant expiresAt, Instant revokedAt) {
        return new VerificationKey().setSecret(secret).setNotBefore(notBefore)
                .setExpiresAt(expiresAt).setRevokedAt(revokedAt);
    }

    private static SkillTaskApprovalScope scope(SkillTaskApprovalContext context) {
        return new SkillTaskApprovalScope(context.tenantId(), context.operatorId(), context.operatorType(),
                context.skillId(), context.skillVersion(), context.inputSha256(), context.riskLevel());
    }

    private static SkillTaskApprovalContext context(String reference) {
        return new SkillTaskApprovalContext(8L, 42L, 2, "skill.full-chain", "1.0.0",
                "c".repeat(64), "a".repeat(64), "R3", reference);
    }

    private static String reference(SkillTaskApprovalContext context, String approvalId, long expires) throws Exception {
        String message = HmacSkillTaskApprovalVerifier.message(context, approvalId, expires);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "cma1:" + approvalId + ":" + expires + ":"
                + HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }
}
