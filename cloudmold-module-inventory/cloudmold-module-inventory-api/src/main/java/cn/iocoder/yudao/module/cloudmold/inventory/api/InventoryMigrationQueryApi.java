package cn.iocoder.yudao.module.cloudmold.inventory.api;

import java.util.List;

public interface InventoryMigrationQueryApi {
    InventoryMigrationAssessmentResult requireRun(String migrationRunId);
    List<InventoryMigrationCandidateView> listCandidates(String migrationRunId);
}
