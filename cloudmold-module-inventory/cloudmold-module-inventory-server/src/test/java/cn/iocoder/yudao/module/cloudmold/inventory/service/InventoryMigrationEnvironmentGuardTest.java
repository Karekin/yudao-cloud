package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryMigrationAssessmentCommand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class InventoryMigrationEnvironmentGuardTest {

    @Test
    void productionRejectsTenantWideAssessmentAndControlledCanaryMutation() {
        InventoryMigrationPilotProperties properties = new InventoryMigrationPilotProperties();
        properties.setEnvironment("PRODUCTION");
        properties.setPilotEnabled(true);
        InventoryMigrationEnvironmentGuard guard = new InventoryMigrationEnvironmentGuard(properties);

        assertThatThrownBy(() -> guard.requireAssessmentAllowed(new InventoryMigrationAssessmentCommand()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exact sourceBalanceId");
        assertThatThrownBy(guard::requireControlledCanaryMutationAllowed)
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("forbidden");
    }

    @Test
    void productionExactAssessmentStillRequiresServerPilotGate() {
        InventoryMigrationPilotProperties properties = new InventoryMigrationPilotProperties();
        properties.setEnvironment("PRODUCTION");
        InventoryMigrationEnvironmentGuard guard = new InventoryMigrationEnvironmentGuard(properties);
        InventoryMigrationAssessmentCommand command = new InventoryMigrationAssessmentCommand()
                .setSourceBalanceId("40000000-0000-4000-8000-000000000001");

        assertThatThrownBy(() -> guard.requireAssessmentAllowed(command))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("disabled");
    }

    @Test
    void nonProductionLeavesExistingCanaryWorkflowAvailable() {
        InventoryMigrationPilotProperties properties = new InventoryMigrationPilotProperties();
        properties.setEnvironment("LOCAL");
        InventoryMigrationEnvironmentGuard guard = new InventoryMigrationEnvironmentGuard(properties);

        assertThatCode(() -> guard.requireAssessmentAllowed(new InventoryMigrationAssessmentCommand()))
                .doesNotThrowAnyException();
        assertThatCode(guard::requireControlledCanaryMutationAllowed).doesNotThrowAnyException();
    }
}
