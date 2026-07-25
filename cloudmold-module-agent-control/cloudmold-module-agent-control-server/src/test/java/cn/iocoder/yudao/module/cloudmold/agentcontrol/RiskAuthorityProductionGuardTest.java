package cn.iocoder.yudao.module.cloudmold.agentcontrol;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskAuthorityProductionGuardTest {

    private static final Instant START = Instant.parse("2026-07-25T00:00:00Z");
    private static final Instant END = Instant.parse("2026-08-25T00:00:00Z");

    @Test
    void acceptsOnlyRemoteRsaProductionConfiguration() throws Exception {
        SkillTaskApprovalSigningProperties properties = productionProperties(rsaKeyPair(3072));

        assertThatCode(() -> RiskAuthorityProductionGuard.validate(properties, true))
                .doesNotThrowAnyException();
        assertThatCode(() -> RiskAuthorityProductionGuard.validate(
                new SkillTaskApprovalSigningProperties(), false)).doesNotThrowAnyException();
    }

    @Test
    void rejectsLocalSecretsPrivatePemAndMixedKeyIdentityInProduction() throws Exception {
        SkillTaskApprovalSigningProperties local = productionProperties(rsaKeyPair(3072));
        local.setAuthorityMode("LOCAL_TEST_HMAC");
        local.setHmacSecret("0123456789abcdef0123456789abcdef");
        assertThatThrownBy(() -> RiskAuthorityProductionGuard.validate(local, true))
                .hasMessageContaining("REMOTE_RSA");

        SkillTaskApprovalSigningProperties privatePem = productionProperties(rsaKeyPair(3072));
        privatePem.getAsymmetricKeys().put("local-private",
                new SkillTaskApprovalSigningProperties.AsymmetricSigningKey()
                        .setPrivateKeyPem("-----BEGIN PRIVATE KEY-----\nforbidden\n-----END PRIVATE KEY-----"));
        assertThatThrownBy(() -> RiskAuthorityProductionGuard.validate(privatePem, true))
                .hasMessageContaining("private PEM");

        SkillTaskApprovalSigningProperties mixed = productionProperties(rsaKeyPair(3072));
        mixed.setActiveKeyId("risk-epoch-7");
        assertThatThrownBy(() -> RiskAuthorityProductionGuard.validate(mixed, true))
                .hasMessageContaining("activeKeyId");
    }

    @Test
    void rejectsWeakRsaInsecureEndpointAndUnboundedTimeout() throws Exception {
        SkillTaskApprovalSigningProperties weak = productionProperties(rsaKeyPair(2048));
        assertThatThrownBy(() -> RiskAuthorityProductionGuard.validate(weak, true))
                .hasMessageContaining("3072");

        SkillTaskApprovalSigningProperties http = productionProperties(rsaKeyPair(3072));
        http.getRemote().setEndpoint("http://risk.example.test/v1/permits/sign");
        assertThatThrownBy(() -> RiskAuthorityProductionGuard.validate(http, true))
                .hasMessageContaining("HTTPS");

        SkillTaskApprovalSigningProperties slow = productionProperties(rsaKeyPair(3072));
        slow.getRemote().setRequestTimeout(java.time.Duration.ofSeconds(11));
        assertThatThrownBy(() -> RiskAuthorityProductionGuard.validate(slow, true))
                .hasMessageContaining("10 seconds");
    }

    private static SkillTaskApprovalSigningProperties productionProperties(KeyPair pair) {
        SkillTaskApprovalSigningProperties properties = new SkillTaskApprovalSigningProperties();
        properties.setAuthorityMode("REMOTE_RSA");
        properties.setIssueVersion("cma3");
        properties.setLegacyHmacEnabled(false);
        properties.setActiveKeyId("");
        properties.getRemote().setEndpoint("https://risk.example.test/v1/permits/sign");
        properties.getRemote().setBearerToken("test-token");
        properties.getRemote().setExpectedKeyId("risk-epoch-7");
        properties.getRemote().getVerificationKeys().put("risk-epoch-7",
                new SkillTaskApprovalSigningProperties.RemoteVerificationKey()
                        .setPublicKeyPem(publicKeyPem(pair))
                        .setNotBefore(START)
                        .setExpiresAt(END));
        return properties;
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
