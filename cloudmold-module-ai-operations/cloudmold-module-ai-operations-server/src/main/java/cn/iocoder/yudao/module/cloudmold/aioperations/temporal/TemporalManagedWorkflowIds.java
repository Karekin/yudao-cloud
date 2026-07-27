package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.core.util.StrUtil;

public final class TemporalManagedWorkflowIds {

    private TemporalManagedWorkflowIds() {
    }

    public static String scheduleWorkflowId(TemporalManagedRunRequest request) {
        String suffix = DigestUtil.sha256Hex(
                request.getSkillId() + ":" + StrUtil.blankToDefault(request.getSkillVersion(), "latest"))
                .substring(0, 12);
        return "cm.aiops.t" + request.getTenantId() + "."
                + normalize(request.getScheduleId()) + "." + suffix;
    }

    public static String scheduleRequestId(TemporalManagedRunRequest request) {
        return "req-" + normalize(request.getScheduleId()) + "-" + request.getTenantId();
    }

    public static String workOrderId(String temporalRunId) {
        return "twr-" + stableSuffix(temporalRunId);
    }

    public static String approvalId(String temporalRunId) {
        return "tap-" + stableSuffix(temporalRunId);
    }

    public static String managedRunId(String temporalRunId) {
        return "tsr-" + stableSuffix(temporalRunId);
    }

    public static String clientRequestKey(TemporalManagedRunRequest request, String temporalRunId) {
        return "temporal/" + normalize(request.getScheduleId()) + "/" + stableSuffix(temporalRunId);
    }

    public static String stableSuffix(String temporalRunId) {
        return DigestUtil.sha256Hex(temporalRunId).substring(0, 24);
    }

    private static String normalize(String value) {
        return StrUtil.blankToDefault(value, "managed-run")
                .trim().toLowerCase().replaceAll("[^a-z0-9._-]", "-");
    }
}
