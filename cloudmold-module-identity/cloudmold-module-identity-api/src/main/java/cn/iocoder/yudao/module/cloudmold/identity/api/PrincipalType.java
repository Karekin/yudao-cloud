package cn.iocoder.yudao.module.cloudmold.identity.api;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Versioned canonical Principal vocabulary shared by Identity providers and consumers.
 */
public enum PrincipalType {

    PLATFORM_OPERATOR,
    MEMBER,
    MERCHANT_OPERATOR,
    WAREHOUSE_OPERATOR;

    private static final Set<String> CONTRACT_CODES = Arrays.stream(values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    public static boolean isSupported(String value) {
        return CONTRACT_CODES.contains(value);
    }

    public static Set<String> contractCodes() {
        return CONTRACT_CODES;
    }
}
