package cn.iocoder.yudao.module.cloudmold.operationsintelligence.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@Data
@ConfigurationProperties(prefix = "cloudmold.analytics")
public class OperationsIntelligenceAnalyticsProperties {

    private Path dataDirectory = Path.of("/app/cloudmold-commerce-analytics/app/data");
    /**
     * Prefer the tenant-scoped live StarRocks KPI mart over the checked-in
     * snapshot. The compose profile enables this explicitly; deployments must
     * provide a read-only identity instead of relying on local defaults.
     */
    private boolean liveQueryEnabled;
    private String starRocksJdbcUrl =
            "jdbc:mysql://yshopping-starrocks:9030/yshopping_ads"
                    + "?useSSL=false&allowPublicKeyRetrieval=true"
                    + "&connectTimeout=5000&socketTimeout=45000";
    private String starRocksUsername = "root";
    private String starRocksPassword = "";
    private int queryTimeoutSeconds = 45;
    private int queryBatchSize = 5;

    public Path snapshotPath() {
        return dataDirectory.resolve("dashboard-snapshot.json");
    }

    public Path catalogPath() {
        return dataDirectory.resolve("kpi-catalog.json");
    }
}
