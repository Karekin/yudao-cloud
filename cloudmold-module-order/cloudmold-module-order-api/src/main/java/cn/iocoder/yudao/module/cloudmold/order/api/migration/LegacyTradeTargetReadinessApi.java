package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import java.util.List;

public interface LegacyTradeTargetReadinessApi {
    LegacyTradeTargetReadinessResult assess(LegacyTradeTargetReadinessCommand command);
    LegacyTradeTargetReadinessResult requireRun(String targetReadinessRunId);
    List<LegacyTradeTargetReadinessOrderView> listOrders(String targetReadinessRunId);
    List<LegacyTradeTargetReadinessItemView> listItems(String targetReadinessRunId);
}
