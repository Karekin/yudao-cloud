package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import java.util.List;

public interface LegacyTradeProductIdentityApi {

    LegacyTradeProductIdentityResult assess(LegacyTradeProductIdentityCommand command);

    LegacyTradeProductIdentityResult requireRun(String identityRunId);

    List<LegacyTradeProductIdentityItemView> listItems(String identityRunId);
}
