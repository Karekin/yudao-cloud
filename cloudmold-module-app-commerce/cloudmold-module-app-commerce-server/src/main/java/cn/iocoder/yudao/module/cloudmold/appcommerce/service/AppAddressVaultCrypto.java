package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

@Component
public class AppAddressVaultCrypto {
    static final String DEFAULT_SANDBOX_KEY_ID = "sandbox-local-v1";
    static final String DEFAULT_SANDBOX_KEY_BASE64 =
            "Y2xvdWRtb2xkLWxvY2FsLWFkZHJlc3Mta2V5LXYxISE=";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final String MODE_DISABLED = "DISABLED";
    private static final String MODE_SANDBOX = "SANDBOX";
    private static final String MODE_ENVELOPE_HTTP = "ENVELOPE_HTTP";
    private static final String DEFAULT_ENCRYPT_PATH = "/internal/address-vault/encrypt";
    private static final String DEFAULT_DECRYPT_PATH = "/internal/address-vault/decrypt";
    private static final String DEFAULT_FINGERPRINT_PATH = "/internal/address-vault/fingerprint";
    private static final String DEFAULT_ENVELOPE_KEY_VERSION = "address-delivery-v1";
    private static final Set<String> PRODUCTION_PROFILE_ALIASES =
            Set.of("prd", "aliyun-prod");

    private final VaultProvider provider;

    @Autowired
    public AppAddressVaultCrypto(
            Environment environment,
            RestTemplate restTemplate,
            @Value("${cloudmold.app-commerce.address-vault-mode:DISABLED}") String mode,
            @Value("${cloudmold.app-commerce.address-vault-environment-class:}") String environmentClass,
            @Value("${cloudmold.app-commerce.address-vault-sandbox-key-id:sandbox-local-v1}") String sandboxKeyId,
            @Value("${cloudmold.app-commerce.address-vault-sandbox-key-base64:}") String sandboxKeyBase64,
            @Value("${cloudmold.app-commerce.address-vault-sandbox-additional-keys:}") String sandboxAdditionalKeys,
            @Value("${cloudmold.app-commerce.address-vault-sandbox-revoked-key-ids:}") String sandboxRevokedKeyIds,
            @Value("${cloudmold.app-commerce.address-vault-envelope-base-url:}") String envelopeBaseUrl,
            @Value("${cloudmold.app-commerce.address-vault-envelope-encrypt-path:/internal/address-vault/encrypt}") String envelopeEncryptPath,
            @Value("${cloudmold.app-commerce.address-vault-envelope-decrypt-path:/internal/address-vault/decrypt}") String envelopeDecryptPath,
            @Value("${cloudmold.app-commerce.address-vault-envelope-fingerprint-path:/internal/address-vault/fingerprint}") String envelopeFingerprintPath,
            @Value("${cloudmold.app-commerce.address-vault-envelope-auth-header-name:}") String envelopeAuthHeaderName,
            @Value("${cloudmold.app-commerce.address-vault-envelope-auth-header-value:}") String envelopeAuthHeaderValue,
            @Value("${cloudmold.app-commerce.address-vault-envelope-key-version:address-delivery-v1}") String envelopeKeyVersion) {
        this(environment, (RestOperations) restTemplate, mode, environmentClass, sandboxKeyId, sandboxKeyBase64,
                sandboxAdditionalKeys, sandboxRevokedKeyIds, envelopeBaseUrl, envelopeEncryptPath,
                envelopeDecryptPath, envelopeFingerprintPath, envelopeAuthHeaderName,
                envelopeAuthHeaderValue, envelopeKeyVersion);
    }

    AppAddressVaultCrypto(Environment environment, String mode, String sandboxKeyBase64) {
        this(environment, null, mode, "", DEFAULT_SANDBOX_KEY_ID, sandboxKeyBase64, "", "", "",
                DEFAULT_ENCRYPT_PATH, DEFAULT_DECRYPT_PATH, DEFAULT_FINGERPRINT_PATH, "", "",
                DEFAULT_ENVELOPE_KEY_VERSION);
    }

