package cn.iocoder.yudao.module.cloudmold.mcp;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CloudMoldMcpSecurityFilterTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    @Test
    void failsClosedWhenTokenIsMissing() {
        CloudMoldMcpSecurityProperties properties = new CloudMoldMcpSecurityProperties();
        assertThatThrownBy(() -> new CloudMoldMcpSecurityFilter(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32");
    }

    @Test
    void rejectsMissingAuthorization() throws Exception {
        CloudMoldMcpSecurityFilter filter = filter("");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(new MockHttpServletRequest("POST", "/mcp"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsUntrustedBrowserOrigin() throws Exception {
        CloudMoldMcpSecurityFilter filter = filter("https://trusted.cloudmold.local");
        MockHttpServletRequest request = authorizedRequest();
        request.addHeader("Origin", "https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(chain);
    }

    @Test
    void acceptsBearerTokenWithoutBrowserOrigin() throws Exception {
        CloudMoldMcpSecurityFilter filter = filter("");
        MockHttpServletRequest request = authorizedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static CloudMoldMcpSecurityFilter filter(String allowedOrigins) {
        CloudMoldMcpSecurityProperties properties = new CloudMoldMcpSecurityProperties();
        properties.setBearerToken(TOKEN);
        properties.setAllowedOrigins(allowedOrigins);
        return new CloudMoldMcpSecurityFilter(properties);
    }

    private static MockHttpServletRequest authorizedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        return request;
    }
}
