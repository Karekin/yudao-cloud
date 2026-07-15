package cn.iocoder.yudao.module.cloudmold.inventory.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "cloudmold.inventory.migration")
public class InventoryMigrationPilotProperties {
    /** Trusted server-side environment. Client requests never set this value. */
    private String environment = "DISABLED";
    /** Deployment-specific immutable fingerprint included in every manifest hash. */
    private String environmentFingerprint = "";
    private boolean pilotEnabled = false;
    private boolean productionAdmissionEnabled = false;
    private int maxPilotItems = 10;
    private int maxPilotWindowSeconds = 900;
    private int maxWatermarkLagSeconds = 300;
    /** Continuous production shadow remains fail-closed until explicitly enabled. */
    private boolean shadowEnabled = false;
    private int shadowRequiredRoundCount = 12;
    private int shadowMinimumDurationSeconds = 259200;
    private int shadowMaxRoundIntervalSeconds = 21600;
    private int shadowMaxWatermarkLagSeconds = 300;
    /** Authenticated system users allowlisted to collect the independent read-only projection. */
    private List<Long> trustedShadowCollectorIds = new ArrayList<>();
    /** Authenticated system users allowlisted to close a shadow evidence window. */
    private List<Long> trustedShadowVerifierIds = new ArrayList<>();
}
