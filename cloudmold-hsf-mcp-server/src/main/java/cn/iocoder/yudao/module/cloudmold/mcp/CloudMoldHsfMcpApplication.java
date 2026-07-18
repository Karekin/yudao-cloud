package cn.iocoder.yudao.module.cloudmold.mcp;

import cn.iocoder.yudao.module.cloudmold.executor.CapabilityContractDescriber;
import cn.iocoder.yudao.module.cloudmold.executor.CloudMoldCapabilityCatalog;
import cn.iocoder.yudao.module.cloudmold.executor.CloudMoldCapabilityExecutor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({CloudMoldCapabilityCatalog.class, CloudMoldCapabilityExecutor.class, CapabilityContractDescriber.class})
public class CloudMoldHsfMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudMoldHsfMcpApplication.class, args);
    }
}
