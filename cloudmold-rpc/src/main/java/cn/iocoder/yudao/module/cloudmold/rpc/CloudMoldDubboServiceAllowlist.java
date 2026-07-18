package cn.iocoder.yudao.module.cloudmold.rpc;

import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class CloudMoldDubboServiceAllowlist {

    private CloudMoldDubboServiceAllowlist() {
    }

    public static Set<String> load() {
        ClassPathResource resource = new ClassPathResource(CloudMoldRpcConstants.SERVICES_RESOURCE);
        Set<String> services = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines().map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(service -> {
                        if (!service.startsWith("cn.iocoder.yudao.module.cloudmold.") || !service.endsWith("Api")) {
                            throw new IllegalStateException("Invalid CloudMold Dubbo service allowlist entry: " + service);
                        }
                        if (!services.add(service)) {
                            throw new IllegalStateException("Duplicate CloudMold Dubbo service: " + service);
                        }
                    });
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load " + CloudMoldRpcConstants.SERVICES_RESOURCE, ex);
        }
        if (services.isEmpty()) {
            throw new IllegalStateException("CloudMold Dubbo service allowlist is empty");
        }
        return Collections.unmodifiableSet(services);
    }
}