    AppAddressVaultCrypto(Environment environment,
                          RestOperations restOperations,
                          String mode,
                          String environmentClass,
                          String sandboxKeyId,
                          String sandboxKeyBase64,
                          String sandboxAdditionalKeys,
                          String sandboxRevokedKeyIds,
                          String envelopeBaseUrl,
                          String envelopeEncryptPath,
                          String envelopeDecryptPath,
                          String envelopeFingerprintPath,
                          String envelopeAuthHeaderName,
                          String envelopeAuthHeaderValue,
                          String envelopeKeyVersion) {
        RuntimeClass runtimeClass = resolveRuntimeClass(environment, environmentClass);
        String normalizedMode = normalizeMode(mode);
        this.provider = switch (normalizedMode) {
            case MODE_DISABLED -> {
                require(runtimeClass != RuntimeClass.PRODUCTION,
                        "production Address Vault must use ENVELOPE_HTTP");
                yield new DisabledVaultProvider();
            }
            case MODE_SANDBOX -> {
                require(runtimeClass != RuntimeClass.PRODUCTION,
                        "production Address Vault must use ENVELOPE_HTTP");
                yield new SandboxVaultProvider(runtimeClass, sandboxKeyId, sandboxKeyBase64,
                        sandboxAdditionalKeys, sandboxRevokedKeyIds);
            }
            case MODE_ENVELOPE_HTTP -> new EnvelopeHttpVaultProvider(restOperations,
                    envelopeBaseUrl, envelopeEncryptPath, envelopeDecryptPath,
                    envelopeFingerprintPath, envelopeAuthHeaderName, envelopeAuthHeaderValue,
                    envelopeKeyVersion);
            default -> throw new IllegalStateException("Unsupported Address Vault mode");
        };
    }

    Encrypted encrypt(String aad, byte[] plaintext) {
        return provider.encrypt(aad, plaintext);
    }

    byte[] decrypt(String aad, String keyId, byte[] iv, byte[] ciphertext) {
        return provider.decrypt(aad, keyId, iv, ciphertext);
    }

    String fingerprint(byte[] plaintext) {
        return provider.fingerprint(plaintext);
    }

