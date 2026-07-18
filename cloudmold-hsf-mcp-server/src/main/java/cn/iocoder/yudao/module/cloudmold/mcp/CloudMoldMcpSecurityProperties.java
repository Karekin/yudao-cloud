package cn.iocoder.yudao.module.cloudmold.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "cloudmold.mcp")
public class CloudMoldMcpSecurityProperties {

    private String bearerToken;
    private String allowedOrigins = "";

    public String getBearerToken() {
        return bearerToken;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public Set<String> allowedOriginSet() {
        return Arrays.stream(allowedOrigins == null ? new String[0] : allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public void validate() {
        if (bearerToken == null || bearerToken.length() < 32) {
            throw new IllegalStateException("cloudmold.mcp.bearer-token must contain at least 32 characters");
        }
    }
}
