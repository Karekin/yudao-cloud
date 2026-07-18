package cn.iocoder.yudao.module.cloudmold.rpc;

import org.apache.dubbo.config.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class CloudMoldDubboServiceExporter implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(CloudMoldDubboServiceExporter.class);

    private final ApplicationContext applicationContext;
    private final CloudMoldRpcProperties properties;
    private final Set<String> serviceInterfaces;
    private final List<ServiceConfig<Object>> exports = new ArrayList<>();
    private final Path readinessMarker;

    public CloudMoldDubboServiceExporter(ApplicationContext applicationContext, CloudMoldRpcProperties properties) {
        this.applicationContext = applicationContext;
        this.properties = properties;
        this.serviceInterfaces = selectServiceInterfaces(CloudMoldDubboServiceAllowlist.load(),
                properties.getExportServiceInterfaces());
        this.readinessMarker = markerPath(properties.getReadinessMarker());
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public synchronized void onApplicationEvent(ApplicationReadyEvent event) {
        if (!exports.isEmpty()) {
            return;
        }
        deleteReadinessMarker();
        ClassLoader classLoader = applicationContext.getClassLoader();
        for (String interfaceName : serviceInterfaces) {
            try {
                Class<?> serviceInterface = Class.forName(interfaceName, false, classLoader);
                if (!serviceInterface.isInterface()) {
                    throw new IllegalStateException(interfaceName + " is not an interface");
                }
                Map<String, ?> beans = applicationContext.getBeansOfType(serviceInterface);
                if (beans.size() != 1) {
                    if (beans.isEmpty() && properties.getExternalServiceInterfaces().contains(interfaceName)) {
                        log.info("Skipping externally provided governed Dubbo service {}", interfaceName);
                        continue;
                    }
                    String message = "Expected exactly one provider bean for " + interfaceName + " but found "
                            + beans.keySet();
                    if (properties.isFailOnMissingService()) {
                        throw new IllegalStateException(message);
                    }
                    log.warn(message);
                    continue;
                }
                ServiceConfig<Object> service = new ServiceConfig<>();
                service.setInterface((Class) serviceInterface);
                service.setRef(beans.values().iterator().next());
                service.setGroup(properties.getGroup());
                service.setVersion(properties.getVersion());
                service.setTimeout(properties.getTimeoutMillis());
                service.setRetries(0);
                service.setFilter(CloudMoldRpcConstants.PROVIDER_FILTER);
                service.export();
                exports.add(service);
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException("Allowlisted Dubbo service is missing from the runtime: "
                        + interfaceName, ex);
            }
        }
        log.info("Exported {} governed CloudMold Dubbo services using group={} version={}", exports.size(),
                properties.getGroup(), properties.getVersion());
        writeReadinessMarker();
    }

    public int exportedServiceCount() {
        return exports.size();
    }

    static Set<String> selectServiceInterfaces(Set<String> allowlist, Set<String> configured) {
        if (configured == null || configured.isEmpty()) {
            return allowlist;
        }
        LinkedHashSet<String> unknown = new LinkedHashSet<>(configured);
        unknown.removeAll(allowlist);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("cloudmold.rpc.export-service-interfaces contains non-allowlisted "
                    + "interfaces: " + unknown);
        }
        return Set.copyOf(configured);
    }

    @Override
    public synchronized void destroy() {
        deleteReadinessMarker();
        exports.forEach(ServiceConfig::unexport);
        exports.clear();
    }

    private static Path markerPath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Path path = Path.of(value).normalize();
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("cloudmold.rpc.readiness-marker must be an absolute path");
        }
        return path;
    }

    private void writeReadinessMarker() {
        if (readinessMarker == null) {
            return;
        }
        try {
            Files.writeString(readinessMarker, Integer.toString(exports.size()), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot publish CloudMold RPC readiness marker " + readinessMarker, ex);
        }
    }

    private void deleteReadinessMarker() {
        if (readinessMarker == null) {
            return;
        }
        try {
            Files.deleteIfExists(readinessMarker);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot clear CloudMold RPC readiness marker " + readinessMarker, ex);
        }
    }
}