    private static String normalizeMode(String value) {
        String normalized = value == null ? MODE_DISABLED : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of(MODE_DISABLED, MODE_SANDBOX, MODE_ENVELOPE_HTTP).contains(normalized)) {
            throw new IllegalStateException("Address Vault mode must be DISABLED, SANDBOX, or ENVELOPE_HTTP");
        }
        return normalized;
    }

    private static RuntimeClass resolveRuntimeClass(Environment environment, String configuredClass) {
        Set<String> profiles = new LinkedHashSet<>();
        for (String profile : environment.getActiveProfiles()) {
            if (profile != null && !profile.isBlank()) {
                profiles.add(profile.trim().toLowerCase(Locale.ROOT));
            }
        }
        for (String alias : PRODUCTION_PROFILE_ALIASES) {
            if (profiles.contains(alias)) {
                throw new IllegalStateException(
                        "Address Vault environment-class must use 'production'; aliases prd and aliyun-prod are not allowed");
            }
        }
        RuntimeClass runtimeClass = configuredClass == null || configuredClass.isBlank()
                ? inferRuntimeClass(profiles)
                : parseRuntimeClass(configuredClass);
        if ((profiles.contains("prod") || profiles.contains("production"))
                && runtimeClass != RuntimeClass.PRODUCTION) {
            throw new IllegalStateException(
                    "Address Vault environment-class must be production when a production profile is active");
        }
        if (runtimeClass == RuntimeClass.PRODUCTION
                && (profiles.contains("local") || profiles.contains("test") || profiles.contains("dev"))) {
            throw new IllegalStateException(
                    "Address Vault production environment-class conflicts with local/test/dev profiles");
        }
        return runtimeClass;
    }

    private static RuntimeClass inferRuntimeClass(Set<String> profiles) {
        if (profiles.contains("prod") || profiles.contains("production")) {
            return RuntimeClass.PRODUCTION;
        }
        if (profiles.contains("test")) {
            return RuntimeClass.TEST;
        }
        if (profiles.contains("dev")) {
            return RuntimeClass.DEV;
        }
        return RuntimeClass.LOCAL;
    }

    private static RuntimeClass parseRuntimeClass(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "local" -> RuntimeClass.LOCAL;
            case "test" -> RuntimeClass.TEST;
            case "dev" -> RuntimeClass.DEV;
            case "production" -> RuntimeClass.PRODUCTION;
            default -> throw new IllegalStateException(
                    "Address Vault environment-class must be one of local, test, dev, production");
        };
    }

    private static byte[] decodeBase64(String value, String message) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (Exception error) {
            throw new IllegalStateException(message, error);
        }
    }

    private static byte[] require32ByteKey(byte[] value, String message) {
        if (value.length != 32) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private static String normalizeUrl(String baseUrl, String path) {
        require(baseUrl != null && !baseUrl.isBlank(),
                "Address Vault ENVELOPE_HTTP requires envelope-base-url");
        require(path != null && !path.isBlank(),
                "Address Vault ENVELOPE_HTTP requires endpoint paths");
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
    }

    private static List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private interface VaultProvider {
        Encrypted encrypt(String aad, byte[] plaintext);

        byte[] decrypt(String aad, String keyId, byte[] iv, byte[] ciphertext);

        String fingerprint(byte[] plaintext);
    }

    private static final class DisabledVaultProvider implements VaultProvider {
        @Override
        public Encrypted encrypt(String aad, byte[] plaintext) {
            throw unavailable();
        }

        @Override
        public byte[] decrypt(String aad, String keyId, byte[] iv, byte[] ciphertext) {
            throw unavailable();
        }

        @Override
        public String fingerprint(byte[] plaintext) {
            throw unavailable();
        }

        private static IllegalStateException unavailable() {
            return new IllegalStateException("Address Vault is unavailable in this environment");
        }
    }

    private static final class SandboxVaultProvider implements VaultProvider {
        private static final String PURPOSE_AES = "app-address-vault:aes";
        private static final String PURPOSE_FINGERPRINT = "app-address-vault:fingerprint";

        private final String activeKeyId;
        private final Map<String, byte[]> keys;
        private final Set<String> revokedKeyIds;
        private final SecureRandom secureRandom = new SecureRandom();

        private SandboxVaultProvider(RuntimeClass runtimeClass,
                                     String activeKeyId,
                                     String activeKeyBase64,
                                     String additionalKeys,
                                     String revokedKeyIds) {
            require(activeKeyId != null && !activeKeyId.isBlank(),
                    "Address Vault SANDBOX requires sandbox-key-id");
            Map<String, byte[]> parsedKeys = new LinkedHashMap<>();
            parsedKeys.put(activeKeyId.trim(), decodeSandboxKey(runtimeClass, activeKeyBase64));
            for (String entry : parseCsv(additionalKeys)) {
                int separator = entry.indexOf('=');
                if (separator <= 0 || separator == entry.length() - 1) {
                    throw new IllegalStateException(
                            "Address Vault SANDBOX additional keys must use keyId=base64 syntax");
                }
                String keyId = entry.substring(0, separator).trim();
                String keyBase64 = entry.substring(separator + 1).trim();
                require(!parsedKeys.containsKey(keyId),
                        "Address Vault SANDBOX key ids must be unique");
                parsedKeys.put(keyId, decodeSandboxKey(runtimeClass, keyBase64));
            }
            Set<String> revoked = new LinkedHashSet<>(parseCsv(revokedKeyIds));
            require(!revoked.contains(activeKeyId.trim()),
                    "Address Vault SANDBOX active key cannot be revoked");
            this.activeKeyId = activeKeyId.trim();
            this.keys = Collections.unmodifiableMap(parsedKeys);
            this.revokedKeyIds = Collections.unmodifiableSet(revoked);
        }

        @Override
        public Encrypted encrypt(String aad, byte[] plaintext) {
            try {
                byte[] iv = new byte[IV_BYTES];
                secureRandom.nextBytes(iv);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE,
                        new SecretKeySpec(deriveKey(activeKeyId, PURPOSE_AES), "AES"),
                        new GCMParameterSpec(GCM_TAG_BITS, iv));
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
                return new Encrypted(activeKeyId, iv, cipher.doFinal(plaintext));
            } catch (Exception error) {
                throw new IllegalStateException("Address Vault encryption failed", error);
            }
        }

        @Override
        public byte[] decrypt(String aad, String keyId, byte[] iv, byte[] ciphertext) {
            byte[] key = keys.get(keyId);
            if (key == null || revokedKeyIds.contains(keyId)) {
                throw new IllegalArgumentException("Address Vault key is unavailable");
            }
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(deriveKey(key, PURPOSE_AES), "AES"),
                        new GCMParameterSpec(GCM_TAG_BITS, iv));
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
                return cipher.doFinal(ciphertext);
            } catch (Exception error) {
                throw new IllegalArgumentException("Address Vault ciphertext verification failed");
            }
        }

        @Override
        public String fingerprint(byte[] plaintext) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(deriveKey(activeKeyId, PURPOSE_FINGERPRINT), "HmacSHA256"));
                return HexFormat.of().formatHex(mac.doFinal(plaintext));
            } catch (Exception error) {
                throw new IllegalStateException("Address Vault fingerprint failed", error);
            }
        }

        private byte[] deriveKey(String keyId, String purpose) throws Exception {
            return deriveKey(Objects.requireNonNull(keys.get(keyId)), purpose);
        }

        private static byte[] deriveKey(byte[] rootKey, String purpose) throws Exception {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(rootKey, "HmacSHA256"));
            return mac.doFinal(purpose.getBytes(StandardCharsets.UTF_8));
        }

        private static byte[] decodeSandboxKey(RuntimeClass runtimeClass, String value) {
            require(value != null && !value.isBlank(),
                    "Address Vault SANDBOX requires a 32-byte Base64 key");
            byte[] decoded = require32ByteKey(
                    decodeBase64(value, "Address Vault SANDBOX requires a 32-byte Base64 key"),
                    "Address Vault SANDBOX requires a 32-byte Base64 key");
            if (DEFAULT_SANDBOX_KEY_BASE64.equals(value.trim())
                    && runtimeClass != RuntimeClass.LOCAL
                    && runtimeClass != RuntimeClass.TEST) {
                throw new IllegalStateException(
                        "Address Vault SANDBOX default key is only allowed in local/test");
            }
            return decoded;
        }
    }

    private static final class EnvelopeHttpVaultProvider implements VaultProvider {
        private final RestOperations restOperations;
        private final String encryptUrl;
        private final String decryptUrl;
        private final String fingerprintUrl;
        private final HttpHeaders headers;
        private final String keyVersion;

        private EnvelopeHttpVaultProvider(RestOperations restOperations,
                                          String baseUrl,
                                          String encryptPath,
                                          String decryptPath,
                                          String fingerprintPath,
                                          String authHeaderName,
                                          String authHeaderValue,
                                          String keyVersion) {
            require(restOperations != null, "Address Vault ENVELOPE_HTTP requires RestTemplate");
            require(authHeaderName != null && !authHeaderName.isBlank()
                            && authHeaderValue != null && !authHeaderValue.isBlank(),
                    "Address Vault ENVELOPE_HTTP requires auth header name and value");
            require(keyVersion != null && !keyVersion.isBlank(),
                    "Address Vault ENVELOPE_HTTP requires envelope-key-version");
            this.restOperations = restOperations;
            this.encryptUrl = normalizeUrl(baseUrl,
                    encryptPath == null || encryptPath.isBlank() ? DEFAULT_ENCRYPT_PATH : encryptPath);
            this.decryptUrl = normalizeUrl(baseUrl,
                    decryptPath == null || decryptPath.isBlank() ? DEFAULT_DECRYPT_PATH : decryptPath);
            this.fingerprintUrl = normalizeUrl(baseUrl,
                    fingerprintPath == null || fingerprintPath.isBlank() ? DEFAULT_FINGERPRINT_PATH : fingerprintPath);
            this.keyVersion = keyVersion.trim();
            HttpHeaders value = new HttpHeaders();
            value.setContentType(MediaType.APPLICATION_JSON);
            value.setAccept(List.of(MediaType.APPLICATION_JSON));
            value.set(authHeaderName.trim(), authHeaderValue.trim());
            this.headers = value;
        }

        @Override
        public Encrypted encrypt(String aad, byte[] plaintext) {
            EncryptResponse response = restOperations.postForObject(
                    encryptUrl,
                    new HttpEntity<>(new EncryptRequest(
                            Base64.getEncoder().encodeToString(aad.getBytes(StandardCharsets.UTF_8)),
                            Base64.getEncoder().encodeToString(plaintext),
                            keyVersion,
                            "DELIVERY_ADDRESS"), headers),
                    EncryptResponse.class);
            if (response == null
                    || response.keyId() == null
                    || response.initializationVectorBase64() == null
                    || response.ciphertextBase64() == null) {
                throw new IllegalStateException("Address Vault encryption failed");
            }
            return new Encrypted(response.keyId(),
                    decodeBase64(response.initializationVectorBase64(), "Address Vault encryption failed"),
                    decodeBase64(response.ciphertextBase64(), "Address Vault encryption failed"));
        }

        @Override
        public byte[] decrypt(String aad, String keyId, byte[] iv, byte[] ciphertext) {
            DecryptResponse response = restOperations.postForObject(
                    decryptUrl,
                    new HttpEntity<>(new DecryptRequest(
                            Base64.getEncoder().encodeToString(aad.getBytes(StandardCharsets.UTF_8)),
                            keyId,
                            Base64.getEncoder().encodeToString(iv),
                            Base64.getEncoder().encodeToString(ciphertext),
                            "DELIVERY_ADDRESS"), headers),
                    DecryptResponse.class);
            if (response == null || response.plaintextBase64() == null) {
                throw new IllegalArgumentException("Address Vault ciphertext verification failed");
            }
            if ("REVOKED".equalsIgnoreCase(response.keyState())) {
                throw new IllegalArgumentException("Address Vault key is unavailable");
            }
            return decodeBase64(response.plaintextBase64(),
                    "Address Vault ciphertext verification failed");
        }

        @Override
        public String fingerprint(byte[] plaintext) {
            FingerprintResponse response = restOperations.postForObject(
                    fingerprintUrl,
                    new HttpEntity<>(new FingerprintRequest(
                            Base64.getEncoder().encodeToString(plaintext),
                            keyVersion,
                            "DELIVERY_ADDRESS_FINGERPRINT"), headers),
                    FingerprintResponse.class);
            if (response == null || response.digestHex() == null || !response.digestHex().matches("^[0-9a-f]{64}$")) {
                throw new IllegalStateException("Address Vault fingerprint failed");
            }
            return response.digestHex();
        }
    }

    record Encrypted(String keyId, byte[] initializationVector, byte[] ciphertext) {
    }

    record EncryptRequest(String aadBase64,
                          String plaintextBase64,
                          String keyVersion,
                          String purpose) {
    }

    record EncryptResponse(String keyId,
                           String initializationVectorBase64,
                           String ciphertextBase64) {
    }

    record DecryptRequest(String aadBase64,
                          String keyId,
                          String initializationVectorBase64,
                          String ciphertextBase64,
                          String purpose) {
    }

    record DecryptResponse(String plaintextBase64, String keyState) {
    }

    record FingerprintRequest(String plaintextBase64, String keyVersion, String purpose) {
    }

    record FingerprintResponse(String digestHex, String keyVersion) {
    }

    private enum RuntimeClass {
        LOCAL,
        TEST,
        DEV,
        PRODUCTION
    }
}
