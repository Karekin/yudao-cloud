package cn.iocoder.yudao.module.cloudmold.skilltask.api.approval;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class SkillTaskPemKeys {

    private SkillTaskPemKeys() {
    }

    public static PublicKey rsaPublicKey(String pem) {
        try {
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(parsePem(pem, "PUBLIC KEY")));
        } catch (Exception ex) {
            throw new IllegalStateException("Skill Task approval publicKeyPem is invalid", ex);
        }
    }

    private static byte[] parsePem(String pem, String type) {
        String value = requireText(pem, type);
        value = value.replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s+", "");
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Skill Task approval " + type + " PEM is invalid", ex);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Skill Task approval " + field + " PEM is required");
        }
        return value.trim();
    }
}
