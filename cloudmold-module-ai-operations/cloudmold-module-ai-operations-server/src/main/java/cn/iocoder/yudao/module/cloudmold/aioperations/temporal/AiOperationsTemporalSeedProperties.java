package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "cloudmold.ai-operations.temporal.seed")
public class AiOperationsTemporalSeedProperties {

    /**
     * 是否在启动时按当前配置自动创建 Temporal 定时工作流。
     */
    private boolean enabled = false;

    /**
     * 全量托管定义对账周期；启动时也会立即执行一次。
     */
    private long reconcileIntervalMs = 600_000L;

    /**
     * 需要补齐/创建定时流的租户列表。
     */
    private List<String> tenantIdsRaw = new ArrayList<>();

    /**
     * 系统用户（后台系统管理员）编号。
     */
    private long operatorUserId = 1L;

    /**
     * 系统用户类型。
     */
    private int operatorUserType = 1;

    /**
     * 测试环境用于消费者旅程的有效会员用户编号。0 表示不生成消费者场景。
     */
    private long syntheticConsumerMemberUserId = 0L;

    /**
     * 定时任务 ID（会拼接为 cloudmold-t{tenantId}-{normalizedId}）。
     */
    private String scheduleId = "auto-shelf-hourly";

    /**
     * 中文名称。
     */
    private String displayName = "自动铺品（每小时）";

    /**
     * 中文描述。
     */
    private String description = "基于 Temporal 的每小时自动铺品执行";

    /**
     * 自动铺品工作流定义。
     */
    private String skillId = "skill.cloudmold.commerce.product-to-listing.v1";

    /**
     * 自动铺品工作流版本。
     */
    private String skillVersion = "1.0.0";

    /**
     * 触发参数（必须是合法 JSON）。
     */
    private String inputJson = "{}";

    /**
     * 间隔秒数（默认 3600 秒）。
     */
    private long intervalSeconds = 86_400L;

    /**
     * 时间时区。
     */
    private String timeZone = "Asia/Shanghai";

    /**
     * 若定时规则需要 BPM 审批，补齐角色与动作编码；非审批可留空。
     */
    private String roleCode = "merchandising";

    /**
     * 若定时规则需要 BPM 审批，补齐角色与动作编码；非审批可留空。
     */
    private String actionCode = "catalog.publish";

    /**
     * 是否创建为暂停态。
     */
    private boolean paused = false;

    public List<Long> getTenantIds() {
        List<Long> tenantIds = new ArrayList<>();
        for (String token : tenantIdsRaw) {
            if (token == null) {
                continue;
            }
            String normalized = token.trim();
            if (normalized.isEmpty() || "[]".equals(normalized)) {
                continue;
            }
            tenantIds.add(Long.valueOf(normalized));
        }
        return tenantIds;
    }

    public void setTenantIds(List<String> tenantIds) {
        this.tenantIdsRaw = normalizeTenantIds(tenantIds);
    }

    public void setTenantIds(String tenantIds) {
        this.tenantIdsRaw = normalizeTenantIds(tokenizeTenantIds(tenantIds));
    }

    private static List<String> tokenizeTenantIds(String raw) {
        if (raw == null) {
            return List.of();
        }
        String normalized = raw.trim();
        if (normalized.isEmpty() || "[]".equals(normalized)) {
            return List.of();
        }
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
            if (normalized.isEmpty()) {
                return List.of();
            }
        }
        return List.of(normalized.split(","));
    }

    private static List<String> normalizeTenantIds(List<String> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> normalized = new ArrayList<>();
        for (String tenantId : tenantIds) {
            if (tenantId == null) {
                continue;
            }
            String token = tenantId.trim();
            if (token.isEmpty() || "[]".equals(token)) {
                continue;
            }
            normalized.add(token);
        }
        return normalized;
    }
}
