package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.SkillTaskApprovalSigningProperties;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.SkillTaskApprovalSigningProperties.RemoteVerificationKey;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalPermitClaims;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalRefCodec;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskPemKeys;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Primary
@Component
@ConditionalOnProperty(prefix = "cloudmold.skill-task.approval", name = "authority-mode",
        havingValue = "REMOTE_RSA")
public class RemoteRiskAuthorityExecutionPermitSigner implements AgentExecutionPermitSigner {

    static final String ALGORITHM = "RS256";
    static final int MINIMUM_RSA_BITS = 3072;

    private final SkillTaskApprovalSigningProperties properties;
    private final RiskAuthoritySigningPort signingPort;
    private final Map<String, VerificationKeyMaterial> verificationKeys;
    private final Clock clock;

    public RemoteRiskAuthorityExecutionPermitSigner(SkillTaskApprovalSigningProperties properties,
                                                     RiskAuthoritySigningPort signingPort) {
        this(properties, signingPort, Clock.systemUTC());
    }

    RemoteRiskAuthorityExecutionPermitSigner(SkillTaskApprovalSigningProperties properties,
                                             RiskAuthoritySigningPort signingPort, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.signingPort = Objects.requireNonNull(signingPort, "signingPort");
        this.verificationKeys = configuredVerificationKeys(properties.getRemote());
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public SignedAgentExecutionPermit sign(SkillTaskApprovalPermitClaims claims) {
        SkillTaskApprovalPermitClaims requested = requireClaims(claims);
        require(ALGORITHM.equalsIgnoreCase(Objects.toString(
                properties.getRemote().getAlgorithm(), ALGORITHM)), "remote signing algorithm must be RS256");
        require(requested.issuer().equals(requireText(properties.getIssuer(), "issuer"))
                        && requested.audience().equals(requireText(properties.getAudience(), "audience")),
                "execution permit issuer or audience does not match the configured Risk Authority");
        Duration validity = Duration.between(requested.issuedAt(), requested.expiresAt());
        require(!validity.isNegative() && !validity.isZero()
                        && validity.compareTo(requirePositive(properties.getMaxValidity(), "maxValidity")) <= 0,
                "execution ticket validity exceeds the configured maximum");

        String payload = SkillTaskApprovalRefCodec.claimsPayload(requested);
        String requestId = SkillTaskApprovalRefCodec.sha256(String.join("\n",
                "risk-authority-sign", requested.permitId(), payload));
        RiskAuthoritySigningPort.RiskAuthoritySignRequest request =
                new RiskAuthoritySigningPort.RiskAuthoritySignRequest(
                        requestId, requested.permitId(), ALGORITHM, payload);
        RiskAuthoritySigningPort.RiskAuthoritySignResponse response = signingPort.sign(
                request, requirePositive(properties.getRemote().getRequestTimeout(), "remote.requestTimeout"));
        require(response != null, "Risk Authority returned no signing response");
        require(requestId.equals(response.requestId()), "Risk Authority response requestId does not match the request");
        require(requested.permitId().equals(response.permitId()),
                "Risk Authority response permitId does not match the request");
        require(ALGORITHM.equals(response.algorithm()), "Risk Authority response algorithm is not RS256");
        String keyId = requireText(response.keyId(), "response.keyId");
        String expectedKeyId = Objects.toString(properties.getRemote().getExpectedKeyId(), "").trim();
        require(expectedKeyId.isEmpty() || expectedKeyId.equals(keyId),
                "Risk Authority response keyId does not match the configured key");
        VerificationKeyMaterial key = verificationKeys.get(keyId);
        require(key != null, "Risk Authority response keyId is not trusted");
        requireKeyEpoch(key, requested, clock.instant());

        byte[] signature = decodeSignature(response.signature(), key.publicKey());
        String message = SkillTaskApprovalRefCodec.claimsMessage(keyId, payload);
        requireSignature(key.publicKey(), message, signature);
        SkillTaskApprovalPermitClaims issued = requested.withKeyId(keyId);
        String approvalRef = SkillTaskApprovalRefCodec.issueClaimsWithSignature(issued, signature);
        return new SignedAgentExecutionPermit(approvalRef, SkillTaskApprovalRefCodec.sha256(approvalRef),
                SkillTaskApprovalRefCodec.CLAIMS_VERSION, keyId, issued.expiresAt());
    }

    private static void requireKeyEpoch(VerificationKeyMaterial key, SkillTaskApprovalPermitClaims claims,
                                        Instant now) {
        require(!now.isBefore(key.notBefore()) && now.isBefore(key.expiresAt()),
                "Risk Authority response key is not currently active");
        require(key.revokedAt() == null || now.isBefore(key.revokedAt()),
                "Risk Authority response key is revoked");
        require(!claims.issuedAt().isBefore(key.notBefore()),
                "Risk Authority response key was not active at permit issuance");
        require(!claims.notBefore().isBefore(key.notBefore()),
                "execution permit notBefore precedes the Risk Authority key epoch");
        require(!claims.expiresAt().isAfter(key.expiresAt()),
                "execution permit exceeds the Risk Authority key epoch");
    }

    private static Map<String, VerificationKeyMaterial> configuredVerificationKeys(
            SkillTaskApprovalSigningProperties.RemoteSigner remote) {
        require(remote != null, "remote signer configuration is required");
        Map<String, RemoteVerificationKey> configured = remote.getVerificationKeys();
        require(configured != null && !configured.isEmpty(),
                "remote signer verification keys are required");
        Map<String, VerificationKeyMaterial> result = new LinkedHashMap<>();
        configured.forEach((keyId, value) -> {
            require(keyId != null && keyId.matches("[A-Za-z0-9._-]{3,64}") && value != null,
                    "remote signer verification key configuration is invalid");
            PublicKey publicKey = SkillTaskPemKeys.rsaPublicKey(value.getPublicKeyPem());
            requireRsaStrength(publicKey);
            Instant notBefore = requireInstant(value.getNotBefore(), "remote verification key notBefore");
            Instant expiresAt = requireInstant(value.getExpiresAt(), "remote verification key expiresAt");
            require(expiresAt.isAfter(notBefore),
                    "remote verification key expiresAt must be after notBefore");
            result.put(keyId, new VerificationKeyMaterial(publicKey, notBefore, expiresAt, value.getRevokedAt()));
        });
        return Map.copyOf(result);
    }

    public static void requireRsaStrength(PublicKey publicKey) {
        require(publicKey instanceof RSAPublicKey
                        && ((RSAPublicKey) publicKey).getModulus().bitLength() >= MINIMUM_RSA_BITS,
                "Risk Authority RSA public key must be at least 3072 bits");
    }

    private static byte[] decodeSignature(String encoded, PublicKey publicKey) {
        String value = requireText(encoded, "response.signature");
        require(value.matches("[A-Za-z0-9_-]+"), "Risk Authority response signature is not base64url");
        byte[] signature;
        try {
            signature = Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Risk Authority response signature is not base64url");
        }
        int expectedLength = (((RSAPublicKey) publicKey).getModulus().bitLength() + 7) / 8;
        require(signature.length == expectedLength,
                "Risk Authority response signature length does not match its RSA key");
        return signature;
    }

    private static void requireSignature(PublicKey publicKey, String message, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            require(verifier.verify(signature), "Risk Authority response signature is invalid");
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Risk Authority response signature cannot be verified", ex);
        }
    }

    private static SkillTaskApprovalPermitClaims requireClaims(SkillTaskApprovalPermitClaims value) {
        Objects.requireNonNull(value, "claims");
        SkillTaskApprovalRefCodec.claimsPayload(value);
        return value;
    }

    private static Duration requirePositive(Duration value, String field) {
        require(value != null && !value.isZero() && !value.isNegative(), field + " must be positive");
        return value;
    }

    private static Instant requireInstant(Instant value, String field) {
        require(value != null, field + " is required");
        return value;
    }

    private static String requireText(String value, String field) {
        String result = Objects.toString(value, "").trim();
        require(!result.isEmpty(), field + " is required");
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record VerificationKeyMaterial(PublicKey publicKey, Instant notBefore, Instant expiresAt,
                                           Instant revokedAt) {
    }
}
