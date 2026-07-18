package cn.iocoder.yudao.module.cloudmold.mcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CloudMoldMcpSecurityProperties.class)
public class CloudMoldMcpConfiguration {

    @Bean
    HttpServletStreamableServerTransportProvider cloudMoldMcpTransport() {
        return HttpServletStreamableServerTransportProvider.builder()
                .mcpEndpoint("/mcp")
                .build();
    }

    @Bean
    ServletRegistrationBean<HttpServletStreamableServerTransportProvider> cloudMoldMcpServlet(
            HttpServletStreamableServerTransportProvider transport) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
                new ServletRegistrationBean<>(transport, "/mcp");
        registration.setName("cloudMoldMcpServlet");
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean
    FilterRegistrationBean<CloudMoldMcpSecurityFilter> cloudMoldMcpSecurityFilter(
            CloudMoldMcpSecurityProperties properties) {
        FilterRegistrationBean<CloudMoldMcpSecurityFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CloudMoldMcpSecurityFilter(properties));
        registration.addUrlPatterns("/mcp");
        registration.setOrder(Integer.MIN_VALUE);
        return registration;
    }

    @Bean
    McpSyncServer cloudMoldMcpServer(HttpServletStreamableServerTransportProvider transport,
                                     CloudMoldMcpTools tools) {
        return McpServer.sync(transport)
                .serverInfo("cloudmold-hsf-mcp-server", "1.0.0")
                .instructions("Discover governed CloudMold Dubbo contracts and invoke READ capabilities only. "
                        + "Business writes must be submitted through durable Skill Tasks.")
                .toolCall(tools.listTool(), tools::list)
                .toolCall(tools.describeTool(), tools::describe)
                .toolCall(tools.invokeReadTool(), tools::invokeRead)
                .build();
    }
}
