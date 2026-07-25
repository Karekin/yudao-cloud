package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.riskauthority;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.SkillTaskApprovalSigningProperties;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.service.RiskAuthoritySigningPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

@Component
@ConditionalOnProperty(prefix = "cloudmold.skill-task.approval", name = "authority-mode",
        havingValue = "REMOTE_RSA")
public class HttpRiskAuthoritySigningAdapter implements RiskAuthoritySigningPort {

    private final URI endpoint;
    private final String bearerToken;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpRiskAuthoritySigningAdapter(SkillTaskApprovalSigningProperties properties, ObjectMapper objectMapper) {
        SkillTaskApprovalSigningProperties.RemoteSigner remote =
                Objects.requireNonNull(properties.getRemote(), "remote signer configuration");
        this.endpoint = requireEndpoint(remote.getEndpoint());
        this.bearerToken = requireText(remote.getBearerToken(), "remote signer bearerToken");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(requirePositive(remote.getConnectTimeout(), "remote signer connectTimeout"))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public RiskAuthoritySignResponse sign(RiskAuthoritySignRequest request, Duration timeout) {
        Objects.requireNonNull(request, "request");
        try {
            HttpRequest httpRequest = buildRequest(request, timeout);
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Risk Authority signing request failed with status "
                        + response.statusCode());
            }
            RiskAuthoritySignResponse result =
                    objectMapper.readValue(response.body(), RiskAuthoritySignResponse.class);
            if (result == null) {
                throw new IllegalStateException("Risk Authority returned no signing response");
            }
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Risk Authority signing request was interrupted");
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Risk Authority signing request failed");
        }
    }

    HttpRequest buildRequest(RiskAuthoritySignRequest request, Duration timeout) throws Exception {
        return HttpRequest.newBuilder(endpoint)
                .timeout(requirePositive(timeout, "remote signer requestTimeout"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + bearerToken)
                .header("X-Request-Id", request.requestId())
                .header("Idempotency-Key", request.permitId())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                .build();
    }

    private static URI requireEndpoint(String configured) {
        String value = requireText(configured, "remote signer endpoint");
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute() || uri.getHost() == null
                    || (!"https".equalsIgnoreCase(uri.getScheme())
                    && !"http".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalStateException("remote signer endpoint must be an absolute HTTP(S) URI");
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("remote signer endpoint is invalid");
        }
    }

    private static Duration requirePositive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(field + " must be positive");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        String result = Objects.toString(value, "").trim();
        if (result.isEmpty()) {
            throw new IllegalStateException(field + " is required");
        }
        return result;
    }
}
