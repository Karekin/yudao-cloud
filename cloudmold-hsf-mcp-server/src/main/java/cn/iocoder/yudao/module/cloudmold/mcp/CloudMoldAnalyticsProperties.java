package cn.iocoder.yudao.module.cloudmold.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "cloudmold.analytics")
public class CloudMoldAnalyticsProperties {
    private Path dataDirectory = Path.of("/app/cloudmold-commerce-analytics/app/data");

    public Path getDataDirectory() { return dataDirectory; }
    public void setDataDirectory(Path dataDirectory) { this.dataDirectory = dataDirectory; }
    public Path snapshotPath() { return dataDirectory.resolve("dashboard-snapshot.json"); }
    public Path catalogPath() { return dataDirectory.resolve("kpi-catalog.json"); }
}
