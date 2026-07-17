package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import java.util.List;

public interface LegacyTradeBenefitGovernanceApi {
    LegacyTradeBenefitGovernanceResult assess(LegacyTradeBenefitGovernanceCommand command);
    LegacyTradeBenefitGovernanceResult requireRun(String governanceRunId);
    List<LegacyTradeBenefitGovernanceComponentView> listComponents(String governanceRunId);
    List<LegacyTradeBenefitGovernanceQuarantineView> listQuarantines(String governanceRunId);
}
