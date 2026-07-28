package cn.iocoder.yudao.module.cloudmold.operationsintelligence.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@Data
@ConfigurationProperties(prefix = "cloudmold.analytics")
public class OperationsIntelligenceAnalyticsProperties {

    private Path dataDirectory = Path.of("/app/cloudmold-commerce-analytics/app/data");

    public Path snapshotPath() {
        return dataDirectory.resolve("dashboard-snapshot.json");
    }

    public Path catalogPath() {
        return dataDirectory.resolve("kpi-catalog.json");
    }
}
