package cn.iocoder.yudao.module.cloudmold.aioperations;

import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcConstants;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcProperties;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskQueryApi;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.utils.SimpleReferenceCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "cloudmold.rpc", name = "enabled", havingValue = "true")
public class AiOperationsSkillTaskClientConfiguration {

    @Bean
    @ConditionalOnMissingBean(SkillTaskQueryApi.class)
    public SkillTaskQueryApi aiOperationsSkillTaskQueryApi(CloudMoldRpcProperties properties) {
        if (!properties.getExternalServiceInterfaces().contains(SkillTaskQueryApi.class.getName())) {
            throw new IllegalStateException("SkillTask query client is not declared as an external CloudMold service: "
                    + SkillTaskQueryApi.class.getName());
        }
        ReferenceConfig<SkillTaskQueryApi> reference = new ReferenceConfig<>();
        reference.setInterface(SkillTaskQueryApi.class);
        reference.setGroup(properties.getGroup());
        reference.setVersion(properties.getVersion());
        reference.setTimeout(properties.getTimeoutMillis());
        reference.setRetries(0);
        reference.setCheck(properties.isFailOnMissingService());
        reference.setFilter(CloudMoldRpcConstants.CONSUMER_FILTER);
        return SimpleReferenceCache.getCache().get(reference);
    }
}
