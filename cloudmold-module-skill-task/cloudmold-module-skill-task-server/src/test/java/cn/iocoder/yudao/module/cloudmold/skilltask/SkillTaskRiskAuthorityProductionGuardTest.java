package cn.iocoder.yudao.module.cloudmold.skilltask;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillTaskRiskAuthorityProductionGuardTest {

    private static final Instant START = Instant.parse("2026-07-25T00:00:00Z");
    private static final Instant END = Instant.parse("2026-08-25T00:00:00Z");

    @Test
    void acceptsPublicOnlyRemoteRsaProductionTrust() throws Exception {
        SkillTaskProperties properties = productionProperties(rsaKeyPair(3072));

        assertThatCode(() -> SkillTaskRiskAuthorityProductionGuard.validate(properties, true))
                .doesNotThrowAnyException();
        assertThatCode(() -> SkillTaskRiskAuthorityProductionGuard.validate(
                new SkillTaskProperties(), false)).doesNotThrowAnyException();
    }

    @Test
    void rejectsLocalHmacWeakRsaAndMixedAuthorityMaterial() throws Exception {
        SkillTaskProperties local = productionProperties(rsaKeyPair(3072));
        local.getApproval().setAuthorityMode("LOCAL_TEST_HMAC");
        assertThatThrownBy(() -> SkillTaskRiskAuthorityProductionGuard.validate(local, true))
                .hasMessageContaining("REMOTE_RSA");

        SkillTaskProperties weak = productionProperties(rsaKeyPair(2048));
        assertThatThrownBy(() -> SkillTaskRiskAuthorityProductionGuard.validate(weak, true))
                .hasMessageContaining("3072");

        SkillTaskProperties mixed = productionProperties(rsaKeyPair(3072));
        mixed.getApproval().setHmacSecret("0123456789abcdef0123456789abcdef");
        assertThatThrownBy(() -> SkillTaskRiskAuthorityProductionGuard.validate(mixed, true))
                .hasMessageContaining("local HMAC");
    }

    private static SkillTaskProperties productionProperties(KeyPair pair) {
        SkillTaskProperties properties = new SkillTaskProperties();
        properties.getApproval().setAuthorityMode("REMOTE_RSA");
        properties.getApproval().setLegacyHmacEnabled(false);
        properties.getApproval().getAsymmetricKeys().put("risk-epoch-7",
                new SkillTaskProperties.AsymmetricVerificationKey()
                        .setAlgorithm("RS256")
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
