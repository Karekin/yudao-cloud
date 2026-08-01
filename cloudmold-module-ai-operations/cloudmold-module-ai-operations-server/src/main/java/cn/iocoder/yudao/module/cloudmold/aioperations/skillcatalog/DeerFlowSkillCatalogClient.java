package cn.iocoder.yudao.module.cloudmold.aioperations.skillcatalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

/** Read-only service bridge to DeerFlow's effective public Skill catalog. */
@Component
public class DeerFlowSkillCatalogClient {

    private static final String CATALOG_TOKEN_HEADER = "X-DeerFlow-Skill-Catalog-Token";
    private static final Pattern SKILL_NAME = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final String catalogToken;

    @Autowired
    public DeerFlowSkillCatalogClient(
            ObjectMapper objectMapper,
            @Value("${cloudmold.ai-operations.deer-flow.base-url:http://127.0.0.1:2026}") String baseUrl,
            @Value("${cloudmold.ai-operations.deer-flow.catalog-token:}") String catalogToken) {
        this(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                objectMapper, baseUrl, catalogToken);
    }

    DeerFlowSkillCatalogClient(HttpClient httpClient, ObjectMapper objectMapper,
                               String baseUrl, String catalogToken) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUri = normalizeBaseUri(baseUrl);
        this.catalogToken = catalogToken == null ? "" : catalogToken.trim();
    }

    public JsonNode getCatalog() {
        return get("api/skills/business-catalog");
    }

    public JsonNode getSkill(String skillName) {
        if (skillName == null || !SKILL_NAME.matcher(skillName).matches() || skillName.length() > 64) {
            throw new IllegalArgumentException(
                    "Skill name must be hyphen-case using lowercase letters, digits, and hyphens only");
        }
        return get("api/skills/business-catalog/" + skillName);
    }

    private JsonNode get(String relativePath) {
        if (catalogToken.isEmpty()) {
            throw new IllegalStateException("DeerFlow Skill catalog token is not configured");
        }
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(relativePath))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header(CATALOG_TOKEN_HEADER, catalogToken)
                .GET()
                .build();
        final HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DeerFlow Skill catalog request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("DeerFlow Skill catalog is unavailable", exception);
        }
        String body;
        try (InputStream input = response.body()) {
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "DeerFlow Skill catalog returned status " + response.statusCode());
            }
            if (input == null) {
                throw new IllegalStateException("DeerFlow Skill catalog returned an empty response");
            }
            body = readBounded(input);
        } catch (IOException exception) {
            throw new IllegalStateException("DeerFlow Skill catalog response could not be read", exception);
        }
        try {
            JsonNode payload = objectMapper.readTree(body);
            if (payload == null || !payload.isObject()) {
                throw new IllegalStateException("DeerFlow Skill catalog returned an invalid payload");
            }
            return payload;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("DeerFlow Skill catalog returned invalid JSON", exception);
        }
    }

    private static String readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_RESPONSE_BYTES) {
                throw new IllegalStateException("DeerFlow Skill catalog response exceeded the read limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static URI normalizeBaseUri(String baseUrl) {
        URI value;
        try {
            value = URI.create(baseUrl == null ? "" : baseUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid DeerFlow base URL", exception);
        }
        if (!("http".equals(value.getScheme()) || "https".equals(value.getScheme()))
                || value.getHost() == null || value.getUserInfo() != null
                || value.getQuery() != null || value.getFragment() != null) {
            throw new IllegalArgumentException("DeerFlow base URL must be an HTTP(S) origin without credentials");
        }
        String normalized = value.toString();
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        return URI.create(normalized);
    }
}
