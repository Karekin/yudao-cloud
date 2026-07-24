package cn.iocoder.yudao.module.cloudmold.agentcontrol;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "cloudmold.skill-task.approval")
public class SkillTaskApprovalSigningProperties {

    private String hmacSecret = "";
    private Duration maxValidity = Duration.ofHours(4);

}
