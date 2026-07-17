package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import java.util.List;

public interface LegacyTradeBenefitMigrationApi {
    LegacyTradeBenefitAssessmentResult assess(LegacyTradeBenefitAssessmentCommand command);
    LegacyTradeBenefitAssessmentResult requireRun(String migrationRunId);
    List<LegacyTradeBenefitCandidateView> listCandidates(String migrationRunId);
    List<LegacyTradeBenefitComponentView> listComponents(String migrationRunId);
    List<LegacyTradeBenefitItemView> listItems(String migrationRunId);
    List<LegacyTradeBenefitComponentReconciliationView> listComponentReconciliations(String migrationRunId);
}
