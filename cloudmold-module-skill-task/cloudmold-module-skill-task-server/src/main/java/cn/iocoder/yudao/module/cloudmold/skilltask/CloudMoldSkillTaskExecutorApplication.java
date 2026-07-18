package cn.iocoder.yudao.module.cloudmold.skilltask;

import cn.iocoder.yudao.module.cloudmold.executor.CloudMoldCapabilityCatalog;
import cn.iocoder.yudao.module.cloudmold.executor.CloudMoldCapabilityExecutor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("cn.iocoder.yudao.module.cloudmold.skilltask.dal")
@EnableConfigurationProperties(SkillTaskProperties.class)
@Import({CloudMoldCapabilityCatalog.class, CloudMoldCapabilityExecutor.class})
@SpringBootApplication
public class CloudMoldSkillTaskExecutorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudMoldSkillTaskExecutorApplication.class, args);
    }
}
