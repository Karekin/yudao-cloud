package cn.iocoder.yudao.module.cloudmold.rpc;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(CloudMoldRpcProperties.class)
@ConditionalOnProperty(prefix = "cloudmold.rpc", name = "enabled", havingValue = "true")
public class CloudMoldRpcAutoConfiguration {

    @Bean
    InitializingBean cloudMoldRpcSecurityInitializer(CloudMoldRpcProperties properties) {
        return () -> CloudMoldRpcSecurity.configure(properties.getSharedSecret(),
                properties.getMaxClockSkewSeconds());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "cloudmold.rpc", name = "export-enabled", havingValue = "true")
    CloudMoldDubboServiceExporter cloudMoldDubboServiceExporter(ApplicationContext applicationContext,
                                                                 CloudMoldRpcProperties properties) {
        return new CloudMoldDubboServiceExporter(applicationContext, properties);
    }
}
