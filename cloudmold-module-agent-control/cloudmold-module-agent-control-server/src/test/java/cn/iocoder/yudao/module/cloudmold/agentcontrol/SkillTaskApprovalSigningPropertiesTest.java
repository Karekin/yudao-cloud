package cn.iocoder.yudao.module.cloudmold.agentcontrol;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillTaskApprovalSigningPropertiesTest {

    @Test
    void bindsRemoteSignerEndpointTimeoutsAndKeyPolicy() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.ofEntries(
                Map.entry("cloudmold.skill-task.approval.authority-mode", "REMOTE_RSA"),
                Map.entry("cloudmold.skill-task.approval.issue-version", "cma3"),
                Map.entry("cloudmold.skill-task.approval.remote.endpoint",
                        "https://risk.example.test/v1/permits/sign"),
                Map.entry("cloudmold.skill-task.approval.remote.bearer-token", "test-token"),
                Map.entry("cloudmold.skill-task.approval.remote.expected-key-id", "risk-epoch-7"),
                Map.entry("cloudmold.skill-task.approval.remote.algorithm", "RS256"),
                Map.entry("cloudmold.skill-task.approval.remote.connect-timeout", "750ms"),
                Map.entry("cloudmold.skill-task.approval.remote.request-timeout", "2500ms"),
                Map.entry("cloudmold.skill-task.approval.remote.verification-keys.risk-epoch-7.public-key-pem",
                        "public-key"),
                Map.entry("cloudmold.skill-task.approval.remote.verification-keys.risk-epoch-7.not-before",
                        "2026-07-25T00:00:00Z"),
                Map.entry("cloudmold.skill-task.approval.remote.verification-keys.risk-epoch-7.expires-at",
                        "2026-08-25T00:00:00Z")));

        SkillTaskApprovalSigningProperties properties = new Binder(source)
                .bind("cloudmold.skill-task.approval",
                        Bindable.of(SkillTaskApprovalSigningProperties.class))
                .get();

        assertThat(properties.getAuthorityMode()).isEqualTo("REMOTE_RSA");
        assertThat(properties.getIssueVersion()).isEqualTo("cma3");
        assertThat(properties.getRemote().getEndpoint())
                .isEqualTo("https://risk.example.test/v1/permits/sign");
        assertThat(properties.getRemote().getConnectTimeout()).isEqualTo(Duration.ofMillis(750));
        assertThat(properties.getRemote().getRequestTimeout()).isEqualTo(Duration.ofMillis(2500));
        assertThat(properties.getRemote().getExpectedKeyId()).isEqualTo("risk-epoch-7");
        assertThat(properties.getRemote().getVerificationKeys()).containsKey("risk-epoch-7");
    }
}
