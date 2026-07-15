package cn.iocoder.yudao.module.cloudmold.inventory.api;

public interface InventoryMigrationAssessmentApi {
    InventoryMigrationAssessmentResult assessV1(InventoryMigrationAssessmentCommand command);
}
