package cn.iocoder.yudao.module.cloudmold.executor;

import java.lang.reflect.Method;
import java.util.List;

public record CapabilityDescriptor(
        String capabilityId,
        String interfaceName,
        String methodName,
        List<String> parameterTypes,
        String returnType,
        CapabilityOperationType operationType,
        String group,
        String version,
        int timeoutMillis,
        Method method) {
}
