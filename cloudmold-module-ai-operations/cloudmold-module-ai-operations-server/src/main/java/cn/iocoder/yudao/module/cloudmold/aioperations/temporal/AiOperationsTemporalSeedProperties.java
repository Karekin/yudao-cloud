package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "cloudmold.ai-operations.temporal.seed")
public class AiOperationsTemporalSeedProperties {

    /**
     * 是否在启动时按当前配置自动创建 Temporal 定时工作流。
     */
    private boolean enabled = false;

    /**
     * 需要补齐/创建定时流的租户列表。
     */
    private List<Long> tenantIds = new ArrayList<>();

    /**
     * 系统用户（后台系统管理员）编号。
     */
    private long operatorUserId = 1L;

    /**
     * 系统用户类型。
     */
    private int operatorUserType = 1;

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
    private String skillId = "skill.cloudmold.commerce.catalog-matrix.v1";

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
    private long intervalSeconds = 3600L;

    /**
     * 时间时区。
     */
    private String timeZone = "Asia/Shanghai";

    /**
     * 若定时规则需要 BPM 审批，补齐角色与动作编码；非审批可留空。
     */
    private String roleCode;

    /**
     * 若定时规则需要 BPM 审批，补齐角色与动作编码；非审批可留空。
     */
    private String actionCode;

    /**
     * 是否创建为暂停态。
     */
    private boolean paused = false;
}
