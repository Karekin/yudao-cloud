package cn.iocoder.yudao.server.config;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiAccessLogCommonApi;
import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.biz.infra.logger.dto.ApiAccessLogCreateReqDTO;
import cn.iocoder.yudao.framework.common.biz.infra.logger.dto.ApiErrorLogCreateReqDTO;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 单体运行时直接桥接 Infra 日志服务，避免依赖 RPC/Feign 注入。
 */
@Configuration(proxyBeanMethods = false)
public class LocalInfraLogApiConfiguration {

    @Bean
    @ConditionalOnMissingBean(ApiErrorLogCommonApi.class)
    public ApiErrorLogCommonApi apiErrorLogCommonApi(ApplicationContext context) {
        return new ApiErrorLogCommonApi() {
            @Override
            public CommonResult<Boolean> createApiErrorLog(ApiErrorLogCreateReqDTO createDTO) {
                invoke(context,
                        "cn.iocoder.yudao.module.infra.service.logger.ApiErrorLogService",
                        "createApiErrorLog", createDTO);
                return success(true);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(ApiAccessLogCommonApi.class)
    public ApiAccessLogCommonApi apiAccessLogCommonApi(ApplicationContext context) {
        return new ApiAccessLogCommonApi() {
            @Override
            public CommonResult<Boolean> createApiAccessLog(ApiAccessLogCreateReqDTO createDTO) {
                invoke(context,
                        "cn.iocoder.yudao.module.infra.service.logger.ApiAccessLogService",
                        "createApiAccessLog", createDTO);
                return success(true);
            }
        };
    }

    private static void invoke(ApplicationContext context, String serviceClassName,
                               String methodName, Object argument) {
        try {
            Class<?> serviceClass = Class.forName(serviceClassName);
            Object service = context.getBean(serviceClass);
            Method method = serviceClass.getMethod(methodName, argument.getClass());
            method.invoke(service, argument);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("Infra log service bridge is unavailable", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Infra log service bridge failed", cause);
        }
    }

}
