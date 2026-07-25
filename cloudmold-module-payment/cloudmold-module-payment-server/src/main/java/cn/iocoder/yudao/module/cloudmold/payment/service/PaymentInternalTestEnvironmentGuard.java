package cn.iocoder.yudao.module.cloudmold.payment.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class PaymentInternalTestEnvironmentGuard {

    private static final Set<String> ALLOWED_ENVIRONMENTS = Set.of("LOCAL", "LOCAL_TEST", "DEMO", "TEST");
    private static final String TEST_PROVIDER = "INTERNAL_TEST";

    private final PaymentInternalTestProperties properties;

    public PaymentInternalTestEnvironmentGuard(PaymentInternalTestProperties properties) {
        this.properties = properties;
    }

    public void requireInternalTestAllowed(String providerCode) {
        if (!TEST_PROVIDER.equals(providerCode)) {
            return;
        }
        String environment = normalizedEnvironment();
        require(!"PRODUCTION".equals(environment),
                "INTERNAL_TEST payment provider is forbidden in PRODUCTION");
        require(properties.isEnabled(),
                "INTERNAL_TEST payment provider is disabled in current environment");
        require(ALLOWED_ENVIRONMENTS.contains(environment),
                "INTERNAL_TEST payment provider is allowed only in LOCAL, LOCAL_TEST, DEMO, or TEST");
    }

    private String normalizedEnvironment() {
        return properties.getEnvironment() == null ? ""
                : properties.getEnvironment().trim().toUpperCase(Locale.ROOT);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
