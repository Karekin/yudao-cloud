package cn.iocoder.yudao.module.cloudmold.skilltask.approval;

import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class HmacSkillTaskApprovalVerifier implements SkillTaskApprovalVerifier {

    static final String VERSION = "cma1";
    private static final String ALGORITHM = "HmacSHA256";
    private static final Pattern APPROVAL_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    private final byte[] secret;
    private final Duration maxValidity;
    private final Duration clockSkew;
    private final Clock clock;

    public HmacSkillTaskApprovalVerifier(SkillTaskProperties properties, Clock clock) {
        SkillTaskProperties.Approval approval = properties.getApproval();
        String configured = approval == null ? "" : Objects.toString(approval.getHmacSecret(), "").trim();
        if (!configured.isEmpty() && configured.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Skill Task approval HMAC secret must contain at least 32 bytes");
        }
        this.secret = configured.getBytes(StandardCharsets.UTF_8);
        this.maxValidity = positive(approval == null ? null : approval.getMaxValidity(), "maxValidity");
        this.clockSkew = nonNegative(approval == null ? null : approval.getClockSkew(), "clockSkew");
        this.clock = clock;
    }

    @Override
    public SkillTaskApprovalEvidence verify(SkillTaskApprovalContext context) {
        Objects.requireNonNull(context, "context");
        if (secret.length == 0) {
            throw new SecurityException("R2/R3 Skill approvals are disabled because no approval authority is configured");
        }
        String reference = requireText(context.approvalRef(), "approvalRef");
        String[] parts = reference.split(":", -1);
        if (parts.length != 4 || !VERSION.equals(parts[0]) || !APPROVAL_ID.matcher(parts[1]).matches()) {
            throw new SecurityException("approvalRef has an unsupported format");
        }
        long expiresEpoch;
        try {
            expiresEpoch = Long.parseLong(parts[2]);
        } catch (NumberFormatException ex) {
            throw new SecurityException("approvalRef expiry is invalid", ex);
        }
        byte[] supplied;
        try {
            supplied = HexFormat.of().parseHex(parts[3]);
        } catch (IllegalArgumentException ex) {
            throw new SecurityException("approvalRef signature is invalid", ex);
        }
        if (supplied.length != 32) {
            throw new SecurityException("approvalRef signature is invalid");
        }
        byte[] expected = sign(message(context, parts[1], expiresEpoch));
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new SecurityException("approvalRef signature or scope is invalid");
        }
        Instant now = clock.instant();
        Instant expiresAt = Instant.ofEpochSecond(expiresEpoch);
        if (!expiresAt.plus(clockSkew).isAfter(now)) {
            throw new SecurityException("approvalRef has expired");
        }
        if (expiresAt.isAfter(now.plus(maxValidity).plus(clockSkew))) {
            throw new SecurityException("approvalRef validity exceeds the configured maximum");
        }
        return new SkillTaskApprovalEvidence(parts[1], sha256(reference), expiresAt, VERSION + ":hmac-sha256");
    }

    static String message(SkillTaskApprovalContext context, String approvalId, long expiresEpoch) {
        return String.join("\n", VERSION, approvalId, Long.toString(expiresEpoch),
                Long.toString(context.tenantId()), Long.toString(context.operatorId()),
                Integer.toString(context.operatorType()), requireText(context.skillId(), "skillId"),
                requireText(context.skillVersion(), "skillVersion"),
                requireSha256(context.inputSha256()), requireRisk(context.riskLevel()));
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot verify Skill Task approval", ex);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot hash Skill Task approval reference", ex);
        }
    }

    private static String requireSha256(String value) {
        String normalized = requireText(value, "inputSha256").toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("inputSha256 must be lowercase SHA-256 hex");
        }
        return normalized;
    }

    private static String requireRisk(String value) {
        String risk = requireText(value, "riskLevel").toUpperCase(Locale.ROOT);
        if (!risk.equals("R2") && !risk.equals("R3")) {
            throw new IllegalArgumentException("approval verification is only valid for R2/R3 Skills");
        }
        return risk;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException("Skill Task approval " + field + " must be positive");
        }
        return value;
    }

    private static Duration nonNegative(Duration value, String field) {
        if (value == null || value.isNegative()) {
            throw new IllegalStateException("Skill Task approval " + field + " must not be negative");
        }
        return value;
    }
}
