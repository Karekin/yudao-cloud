package cn.iocoder.yudao.module.cloudmold.order.api.cancellation;

import java.util.Set;

public enum OrderCancellationResponsibilityParty {
    MERCHANT,
    BUYER,
    PLATFORM,
    CARRIER;

    private static final Set<String> NAMES = Set.of(
            MERCHANT.name(),
            BUYER.name(),
            PLATFORM.name(),
            CARRIER.name()
    );

    public static boolean isSupported(String value) {
        return value != null && NAMES.contains(value);
    }
}
