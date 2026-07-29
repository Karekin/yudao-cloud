package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "cloudmold.ai-operations.temporal")
public class AiOperationsTemporalProperties {

    private boolean enabled;
    private String target = "127.0.0.1:7233";
    private String namespace = "default";
    private String taskQueue = "cloudmold-ai-operations";
    private long activityTimeoutSeconds = 60;
    /** 业务事件等待兜底超时（秒）：事件驱动为主，此值仅在事件丢失时触发 child refresh 兜底 */
    private long businessEventTimeoutSeconds = 300;
}
