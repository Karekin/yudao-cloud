package cn.iocoder.yudao.module.cloudmold.skilltask.api.approval;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SkillTaskApprovalRefCodec {

    public static final String LEGACY_VERSION = "cma1";
    public static final String VERSION = "cma2";
    public static final String CLAIMS_VERSION = "cma3";
    public static final String LEGACY_VERIFIER = LEGACY_VERSION + ":hmac-sha256";
    public static final String VERIFIER = VERSION + ":hmac-sha256-keyed";
    public static final String CLAIMS_VERIFIER = CLAIMS_VERSION + ":hmac-sha256-claims";
    public static final String DEFAULT_ISSUER = "cloudmold.agent-control";
    public static final String DEFAULT_AUDIENCE = "cloudmold.skill-task";
    public static final String LOCAL_HMAC_KEY_ID = "local-hmac";

    private static final String ALGORITHM = "HmacSHA256";
    private static final Pattern APPROVAL_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9._-]{3,64}");
    private static final Pattern WORK_ORDER_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern PERMIT_ID = Pattern.compile("[A-Za-z0-9._-]{8,128}");
    private static final Pattern SUBJECT = Pattern.compile("[0-9]+:[0-9]+");

    private SkillTaskApprovalRefCodec() {
    }

    public static ParsedApprovalRef parse(String reference) {
        String value = requireText(reference, "approvalRef");
        String[] parts = value.split(":", -1);
        if (parts.length != 4 || !LEGACY_VERSION.equals(parts[0]) || !APPROVAL_ID.matcher(parts[1]).matches()) {
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
        return new ParsedApprovalRef(parts[1], parseInstant(expiresEpoch, "expiry"), signature);
    }

    public static String issue(byte[] secret, SkillTaskApprovalScope scope, String approvalId, Instant expiresAt) {
        requireSecret(secret);
        long expiresEpoch = requireNonNull(expiresAt, "expiresAt").getEpochSecond();
        String normalizedApprovalId = requireApprovalId(approvalId);
        byte[] signature = sign(secret, message(scope, normalizedApprovalId, expiresEpoch));
        return LEGACY_VERSION + ":" + normalizedApprovalId + ":" + expiresEpoch + ":"
                + HexFormat.of().formatHex(signature);
    }

    public static String message(SkillTaskApprovalScope scope, String approvalId, long expiresEpoch) {
        return scopeMessage(LEGACY_VERSION, scope, requireApprovalId(approvalId), expiresEpoch);
    }

    public static ParsedKeyedApprovalRef parseKeyed(String reference) {
        String value = requireText(reference, "approvalRef");
        String[] parts = value.split(":", -1);
        if (parts.length != 6 || !VERSION.equals(parts[0]) || !KEY_ID.matcher(parts[1]).matches()
                || !APPROVAL_ID.matcher(parts[2]).matches()) {
            throw new IllegalArgumentException("approvalRef has an unsupported format");
        }
        long issuedEpoch = parseEpoch(parts[3], "issued-at");
        long expiresEpoch = parseEpoch(parts[4], "expiry");
        byte[] signature = parseSignature(parts[5]);
        return new ParsedKeyedApprovalRef(parts[1], parts[2], parseInstant(issuedEpoch, "issued-at"),
                parseInstant(expiresEpoch, "expiry"), signature);
    }

    public static ParsedClaimsApprovalRef parseClaims(String reference) {
        String value = requireText(reference, "approvalRef");
        String[] parts = value.split(":", -1);
        if (parts.length != 4 || !CLAIMS_VERSION.equals(parts[0]) || !KEY_ID.matcher(parts[1]).matches()) {
            throw new IllegalArgumentException("approvalRef has an unsupported format");
        }
        byte[] payloadBytes;
        try {
            payloadBytes = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("approvalRef payload is invalid", ex);
        }
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        String[] fields = payload.split("\n", -1);
        if (fields.length != 16) {
            throw new IllegalArgumentException("approvalRef payload is invalid");
        }
        SkillTaskApprovalPermitClaims claims = new SkillTaskApprovalPermitClaims(
                CLAIMS_VERSION,
                requireKeyId(parts[1]),
                requireText(fields[0], "issuer"),
                requireText(fields[1], "audience"),
                requirePermitId(fields[2]),
                requireWorkOrderId(fields[3]),
                requireApprovalId(fields[4]),
                requireSha256(fields[5], "rootRequestIdentity"),
                requireText(fields[6], "skillId"),
                requireText(fields[7], "skillVersion"),
                requireSha256(fields[8], "definitionClosureSha256"),
                requireSha256(fields[9], "inputSha256"),
                requireRisk(fields[10]),
                requireSubject(fields[11]),
                requirePositiveLong(fields[12], "tenantId"),
                parseInstant(parseEpoch(fields[13], "not-before"), "not-before"),
                parseInstant(parseEpoch(fields[14], "issued-at"), "issued-at"),
                parseInstant(parseEpoch(fields[15], "expiry"), "expiry"));
        byte[] signature = parseSignature(parts[3]);
        return new ParsedClaimsApprovalRef(claims, payload, signature);
    }

    public static String issueKeyed(byte[] secret, String keyId, SkillTaskApprovalScope scope, String approvalId,
                                    Instant issuedAt, Instant expiresAt) {
        requireSecret(secret);
        String normalizedKeyId = requireKeyId(keyId);
        String normalizedApprovalId = requireApprovalId(approvalId);
        long issuedEpoch = requireNonNull(issuedAt, "issuedAt").getEpochSecond();
        long expiresEpoch = requireNonNull(expiresAt, "expiresAt").getEpochSecond();
        if (expiresEpoch <= issuedEpoch) {
            throw new IllegalArgumentException("approvalRef expiry must be after issuedAt");
        }
        byte[] signature = sign(secret, keyedMessage(scope, normalizedKeyId, normalizedApprovalId,
                issuedEpoch, expiresEpoch));
        return String.join(":", VERSION, normalizedKeyId, normalizedApprovalId, Long.toString(issuedEpoch),
                Long.toString(expiresEpoch), HexFormat.of().formatHex(signature));
    }

    public static String keyedMessage(SkillTaskApprovalScope scope, String keyId, String approvalId,
                                      long issuedEpoch, long expiresEpoch) {
        if (expiresEpoch <= issuedEpoch) {
            throw new IllegalArgumentException("approvalRef expiry must be after issuedAt");
        }
        SkillTaskApprovalScope value = requireScope(scope);
        return String.join("\n", VERSION, requireKeyId(keyId), requireApprovalId(approvalId),
                Long.toString(issuedEpoch), Long.toString(expiresEpoch),
                Long.toString(value.tenantId()), Long.toString(value.operatorId()),
                Integer.toString(value.operatorType()), requireText(value.skillId(), "skillId"),
                requireText(value.skillVersion(), "skillVersion"),
                requireSha256(value.inputSha256()), requireRisk(value.riskLevel()));
    }

    public static String issueClaims(byte[] secret, SkillTaskApprovalPermitClaims claims) {
        requireSecret(secret);
        SkillTaskApprovalPermitClaims normalized = requireClaims(claims);
        String payload = claimsPayload(normalized);
        byte[] signature = sign(secret, claimsMessage(normalized.keyId(), payload));
        return String.join(":", CLAIMS_VERSION, normalized.keyId(),
                Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(payload.getBytes(StandardCharsets.UTF_8)),
                HexFormat.of().formatHex(signature));
    }

    public static String claimsMessage(String keyId, String payload) {
        return String.join("\n", CLAIMS_VERSION, requireKeyId(keyId), requireText(payload, "payload"));
    }

    public static SkillTaskApprovalPermitSummary summarize(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        if (reference.startsWith(CLAIMS_VERSION + ":")) {
            ParsedClaimsApprovalRef parsed = parseClaims(reference);
            SkillTaskApprovalPermitClaims claims = parsed.claims();
            return SkillTaskApprovalPermitSummary.builder()
                    .permitVersion(CLAIMS_VERSION)
                    .keyId(claims.keyId())
                    .permitId(claims.permitId())
                    .workOrderId(claims.workOrderId())
                    .approvalId(claims.approvalId())
                    .verifier(CLAIMS_VERIFIER + ":" + claims.keyId())
                    .referenceSha256(sha256(reference))
                    .issuedAt(claims.issuedAt())
                    .expiresAt(claims.expiresAt())
                    .build();
        }
        if (reference.startsWith(VERSION + ":")) {
            ParsedKeyedApprovalRef parsed = parseKeyed(reference);
            return SkillTaskApprovalPermitSummary.builder()
                    .permitVersion(VERSION)
                    .keyId(parsed.keyId())
                    .approvalId(parsed.approvalId())
                    .verifier(VERIFIER + ":" + parsed.keyId())
                    .referenceSha256(sha256(reference))
                    .issuedAt(parsed.issuedAt())
                    .expiresAt(parsed.expiresAt())
                    .build();
        }
        ParsedApprovalRef parsed = parse(reference);
        return SkillTaskApprovalPermitSummary.builder()
                .permitVersion(LEGACY_VERSION)
                .approvalId(parsed.approvalId())
                .verifier(LEGACY_VERIFIER)
                .referenceSha256(sha256(reference))
                .expiresAt(parsed.expiresAt())
                .build();
    }

    private static String scopeMessage(String prefix, SkillTaskApprovalScope scope, String approvalId,
                                       long expiresEpoch) {
        SkillTaskApprovalScope value = requireScope(scope);
        return String.join("\n", prefix, requireApprovalId(approvalId), Long.toString(expiresEpoch),
                Long.toString(value.tenantId()), Long.toString(value.operatorId()),
                Integer.toString(value.operatorType()), requireText(value.skillId(), "skillId"),
                requireText(value.skillVersion(), "skillVersion"),
                requireSha256(value.inputSha256()), requireRisk(value.riskLevel()));
    }

    private static SkillTaskApprovalScope requireScope(SkillTaskApprovalScope scope) {
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
        return value;
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

    private static String requireWorkOrderId(String value) {
        String result = requireText(value, "workOrderId");
        if (!WORK_ORDER_ID.matcher(result).matches()) {
            throw new IllegalArgumentException("workOrderId has an unsupported format");
        }
        return result;
    }

    private static String requireKeyId(String keyId) {
        String value = requireText(keyId, "keyId");
        if (!KEY_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("keyId has an unsupported format");
        }
        return value;
    }

    private static String requirePermitId(String value) {
        String result = requireText(value, "permitId");
        if (!PERMIT_ID.matcher(result).matches()) {
            throw new IllegalArgumentException("permitId has an unsupported format");
        }
        return result;
    }

    private static String requireSubject(String value) {
        String result = requireText(value, "subject");
        if (!SUBJECT.matcher(result).matches()) {
            throw new IllegalArgumentException("subject has an unsupported format");
        }
        return result;
    }

    private static long parseEpoch(String value, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("approvalRef " + field + " is invalid", ex);
        }
    }

    private static byte[] parseSignature(String value) {
        byte[] signature;
        try {
            signature = HexFormat.of().parseHex(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("approvalRef signature is invalid", ex);
        }
        if (signature.length != 32) {
            throw new IllegalArgumentException("approvalRef signature is invalid");
        }
        return signature;
    }

    private static Instant parseInstant(long value, String field) {
        try {
            return Instant.ofEpochSecond(value);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("approvalRef " + field + " is invalid", ex);
        }
    }

    private static String requireRisk(String value) {
        String risk = requireText(value, "riskLevel").toUpperCase(Locale.ROOT);
        if (!risk.equals("R2") && !risk.equals("R3")) {
            throw new IllegalArgumentException("approval verification is only valid for R2/R3 Skills");
        }
        return risk;
    }

    private static String requireSha256(String value) {
        return requireSha256(value, "inputSha256");
    }

    private static String requireSha256(String value, String field) {
        String normalized = requireText(value, field).toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256 hex");
        }
        return normalized;
    }

    private static long requirePositiveLong(String value, String field) {
        long parsed = parseEpoch(value, field);
        if (parsed <= 0) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return parsed;
    }

    private static SkillTaskApprovalPermitClaims requireClaims(SkillTaskApprovalPermitClaims claims) {
        SkillTaskApprovalPermitClaims value = requireNonNull(claims, "claims");
        Instant nbf = requireNonNull(value.notBefore(), "notBefore");
        Instant iat = requireNonNull(value.issuedAt(), "issuedAt");
        Instant exp = requireNonNull(value.expiresAt(), "expiresAt");
        if (exp.getEpochSecond() <= iat.getEpochSecond()) {
            throw new IllegalArgumentException("approvalRef expiry must be after issuedAt");
        }
        if (nbf.getEpochSecond() > iat.getEpochSecond()) {
            throw new IllegalArgumentException("approvalRef notBefore must not be after issuedAt");
        }
        return new SkillTaskApprovalPermitClaims(
                CLAIMS_VERSION,
                requireKeyId(value.keyId()),
                requireText(value.issuer(), "issuer"),
                requireText(value.audience(), "audience"),
                requirePermitId(value.permitId()),
                requireWorkOrderId(value.workOrderId()),
                requireApprovalId(value.approvalId()),
                requireSha256(value.rootRequestIdentity(), "rootRequestIdentity"),
                requireText(value.skillId(), "skillId"),
                requireText(value.skillVersion(), "skillVersion"),
                requireSha256(value.definitionClosureSha256(), "definitionClosureSha256"),
                requireSha256(value.inputSha256(), "inputSha256"),
                requireRisk(value.riskLevel()),
                requireSubject(value.subject()),
                value.tenantId() > 0 ? value.tenantId() : requirePositiveLong(Long.toString(value.tenantId()), "tenantId"),
                nbf, iat, exp);
    }

    private static String claimsPayload(SkillTaskApprovalPermitClaims claims) {
        return String.join("\n",
                claims.issuer(),
                claims.audience(),
                claims.permitId(),
                claims.workOrderId(),
                claims.approvalId(),
                claims.rootRequestIdentity(),
                claims.skillId(),
                claims.skillVersion(),
                claims.definitionClosureSha256(),
                claims.inputSha256(),
                claims.riskLevel(),
                claims.subject(),
                Long.toString(claims.tenantId()),
                Long.toString(claims.notBefore().getEpochSecond()),
                Long.toString(claims.issuedAt().getEpochSecond()),
                Long.toString(claims.expiresAt().getEpochSecond()));
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

    public record ParsedKeyedApprovalRef(String keyId, String approvalId, Instant issuedAt, Instant expiresAt,
                                         byte[] signature) {
    }

    public record ParsedClaimsApprovalRef(SkillTaskApprovalPermitClaims claims, String payload, byte[] signature) {
    }
}
