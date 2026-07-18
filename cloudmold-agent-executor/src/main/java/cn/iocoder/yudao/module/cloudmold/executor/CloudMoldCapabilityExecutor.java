package cn.iocoder.yudao.module.cloudmold.executor;

import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcCallContext;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.utils.SimpleReferenceCache;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CloudMoldCapabilityExecutor {

    private final CloudMoldCapabilityCatalog catalog;
    private final ObjectMapper objectMapper;
    private final Map<String, Object> proxies = new ConcurrentHashMap<>();

    public CloudMoldCapabilityExecutor(CloudMoldCapabilityCatalog catalog, ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.objectMapper = objectMapper;
    }

    public JsonNode execute(String capabilityId, JsonNode argumentArray, CloudMoldRpcCallContext context,
                            boolean writeApproved) {
        CapabilityDescriptor descriptor = catalog.require(capabilityId);
        if (descriptor.operationType() == CapabilityOperationType.WRITE && !writeApproved) {
            throw new SecurityException("Write capability requires explicit approval: " + capabilityId);
        }
        if (argumentArray == null || !argumentArray.isArray()) {
            throw new IllegalArgumentException("arguments must be a JSON array");
        }
        Method method = descriptor.method();
        if (argumentArray.size() != method.getParameterCount()) {
            throw new IllegalArgumentException("Expected " + method.getParameterCount() + " arguments but received "
                    + argumentArray.size());
        }
        Object[] arguments = new Object[method.getParameterCount()];
        for (int index = 0; index < arguments.length; index++) {
            arguments[index] = objectMapper.convertValue(argumentArray.get(index), method.getParameterTypes()[index]);
        }
        Object proxy = proxies.computeIfAbsent(descriptor.interfaceName(), ignored -> createProxy(descriptor));
        try (CloudMoldRpcCallContext.Scope ignored = CloudMoldRpcCallContext.open(context)) {
            Object result = method.invoke(proxy, arguments);
            return objectMapper.valueToTree(result);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getTargetException();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Capability invocation failed: " + capabilityId, cause);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Capability invocation failed: " + capabilityId, ex);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object createProxy(CapabilityDescriptor descriptor) {
        ReferenceConfig reference = new ReferenceConfig<>();
        reference.setInterface(descriptor.method().getDeclaringClass());
        reference.setGroup(descriptor.group());
        reference.setVersion(descriptor.version());
        reference.setTimeout(descriptor.timeoutMillis());
        reference.setRetries(0);
        reference.setFilter(CloudMoldRpcConstants.CONSUMER_FILTER);
        return SimpleReferenceCache.getCache().get(reference);
    }
}
