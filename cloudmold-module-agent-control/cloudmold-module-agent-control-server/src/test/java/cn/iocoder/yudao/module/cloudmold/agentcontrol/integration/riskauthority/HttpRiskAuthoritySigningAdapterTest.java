package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.riskauthority;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.SkillTaskApprovalSigningProperties;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.service.RiskAuthoritySigningPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRiskAuthoritySigningAdapterTest {

    @Test
    void buildsBoundedAuthenticatedIdempotentRequestWithoutSendingIt() throws Exception {
        SkillTaskApprovalSigningProperties properties = new SkillTaskApprovalSigningProperties();
        properties.getRemote().setEndpoint("https://risk.example.test/v1/permits/sign");
        properties.getRemote().setBearerToken("test-token");
        properties.getRemote().setConnectTimeout(Duration.ofMillis(750));
        HttpRiskAuthoritySigningAdapter adapter =
                new HttpRiskAuthoritySigningAdapter(properties, new ObjectMapper());
        RiskAuthoritySigningPort.RiskAuthoritySignRequest request =
                new RiskAuthoritySigningPort.RiskAuthoritySignRequest(
                        "request-sha256", "permit-remote-0001", "RS256", "claims-payload");

        HttpRequest httpRequest = adapter.buildRequest(request, Duration.ofMillis(2500));

        assertThat(httpRequest.uri().toString())
                .isEqualTo("https://risk.example.test/v1/permits/sign");
        assertThat(httpRequest.timeout()).contains(Duration.ofMillis(2500));
        assertThat(httpRequest.headers().firstValue("Authorization"))
                .contains("Bearer test-token");
        assertThat(httpRequest.headers().firstValue("X-Request-Id"))
                .contains("request-sha256");
        assertThat(httpRequest.headers().firstValue("Idempotency-Key"))
                .contains("permit-remote-0001");
        assertThat(httpRequest.bodyPublisher()).isPresent();
    }
}
