package cn.iocoder.yudao.module.cloudmold.skilltask;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "cloudmold.skill-task")
public class SkillTaskProperties {

    private Path registryRoot = Path.of("/app/skills");
    private boolean failOnEmptyRegistry = true;
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration workerDelay = Duration.ofSeconds(1);
    private Duration maxBackoff = Duration.ofMinutes(5);
    private int batchSize = 20;
    private int defaultMaxAttempts = 5;
    private int maxPayloadBytes = 1_048_576;
    private int maxErrorMessageLength = 2_000;
    private boolean workerEnabled = true;
}
