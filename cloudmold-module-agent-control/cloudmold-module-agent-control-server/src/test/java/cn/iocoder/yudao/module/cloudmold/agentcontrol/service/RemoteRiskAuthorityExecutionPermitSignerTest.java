package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.SkillTaskApprovalSigningProperties;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalPermitClaims;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalRefCodec;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteRiskAuthorityExecutionPermitSignerTest {

    private static final Instant NOW = Instant.parse("2026-07-25T10:00:00Z");
    private static final String KEY_ID = "risk-epoch-7";

    @Test
    void signsThroughThePortWithDeterministicIdempotencyAndPreservesMissionFence() throws Exception {
        KeyPair pair = rsaKeyPair(3072);
        SkillTaskApprovalSigningProperties properties = properties(pair);
        List<RiskAuthoritySigningPort.RiskAuthoritySignRequest> requests = new ArrayList<>();
        List<Duration> timeouts = new ArrayList<>();
        RiskAuthoritySigningPort port = (request, timeout) -> {
            requests.add(request);
            timeouts.add(timeout);
            return validResponse(pair, request);
        };
        RemoteRiskAuthorityExecutionPermitSigner signer =
                signer(properties, port);

        SignedAgentExecutionPermit first = signer.sign(claims());
        SignedAgentExecutionPermit replay = signer.sign(claims());

        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).requestId()).isEqualTo(requests.get(1).requestId());
        assertThat(requests.get(0).permitId()).isEqualTo("permit-remote-0001");
        assertThat(timeouts).containsOnly(Duration.ofSeconds(3));
        assertThat(first.approvalRef()).isEqualTo(replay.approvalRef());
        SkillTaskApprovalPermitClaims issued =
                SkillTaskApprovalRefCodec.parseClaims(first.approvalRef()).claims();
        assertThat(issued.keyId()).isEqualTo(KEY_ID);
        assertThat(issued.missionRunId()).isEqualTo("mission-run-7");
        assertThat(issued.fencingToken()).isEqualTo(41L);
        assertThat(issued.leaseOwner()).isEqualTo("agent-control-1");
        assertThat(issued.leaseEpoch()).isEqualTo(9L);
        assertThat(first.approvalRefSha256())
                .isEqualTo(SkillTaskApprovalRefCodec.sha256(first.approvalRef()));
    }

    @Test
    void rejectsResponseIdentityAlgorithmAndUntrustedKeyDrift() throws Exception {
        KeyPair pair = rsaKeyPair(3072);
        SkillTaskApprovalSigningProperties properties = properties(pair);

        assertRejected(properties, (request, valid) ->
                        new RiskAuthoritySigningPort.RiskAuthoritySignResponse(
                                "wrong-request", request.permitId(), valid.keyId(),
                                valid.algorithm(), valid.signature()),
                "requestId");
        assertRejected(properties, (request, valid) ->
                        new RiskAuthoritySigningPort.RiskAuthoritySignResponse(
                                request.requestId(), "wrong-permit", valid.keyId(),
                                valid.algorithm(), valid.signature()),
                "permitId");
        assertRejected(properties, (request, valid) ->
                        new RiskAuthoritySigningPort.RiskAuthoritySignResponse(
                                request.requestId(), request.permitId(), valid.keyId(),
                                "RS512", valid.signature()),
                "algorithm");
        assertRejected(properties, (request, valid) ->
                        new RiskAuthoritySigningPort.RiskAuthoritySignResponse(
                                request.requestId(), request.permitId(), "risk-unknown",
                                valid.algorithm(), valid.signature()),
                "keyId");
    }

    @Test
    void rejectsMalformedAndCryptographicallyInvalidSignatures() throws Exception {
        KeyPair pair = rsaKeyPair(3072);
        SkillTaskApprovalSigningProperties properties = properties(pair);
        assertRejected(properties, (request, valid) ->
                        new RiskAuthoritySigningPort.RiskAuthoritySignResponse(
                                request.requestId(), request.permitId(), valid.keyId(),
                                valid.algorithm(), "not+base64"),
                "base64url");

        byte[] invalid = new byte[384];
        assertRejected(properties, (request, valid) ->
                        new RiskAuthoritySigningPort.RiskAuthoritySignResponse(
                                request.requestId(), request.permitId(), valid.keyId(),
                                valid.algorithm(), Base64.getUrlEncoder().withoutPadding().encodeToString(invalid)),
                "signature is invalid");
    }

    @Test
    void rejectsWeakRemoteVerificationKeyBeforeAnyRequest() throws Exception {
        SkillTaskApprovalSigningProperties properties = properties(rsaKeyPair(2048));
        final boolean[] invoked = {false};

        assertThatThrownBy(() -> signer(properties, (request, timeout) -> {
            invoked[0] = true;
            return null;
        })).hasMessageContaining("3072");
        assertThat(invoked[0]).isFalse();
    }

    @Test
    void rejectsRemoteResponseOutsideTheTrustedKeyEpoch() throws Exception {
        KeyPair pair = rsaKeyPair(3072);
        SkillTaskApprovalSigningProperties notActive = properties(pair);
        notActive.getRemote().getVerificationKeys().get(KEY_ID).setNotBefore(NOW.plusSeconds(1));
        RemoteRiskAuthorityExecutionPermitSigner signer = signer(
                notActive, (request, timeout) -> validResponse(pair, request));
        assertThatThrownBy(() -> signer.sign(claims())).hasMessageContaining("currently active");

        SkillTaskApprovalSigningProperties expiring = properties(pair);
        expiring.getRemote().getVerificationKeys().get(KEY_ID).setExpiresAt(NOW.plusSeconds(120));
        RemoteRiskAuthorityExecutionPermitSigner expiringSigner = signer(
                expiring, (request, timeout) -> validResponse(pair, request));
        assertThatThrownBy(() -> expiringSigner.sign(claims())).hasMessageContaining("key epoch");
    }

    private static void assertRejected(
            SkillTaskApprovalSigningProperties properties,
            BiFunction<RiskAuthoritySigningPort.RiskAuthoritySignRequest,
                    RiskAuthoritySigningPort.RiskAuthoritySignResponse,
                    RiskAuthoritySigningPort.RiskAuthoritySignResponse> mutation,
            String message) throws Exception {
        KeyPair pair = rsaKeyPair(3072);
        properties.getRemote().getVerificationKeys().get(KEY_ID).setPublicKeyPem(publicKeyPem(pair));
        RemoteRiskAuthorityExecutionPermitSigner signer = signer(
                properties, (request, timeout) -> mutation.apply(request, validResponse(pair, request)));
        assertThatThrownBy(() -> signer.sign(claims())).hasMessageContaining(message);
    }

    private static RiskAuthoritySigningPort.RiskAuthoritySignResponse validResponse(
            KeyPair pair, RiskAuthoritySigningPort.RiskAuthoritySignRequest request) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(pair.getPrivate());
            signature.update(SkillTaskApprovalRefCodec.claimsMessage(
                    KEY_ID, request.claimsPayload()).getBytes(StandardCharsets.UTF_8));
            return new RiskAuthoritySigningPort.RiskAuthoritySignResponse(
                    request.requestId(), request.permitId(), KEY_ID,
                    RemoteRiskAuthorityExecutionPermitSigner.ALGORITHM,
                    Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign()));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static SkillTaskApprovalSigningProperties properties(KeyPair pair) {
        SkillTaskApprovalSigningProperties properties = new SkillTaskApprovalSigningProperties();
        properties.setAuthorityMode("REMOTE_RSA");
        properties.setIssueVersion("cma3");
        properties.setLegacyHmacEnabled(false);
        properties.getRemote().setExpectedKeyId(KEY_ID);
        properties.getRemote().getVerificationKeys().put(KEY_ID,
                new SkillTaskApprovalSigningProperties.RemoteVerificationKey()
                        .setPublicKeyPem(publicKeyPem(pair))
                        .setNotBefore(NOW.minusSeconds(60))
                        .setExpiresAt(NOW.plusSeconds(600)));
        return properties;
    }

    private static RemoteRiskAuthorityExecutionPermitSigner signer(
            SkillTaskApprovalSigningProperties properties, RiskAuthoritySigningPort port) {
        return new RemoteRiskAuthorityExecutionPermitSigner(
                properties, port, Clock.fixed(NOW, java.time.ZoneOffset.UTC));
    }

    private static SkillTaskApprovalPermitClaims claims() {
        return new SkillTaskApprovalPermitClaims(
                SkillTaskApprovalRefCodec.CLAIMS_VERSION,
                SkillTaskApprovalRefCodec.LOCAL_HMAC_KEY_ID,
                SkillTaskApprovalRefCodec.DEFAULT_ISSUER,
                SkillTaskApprovalRefCodec.DEFAULT_AUDIENCE,
                "permit-remote-0001",
                "wo-remote-1",
                "approval-remote-1",
                "f".repeat(64),
                "skill.full-chain",
                "1.0.0",
                "c".repeat(64),
                "a".repeat(64),
                "R3",
                "2:42",
                8L,
                NOW,
                NOW,
                NOW.plusSeconds(300),
                "mission-run-7",
                41L,
                "agent-control-1",
                9L);
    }

    private static KeyPair rsaKeyPair(int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    private static String publicKeyPem(KeyPair pair) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
