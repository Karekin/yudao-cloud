package cn.iocoder.yudao.module.cloudmold.operationsintelligence.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(OperationsIntelligenceAnalyticsProperties.class)
public class OperationsIntelligenceConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock operationsIntelligenceClock() {
        return Clock.systemUTC();
    }

}
