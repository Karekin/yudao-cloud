package cn.iocoder.yudao.module.cloudmold.skilltask;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class SkillTaskRuntimeConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock skillTaskClock() {
        return Clock.systemDefaultZone();
    }
}
