package cn.iocoder.yudao.module.cloudmold.executor;

import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldDubboServiceAllowlist;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class CloudMoldCapabilityCatalogTest {

    @Test
    void shouldCreateAUniqueGovernedCapabilityForEveryPublicMethod() {
        CloudMoldRpcProperties properties = new CloudMoldRpcProperties();
        CloudMoldCapabilityCatalog catalog = new CloudMoldCapabilityCatalog(properties);

        assertThat(CloudMoldDubboServiceAllowlist.load()).isNotEmpty();
        assertThat(CloudMoldDubboServiceAllowlist.load()).allSatisfy(interfaceName -> {
            try {
                Class<?> type = Class.forName(interfaceName);
                assertThat(type.isInterface()).as(interfaceName).isTrue();
                assertThat(Modifier.isPublic(type.getModifiers())).as(interfaceName).isTrue();
            } catch (ClassNotFoundException ex) {
                throw new AssertionError("Missing allowlisted interface " + interfaceName, ex);
            }
        });
        assertThat(catalog.all()).isNotEmpty();
        assertThat(new HashSet<>(catalog.all().stream().map(CapabilityDescriptor::capabilityId).toList()))
                .hasSameSizeAs(catalog.all());
        assertThat(catalog.all()).allSatisfy(capability -> {
            assertThat(capability.group()).isEqualTo("cloudmold-internal");
            assertThat(capability.version()).isEqualTo("1.0.0");
            assertThat(capability.timeoutMillis()).isPositive();
        });
    }

    @Test
    void shouldClassifyCommandsAsWritesAndQueriesAsReads() {
        CloudMoldCapabilityCatalog catalog = new CloudMoldCapabilityCatalog(new CloudMoldRpcProperties());

        assertThat(catalog.all()).anySatisfy(capability -> {
            assertThat(capability.interfaceName()).contains("CommandApi");
            assertThat(capability.operationType()).isEqualTo(CapabilityOperationType.WRITE);
        });
        assertThat(catalog.all()).anySatisfy(capability -> {
            assertThat(capability.interfaceName()).contains("QueryApi");
            assertThat(capability.operationType()).isEqualTo(CapabilityOperationType.READ);
        });
        assertThat(catalog.all()).anySatisfy(capability -> {
            assertThat(capability.interfaceName()).isEqualTo(
                    "cn.iocoder.yudao.module.cloudmold.dreamplant.api.DreamPlantCommandApi");
            assertThat(capability.operationType()).isEqualTo(CapabilityOperationType.WRITE);
        });
        assertThat(catalog.all()).anySatisfy(capability -> {
            assertThat(capability.interfaceName()).isEqualTo(
                    "cn.iocoder.yudao.module.cloudmold.dreamplant.api.DreamPlantQueryApi");
            assertThat(capability.operationType()).isEqualTo(CapabilityOperationType.READ);
        });
        assertThat(catalog.all()).anySatisfy(capability -> {
            assertThat(capability.interfaceName()).isEqualTo(
                    "cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoProcurementPromiseApi");
            assertThat(capability.methodName()).isEqualTo("getPurchasePromise");
            assertThat(capability.operationType()).isEqualTo(CapabilityOperationType.READ);
        });
        assertThat(catalog.all()).anySatisfy(capability -> {
            assertThat(capability.interfaceName()).isEqualTo(
                    "cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoProcurementPromiseApi");
            assertThat(capability.methodName()).isEqualTo("savePurchasePromise");
            assertThat(capability.operationType()).isEqualTo(CapabilityOperationType.WRITE);
        });
    }

    @Test
    void shouldGovernEveryCloudMoldPublicApiOnTheExecutorClasspath() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        CachingMetadataReaderFactory metadata = new CachingMetadataReaderFactory(resolver);
        Set<String> discovered = new TreeSet<>();
        for (Resource resource : resolver.getResources(
                "classpath*:cn/iocoder/yudao/module/cloudmold/**/*Api.class")) {
            String className = metadata.getMetadataReader(resource).getClassMetadata().getClassName();
            try {
                Class<?> type = Class.forName(className);
                if (type.isInterface() && Modifier.isPublic(type.getModifiers())
                        && Arrays.stream(type.getMethods()).anyMatch(method -> Modifier.isPublic(method.getModifiers()))) {
                    discovered.add(className);
                }
            } catch (ClassNotFoundException ex) {
                throw new AssertionError("Cannot inspect CloudMold API contract " + className, ex);
            }
        }

        assertThat(discovered).isEqualTo(CloudMoldDubboServiceAllowlist.load());
    }

    @Test
    void shouldTrustOnlyPublishedCloudMoldApiPackagesForDeserialization() throws IOException {
        ClassPathResource resource = new ClassPathResource("security/serialize.allowlist");
        List<String> trustedPrefixes;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8))) {
            trustedPrefixes = reader.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        }

        assertThat(trustedPrefixes).doesNotContain("cn.iocoder.yudao.module.cloudmold");
        assertThat(CloudMoldDubboServiceAllowlist.load()).allSatisfy(interfaceName ->
                assertThat(trustedPrefixes).anySatisfy(prefix ->
                        assertThat(interfaceName).startsWith(prefix + ".")));
    }
}
