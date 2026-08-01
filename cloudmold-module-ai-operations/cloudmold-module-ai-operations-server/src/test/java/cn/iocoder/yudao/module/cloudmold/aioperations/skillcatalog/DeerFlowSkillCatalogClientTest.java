package cn.iocoder.yudao.module.cloudmold.aioperations.skillcatalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeerFlowSkillCatalogClientTest {

    private final HttpClient httpClient = mock(HttpClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReadCatalogWithScopedTokenAndNoRedirect() throws Exception {
        HttpResponse<InputStream> response = response(200, "{\"schema_version\":\"cloudmold.skill-business-taxonomy/v1\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        DeerFlowSkillCatalogClient client = new DeerFlowSkillCatalogClient(
                httpClient, objectMapper, "http://127.0.0.1:2026", "secret-token");

        var result = client.getCatalog();

        assertThat(result.path("schema_version").asText())
                .isEqualTo("cloudmold.skill-business-taxonomy/v1");
        var requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();
        assertThat(request.uri().toString()).isEqualTo("http://127.0.0.1:2026/api/skills/business-catalog");
        assertThat(request.headers().firstValue("X-DeerFlow-Skill-Catalog-Token")).contains("secret-token");
        assertThat(request.headers().firstValue("X-DeerFlow-Internal-Token")).isEmpty();
    }

    @Test
    void shouldRejectUnsafeSkillNameBeforeRequest() {
        DeerFlowSkillCatalogClient client = new DeerFlowSkillCatalogClient(
                httpClient, objectMapper, "http://127.0.0.1:2026", "secret-token");

        assertThatThrownBy(() -> client.getSkill("../private"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hyphen-case");
    }

    @Test
    void shouldFailClosedWhenTokenMissingOrUpstreamFails() throws Exception {
        DeerFlowSkillCatalogClient missingToken = new DeerFlowSkillCatalogClient(
                httpClient, objectMapper, "http://127.0.0.1:2026", "");
        assertThatThrownBy(missingToken::getCatalog)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token is not configured");

        HttpResponse<InputStream> unavailableResponse = response(503, "{\"detail\":\"unavailable\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(unavailableResponse);
        DeerFlowSkillCatalogClient upstreamFailure = new DeerFlowSkillCatalogClient(
                httpClient, objectMapper, "http://127.0.0.1:2026", "secret-token");
        assertThatThrownBy(upstreamFailure::getCatalog)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("status 503")
                .hasMessageNotContaining("secret-token");
    }

    @Test
    void shouldStopReadingWhenResponseExceedsLimit() throws Exception {
        byte[] oversized = new byte[4 * 1024 * 1024 + 1];
        HttpResponse<InputStream> response = response(200, oversized);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        DeerFlowSkillCatalogClient client = new DeerFlowSkillCatalogClient(
                httpClient, objectMapper, "http://127.0.0.1:2026", "secret-token");

        assertThatThrownBy(client::getCatalog)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeded the read limit");
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<InputStream> response(int status, String body) {
        return response(status, body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<InputStream> response(int status, byte[] body) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(new ByteArrayInputStream(body));
        return response;
    }
}
