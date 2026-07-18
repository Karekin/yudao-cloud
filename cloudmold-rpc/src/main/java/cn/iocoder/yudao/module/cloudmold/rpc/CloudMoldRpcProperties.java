package cn.iocoder.yudao.module.cloudmold.rpc;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

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
    private Set<String> exportServiceInterfaces = new LinkedHashSet<>();
    private Set<String> externalServiceInterfaces = new LinkedHashSet<>();
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
    public Set<String> getExportServiceInterfaces() { return exportServiceInterfaces; }
    public void setExportServiceInterfaces(Set<String> exportServiceInterfaces) {
        this.exportServiceInterfaces = exportServiceInterfaces == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(exportServiceInterfaces);
    }
    public Set<String> getExternalServiceInterfaces() { return externalServiceInterfaces; }
    public void setExternalServiceInterfaces(Set<String> externalServiceInterfaces) {
        this.externalServiceInterfaces = externalServiceInterfaces == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(externalServiceInterfaces);
    }
    public String getReadinessMarker() { return readinessMarker; }
    public void setReadinessMarker(String readinessMarker) { this.readinessMarker = readinessMarker; }
}
