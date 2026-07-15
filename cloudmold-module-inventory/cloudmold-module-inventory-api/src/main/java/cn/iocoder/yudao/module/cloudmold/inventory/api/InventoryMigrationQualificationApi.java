package cn.iocoder.yudao.module.cloudmold.inventory.api;

public interface InventoryMigrationQualificationApi {
    InventoryMigrationQualificationResult qualify(InventoryMigrationQualificationCommand command);
    InventoryMigrationOpeningResult migrate(InventoryMigrationOpeningCommand command);
    InventoryMigrationQualificationResult requireQualification(String qualificationId);
}
