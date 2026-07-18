package cn.iocoder.yudao.module.cloudmold.executor;

import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldCapabilityIds;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldDubboServiceAllowlist;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcProperties;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CloudMoldCapabilityCatalog {

    private final Map<String, CapabilityDescriptor> descriptors;

    public CloudMoldCapabilityCatalog(CloudMoldRpcProperties properties) {
        Map<String, CapabilityDescriptor> discovered = new LinkedHashMap<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (String interfaceName : CloudMoldDubboServiceAllowlist.load()) {
            try {
                Class<?> serviceInterface = Class.forName(interfaceName, false, classLoader);
                for (Method method : serviceInterface.getMethods()) {
                    if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())
                            || method.isDefault()) {
                        continue;
                    }
                    String capabilityId = CloudMoldCapabilityIds.forMethod(method);
                    CapabilityDescriptor descriptor = new CapabilityDescriptor(
                            capabilityId,
                            interfaceName,
                            method.getName(),
                            Arrays.stream(method.getParameterTypes()).map(Class::getName).toList(),
                            method.getReturnType().getName(),
                            operationType(serviceInterface, method),
                            properties.getGroup(),
                            properties.getVersion(),
                            properties.getTimeoutMillis(),
                            method);
                    CapabilityDescriptor previous = discovered.putIfAbsent(capabilityId, descriptor);
                    if (previous != null) {
                        throw new IllegalStateException("Overloaded or duplicate capability id " + capabilityId
                                + " for " + previous.interfaceName() + " and " + interfaceName);
                    }
                }
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException("Allowlisted capability interface is missing: " + interfaceName, ex);
            }
        }
        this.descriptors = Collections.unmodifiableMap(discovered);
    }

    public CapabilityDescriptor require(String capabilityId) {
        CapabilityDescriptor descriptor = descriptors.get(capabilityId);
        if (descriptor == null) {
            throw new IllegalArgumentException("Capability is not registered: " + capabilityId);
        }
        return descriptor;
    }

    public List<CapabilityDescriptor> all() {
        List<CapabilityDescriptor> result = new ArrayList<>(descriptors.values());
        result.sort(Comparator.comparing(CapabilityDescriptor::capabilityId));
        return Collections.unmodifiableList(result);
    }

    private static CapabilityOperationType operationType(Class<?> serviceInterface, Method method) {
        String name = serviceInterface.getSimpleName();
        if (name.contains("Query") || name.contains("Validation") || name.contains("Reference")
                || name.contains("Projection") || name.contains("Authorization")) {
            return CapabilityOperationType.READ;
        }
        String methodName = method.getName();
        if (methodName.startsWith("get") || methodName.startsWith("list")
                || methodName.startsWith("find") || methodName.startsWith("query")
                || methodName.startsWith("require") || methodName.startsWith("resolve")
                || methodName.startsWith("validate")) {
            return CapabilityOperationType.READ;
        }
        return CapabilityOperationType.WRITE;
    }
}
