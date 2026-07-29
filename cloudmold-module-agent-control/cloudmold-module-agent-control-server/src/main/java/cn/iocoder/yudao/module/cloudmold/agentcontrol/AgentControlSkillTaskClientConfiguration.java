package cn.iocoder.yudao.module.cloudmold.agentcontrol;

import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcConstants;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcProperties;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskCommandApi;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskQueryApi;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.utils.SimpleReferenceCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "cloudmold.agent-control", name = "enabled", havingValue = "true")
public class AgentControlSkillTaskClientConfiguration {

    @Bean
    public SkillTaskCommandApi agentControlSkillTaskCommandApi(CloudMoldRpcProperties properties) {
        return reference(SkillTaskCommandApi.class, properties);
    }

    @Bean
    @Primary
    public SkillTaskQueryApi agentControlSkillTaskQueryApi(CloudMoldRpcProperties properties) {
        return reference(SkillTaskQueryApi.class, properties);
    }

    private <T> T reference(Class<T> interfaceType, CloudMoldRpcProperties properties) {
        if (!properties.getExternalServiceInterfaces().contains(interfaceType.getName())) {
            throw new IllegalStateException("SkillTask client is not declared as an external CloudMold service: "
                    + interfaceType.getName());
        }
        ReferenceConfig<T> reference = new ReferenceConfig<>();
        reference.setInterface(interfaceType);
        reference.setGroup(properties.getGroup());
        reference.setVersion(properties.getVersion());
        reference.setTimeout(properties.getTimeoutMillis());
        reference.setRetries(0);
        reference.setCheck(properties.isFailOnMissingService());
        reference.setFilter(CloudMoldRpcConstants.CONSUMER_FILTER);
        return SimpleReferenceCache.getCache().get(reference);
    }
}
