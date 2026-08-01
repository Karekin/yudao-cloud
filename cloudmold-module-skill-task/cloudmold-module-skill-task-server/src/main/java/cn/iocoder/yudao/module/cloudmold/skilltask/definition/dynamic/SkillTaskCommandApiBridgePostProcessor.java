package cn.iocoder.yudao.module.cloudmold.skilltask.definition.dynamic;

import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskCommandApi;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskSubmitCommand;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.stereotype.Component;

@Component
public class SkillTaskCommandApiBridgePostProcessor implements BeanPostProcessor {

    private final ActiveSkillTaskSubmitCommandResolver resolver;

    public SkillTaskCommandApiBridgePostProcessor(ActiveSkillTaskSubmitCommandResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof SkillTaskCommandApi) || !"skillTaskApiService".equals(beanName)) {
            return bean;
        }
        ProxyFactory factory = new ProxyFactory(bean);
        factory.setProxyTargetClass(true);
        factory.addAdvice((MethodInterceptor) invocation -> {
            if ("submit".equals(invocation.getMethod().getName())
                    && invocation.getArguments().length == 1
                    && invocation.getArguments()[0] instanceof SkillTaskSubmitCommand command) {
                invocation.getArguments()[0] = resolver.resolve(command);
            }
            return invocation.proceed();
        });
        return factory.getProxy();
    }
}
