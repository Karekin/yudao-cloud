package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryMigrationAssessmentCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Trusted server-side environment boundary for every externally reachable migration mutation. */
@Component
@RequiredArgsConstructor
public class InventoryMigrationEnvironmentGuard {

    private final InventoryMigrationPilotProperties properties;

    public void requireAssessmentAllowed(InventoryMigrationAssessmentCommand command) {
        if (!isProduction()) return;
        require(properties.isPilotEnabled(), "Inventory migration pilot is disabled in PRODUCTION");
        require(command != null && command.getSourceBalanceId() != null && !command.getSourceBalanceId().isBlank(),
                "PRODUCTION assessment requires one exact sourceBalanceId; tenant-wide assessment is forbidden");
    }

    public void requireControlledCanaryMutationAllowed() {
        require(!isProduction(),
                "controlled canary qualification and opening are forbidden in the PRODUCTION environment");
    }

    private boolean isProduction() {
        return "PRODUCTION".equals(properties.getEnvironment() == null ? null
                : properties.getEnvironment().trim().toUpperCase(Locale.ROOT));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
