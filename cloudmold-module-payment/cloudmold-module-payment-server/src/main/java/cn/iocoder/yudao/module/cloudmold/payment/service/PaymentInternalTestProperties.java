package cn.iocoder.yudao.module.cloudmold.payment.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "cloudmold.payment.internal-test")
public class PaymentInternalTestProperties {
    /**
     * Trusted server-side deployment environment.
     * Allowed values for INTERNAL_TEST payments: LOCAL, LOCAL_TEST, DEMO, TEST.
     */
    private String environment = "DISABLED";
    /**
     * Explicit feature flag for INTERNAL_TEST capture/refund.
     * Production remains fail-closed even if this flag is accidentally enabled.
     */
    private boolean enabled = false;
}
