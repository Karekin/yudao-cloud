package cn.iocoder.yudao.module.cloudmold.rpc;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudmold.rpc")
public class CloudMoldRpcProperties {

    private boolean enabled;
    private boolean exportEnabled;
    private String sharedSecret;
    private String group = "cloudmold-internal";
    private String version = "1.0.0";
    private int timeoutMillis = 5000;
    private long maxClockSkewSeconds = 60;
    private boolean failOnMissingService = true;
    private String readinessMarker;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isExportEnabled() { return exportEnabled; }
    public void setExportEnabled(boolean exportEnabled) { this.exportEnabled = exportEnabled; }
    public String getSharedSecret() { return sharedSecret; }
    public void setSharedSecret(String sharedSecret) { this.sharedSecret = sharedSecret; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public int getTimeoutMillis() { return timeoutMillis; }
    public void setTimeoutMillis(int timeoutMillis) { this.timeoutMillis = timeoutMillis; }
    public long getMaxClockSkewSeconds() { return maxClockSkewSeconds; }
    public void setMaxClockSkewSeconds(long maxClockSkewSeconds) { this.maxClockSkewSeconds = maxClockSkewSeconds; }
    public boolean isFailOnMissingService() { return failOnMissingService; }
    public void setFailOnMissingService(boolean failOnMissingService) { this.failOnMissingService = failOnMissingService; }
    public String getReadinessMarker() { return readinessMarker; }
    public void setReadinessMarker(String readinessMarker) { this.readinessMarker = readinessMarker; }
}
