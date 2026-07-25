package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestOperations;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AppAddressVaultCryptoTest {

    private static final String LOCAL_KEY = AppAddressVaultCrypto.DEFAULT_SANDBOX_KEY_BASE64;
    private static final String ROTATED_KEY =
            "cm90YXRlZC1hZGRyZXNzLWtleS12Mi1jbG91ZG1vbGQ=";

    @Test
    void shouldRoundTripOnlyWithMatchingAuthenticatedContext() {
        AppAddressVaultCrypto crypto = new AppAddressVaultCrypto(
                new MockEnvironment().withProperty("spring.profiles.active", "test"),
                "SANDBOX", LOCAL_KEY);
        byte[] plaintext = "receiver-private-data".getBytes(StandardCharsets.UTF_8);

        AppAddressVaultCrypto.Encrypted encrypted = crypto.encrypt("tenant|owner|ref|1", plaintext);

        assertThat(crypto.decrypt("tenant|owner|ref|1", encrypted.keyId(),
                encrypted.initializationVector(), encrypted.ciphertext())).isEqualTo(plaintext);
        assertThat(crypto.fingerprint(plaintext))
                .hasSize(64)
                .isEqualTo(crypto.fingerprint(plaintext));
        assertThatThrownBy(() -> crypto.decrypt("tenant|other-owner|ref|1", encrypted.keyId(),
                encrypted.initializationVector(), encrypted.ciphertext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Address Vault ciphertext verification failed");
    }

    @Test
    void shouldFailClosedWhenDisabled() {
        AppAddressVaultCrypto crypto =
                new AppAddressVaultCrypto(new MockEnvironment(), "DISABLED", "");

        assertThatThrownBy(() -> crypto.encrypt("aad", new byte[]{1}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Address Vault is unavailable in this environment");
    }

    @Test
    void shouldRejectDefaultSandboxKeyOutsideLocalOrTest() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        assertThatThrownBy(() -> new AppAddressVaultCrypto(environment, "SANDBOX", LOCAL_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Address Vault SANDBOX default key is only allowed in local/test");
    }

    @Test
    void shouldRejectSandboxModeInProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new AppAddressVaultCrypto(environment, "SANDBOX", LOCAL_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("production Address Vault must use ENVELOPE_HTTP");
    }

    @Test
    void shouldRejectProductionProfileAliases() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("aliyun-prod");

        assertThatThrownBy(() -> new AppAddressVaultCrypto(environment, "DISABLED", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Address Vault environment-class must use 'production'; aliases prd and aliyun-prod are not allowed");
    }

    @Test
    void shouldSupportRotationAndRejectRevokedKeys() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        AppAddressVaultCrypto oldCrypto = new AppAddressVaultCrypto(environment, null,
                "SANDBOX", "test", "sandbox-local-v1", LOCAL_KEY, "", "",
                "", "", "", "", "", "", "");
        AppAddressVaultCrypto newCrypto = new AppAddressVaultCrypto(environment, null,
                "SANDBOX", "test", "sandbox-local-v2", ROTATED_KEY,
                "sandbox-local-v1=" + LOCAL_KEY,
                "sandbox-local-v1", "", "", "", "", "", "", "");
        byte[] plaintext = "receiver-private-data".getBytes(StandardCharsets.UTF_8);
        AppAddressVaultCrypto.Encrypted rotated = newCrypto.encrypt("tenant|owner|ref|1", plaintext);
        AppAddressVaultCrypto.Encrypted old = oldCrypto.encrypt("tenant|owner|ref|1", plaintext);

        assertThat(rotated.keyId()).isEqualTo("sandbox-local-v2");
        assertThat(newCrypto.decrypt("tenant|owner|ref|1", rotated.keyId(),
                rotated.initializationVector(), rotated.ciphertext())).isEqualTo(plaintext);
        assertThatThrownBy(() -> newCrypto.decrypt("tenant|owner|ref|1", old.keyId(),
                old.initializationVector(), old.ciphertext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Address Vault key is unavailable");
    }

    @Test
    void shouldUseEnvelopeHttpProviderInProduction() {
        RestOperations restOperations = mock(RestOperations.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        String aad = "tenant|owner|ref|1";
        byte[] plaintext = "receiver-private-data".getBytes(StandardCharsets.UTF_8);
        when(restOperations.postForObject(eq("https://vault.internal/internal/address-vault/fingerprint"),
                any(HttpEntity.class), eq(AppAddressVaultCrypto.FingerprintResponse.class)))
                .thenReturn(new AppAddressVaultCrypto.FingerprintResponse(
                        "3d6a596f1e695f0d2f5354d35f15b2337d6c00f9b4f2d6f6b45a5a31e5ce41a8",
                        "address-delivery-v3"));
        when(restOperations.postForObject(eq("https://vault.internal/internal/address-vault/encrypt"),
                any(HttpEntity.class), eq(AppAddressVaultCrypto.EncryptResponse.class)))
                .thenReturn(new AppAddressVaultCrypto.EncryptResponse(
                        "kms-address-delivery-v3",
                        Base64.getEncoder().encodeToString("123456789012".getBytes(StandardCharsets.UTF_8)),
                        Base64.getEncoder().encodeToString("ciphertext-body".getBytes(StandardCharsets.UTF_8))));
        when(restOperations.postForObject(eq("https://vault.internal/internal/address-vault/decrypt"),
                any(HttpEntity.class), eq(AppAddressVaultCrypto.DecryptResponse.class)))
                .thenReturn(new AppAddressVaultCrypto.DecryptResponse(
                        Base64.getEncoder().encodeToString(plaintext), "ACTIVE"));

        AppAddressVaultCrypto crypto = new AppAddressVaultCrypto(environment, restOperations,
                "ENVELOPE_HTTP", "production", "", "", "", "",
                "https://vault.internal", "", "", "", "X-Vault-Token", "secret-token",
                "address-delivery-v3");

        assertThat(crypto.fingerprint(plaintext))
                .isEqualTo("3d6a596f1e695f0d2f5354d35f15b2337d6c00f9b4f2d6f6b45a5a31e5ce41a8");
        AppAddressVaultCrypto.Encrypted encrypted = crypto.encrypt(aad, plaintext);
        assertThat(encrypted.keyId()).isEqualTo("kms-address-delivery-v3");
        assertThat(crypto.decrypt(aad, encrypted.keyId(), encrypted.initializationVector(),
                encrypted.ciphertext())).isEqualTo(plaintext);
    }

    @Test
    void shouldFailClosedWhenEnvelopeDecryptResponseIsMalformed() {
        RestOperations restOperations = mock(RestOperations.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        when(restOperations.postForObject(eq("https://vault.internal/internal/address-vault/decrypt"),
                any(HttpEntity.class), eq(AppAddressVaultCrypto.DecryptResponse.class)))
                .thenReturn(new AppAddressVaultCrypto.DecryptResponse(null, "ACTIVE"));

        AppAddressVaultCrypto crypto = new AppAddressVaultCrypto(environment, restOperations,
                "ENVELOPE_HTTP", "production", "", "", "", "",
                "https://vault.internal", "", "", "", "X-Vault-Token", "secret-token",
                "address-delivery-v3");

        assertThatThrownBy(() -> crypto.decrypt("aad", "kms-address-delivery-v3",
                "123456789012".getBytes(StandardCharsets.UTF_8),
                "ciphertext-body".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Address Vault ciphertext verification failed");
    }
}
