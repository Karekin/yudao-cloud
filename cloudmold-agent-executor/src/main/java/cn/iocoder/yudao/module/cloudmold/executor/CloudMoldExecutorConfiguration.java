package cn.iocoder.yudao.module.cloudmold.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class CloudMoldExecutorConfiguration {

    @Bean
    ObjectMapper cloudMoldExecutorObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
