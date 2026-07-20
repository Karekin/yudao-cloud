package cn.iocoder.yudao.module.cloudmold.agentcontrol;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "cloudmold.agent-control")
public class AgentControlProperties {

    private boolean enabled = false;
    private boolean actionSubmissionEnabled = false;

}
