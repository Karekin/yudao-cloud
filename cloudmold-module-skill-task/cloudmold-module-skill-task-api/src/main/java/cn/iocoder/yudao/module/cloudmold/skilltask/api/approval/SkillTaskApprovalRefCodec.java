package cn.iocoder.yudao.module.cloudmold.skilltask.api.approval;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SkillTaskApprovalRefCodec {

    public static final String VERSION = "cma1";
    public static final String VERIFIER = VERSION + ":hmac-sha256";

    private static final String ALGORITHM = "HmacSHA256";
    private static final Pattern APPROVAL_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    private SkillTaskApprovalRefCodec() {
    }

    public static ParsedApprovalRef parse(String reference) {
        String value = requireText(reference, "approvalRef");
        String[] parts = value.split(":", -1);
        if (parts.length != 4 || !VERSION.equals(parts[0]) || !APPROVAL_ID.matcher(parts[1]).matches()) {
            throw new IllegalArgumentException("approvalRef has an unsupported format");
        }
        long expiresEpoch;
        try {
            expiresEpoch = Long.parseLong(parts[2]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("approvalRef expiry is invalid", ex);
        }
        byte[] signature;
        try {
            signature = HexFormat.of().parseHex(parts[3]);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("approvalRef signature is invalid", ex);
        }
        if (signature.length != 32) {
            throw new IllegalArgumentException("approvalRef signature is invalid");
        }
        return new ParsedApprovalRef(parts[1], Instant.ofEpochSecond(expiresEpoch), signature);
    }

    public static String issue(byte[] secret, SkillTaskApprovalScope scope, String approvalId, Instant expiresAt) {
        requireSecret(secret);
        long expiresEpoch = requireNonNull(expiresAt, "expiresAt").getEpochSecond();
        String normalizedApprovalId = requireApprovalId(approvalId);
        byte[] signature = sign(secret, message(scope, normalizedApprovalId, expiresEpoch));
        return VERSION + ":" + normalizedApprovalId + ":" + expiresEpoch + ":" + HexFormat.of().formatHex(signature);
    }

    public static String message(SkillTaskApprovalScope scope, String approvalId, long expiresEpoch) {
        SkillTaskApprovalScope value = requireNonNull(scope, "scope");
        if (value.tenantId() <= 0) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (value.operatorId() <= 0) {
            throw new IllegalArgumentException("operatorId is required");
        }
        if (value.operatorType() <= 0) {
            throw new IllegalArgumentException("operatorType is required");
        }
        return String.join("\n", VERSION, requireApprovalId(approvalId), Long.toString(expiresEpoch),
                Long.toString(value.tenantId()), Long.toString(value.operatorId()),
                Integer.toString(value.operatorType()), requireText(value.skillId(), "skillId"),
                requireText(value.skillVersion(), "skillVersion"),
                requireSha256(value.inputSha256()), requireRisk(value.riskLevel()));
    }

    public static byte[] sign(byte[] secret, String message) {
        requireSecret(secret);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(requireText(message, "message").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot sign Skill Task approval reference", ex);
        }
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(requireText(value, "value").getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot hash Skill Task approval reference", ex);
        }
    }

    private static String requireApprovalId(String approvalId) {
        String value = requireText(approvalId, "approvalId");
        if (!APPROVAL_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("approvalId has an unsupported format");
        }
        return value;
    }

    private static String requireRisk(String value) {
        String risk = requireText(value, "riskLevel").toUpperCase(Locale.ROOT);
        if (!risk.equals("R2") && !risk.equals("R3")) {
            throw new IllegalArgumentException("approval verification is only valid for R2/R3 Skills");
        }
        return risk;
    }

    private static String requireSha256(String value) {
        String normalized = requireText(value, "inputSha256").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("inputSha256 must be lowercase SHA-256 hex");
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static void requireSecret(byte[] secret) {
        if (secret == null || secret.length == 0) {
            throw new IllegalStateException(
                    "Skill Task approval signing is disabled because no signing secret is configured");
        }
        if (secret.length < 32) {
            throw new IllegalStateException("Skill Task approval signing secret must contain at least 32 bytes");
        }
    }

    public record ParsedApprovalRef(String approvalId, Instant expiresAt, byte[] signature) {
    }
}
