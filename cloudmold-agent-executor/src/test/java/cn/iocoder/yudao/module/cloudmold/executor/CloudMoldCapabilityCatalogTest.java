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
            assertThat(capability.capabilityId()).isEqualTo(
                    "capability.cloudmold.agentcontrol.agent-approval-review.get-review-context.v1");
            assertThat(capability.operationType()).isEqualTo(CapabilityOperationType.READ);
        });
        assertThat(catalog.all()).anySatisfy(capability -> {
            assertThat(capability.capabilityId()).isEqualTo(
                    "capability.cloudmold.agentcontrol.agent-approval-review.submit-decision.v1");
            assertThat(capability.operationType()).isEqualTo(CapabilityOperationType.WRITE);
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
        assertThat(catalog.all()).anySatisfy(capability -> {
            assertThat(capability.capabilityId()).isEqualTo(
                    "capability.cloudmold.supplyplanning.supply-planning-command.execute.sig-1ca9b1370b9e.v1");
            assertThat(capability.interfaceName()).isEqualTo(
                    "cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SupplyPlanningCommandApi");
            assertThat(capability.operationType()).isEqualTo(CapabilityOperationType.WRITE);
        });
        assertThat(catalog.all()).anySatisfy(capability -> {
            assertThat(capability.capabilityId()).isEqualTo(
                    "capability.cloudmold.supplyplanning.supply-planning-query.require-replenishment-business-stage.v1");
            assertThat(capability.interfaceName()).isEqualTo(
                    "cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SupplyPlanningQueryApi");
            assertThat(capability.operationType()).isEqualTo(CapabilityOperationType.READ);
        });
        assertThat(catalog.all()).anySatisfy(capability -> {
            assertThat(capability.capabilityId()).isEqualTo(
                    "capability.cloudmold.catalog.assortment-planning-command.execute.v1");
            assertThat(capability.interfaceName()).isEqualTo(
                    "cn.iocoder.yudao.module.cloudmold.catalog.api.AssortmentPlanningCommandApi");
            assertThat(capability.operationType()).isEqualTo(CapabilityOperationType.WRITE);
        });
        assertThat(catalog.all()).anySatisfy(capability -> {
            assertThat(capability.capabilityId()).isEqualTo(
                    "capability.cloudmold.catalog.assortment-planning-query.get-workflow.v1");
            assertThat(capability.interfaceName()).isEqualTo(
                    "cn.iocoder.yudao.module.cloudmold.catalog.api.AssortmentPlanningQueryApi");
            assertThat(capability.operationType()).isEqualTo(CapabilityOperationType.READ);
        });
        assertThat(catalog.all()).anySatisfy(capability -> {
            assertThat(capability.capabilityId()).isEqualTo(
                    "capability.cloudmold.crossborder.cross-border-command.execute.v1");
            assertThat(capability.interfaceName()).isEqualTo(
                    "cn.iocoder.yudao.module.cloudmold.crossborder.api.CrossBorderCommandApi");
            assertThat(capability.operationType()).isEqualTo(CapabilityOperationType.WRITE);
        });
        assertThat(catalog.all()).anySatisfy(capability -> {
            assertThat(capability.capabilityId()).isEqualTo(
                    "capability.cloudmold.crossborder.cross-border-query.get.v1");
            assertThat(capability.interfaceName()).isEqualTo(
                    "cn.iocoder.yudao.module.cloudmold.crossborder.api.CrossBorderQueryApi");
            assertThat(capability.operationType()).isEqualTo(CapabilityOperationType.READ);
        });
        assertThat(catalog.all()).anySatisfy(capability -> {
            assertThat(capability.capabilityId()).isEqualTo(
                    "capability.cloudmold.crossborder.bonded-customs-command.execute.v1");
            assertThat(capability.interfaceName()).isEqualTo(
                    "cn.iocoder.yudao.module.cloudmold.crossborder.api.bonded.BondedCustomsCommandApi");
            assertThat(capability.operationType()).isEqualTo(CapabilityOperationType.WRITE);
        });
        assertThat(catalog.all()).anySatisfy(capability -> {
            assertThat(capability.capabilityId()).isEqualTo(
                    "capability.cloudmold.crossborder.bonded-customs-query.get.v1");
            assertThat(capability.interfaceName()).isEqualTo(
                    "cn.iocoder.yudao.module.cloudmold.crossborder.api.bonded.BondedCustomsQueryApi");
            assertThat(capability.operationType()).isEqualTo(CapabilityOperationType.READ);
        });
        assertThat(catalog.all()).anySatisfy(capability -> {
            assertThat(capability.capabilityId()).isEqualTo(
                    "capability.cloudmold.partnermarketing.partner-marketing-command.execute.v1");
            assertThat(capability.interfaceName()).isEqualTo(
                    "cn.iocoder.yudao.module.cloudmold.partnermarketing.api.PartnerMarketingCommandApi");
            assertThat(capability.operationType()).isEqualTo(CapabilityOperationType.WRITE);
        });
        assertThat(catalog.all()).anySatisfy(capability -> {
            assertThat(capability.capabilityId()).isEqualTo(
                    "capability.cloudmold.partnermarketing.partner-marketing-query.get-workflow.v1");
            assertThat(capability.interfaceName()).isEqualTo(
                    "cn.iocoder.yudao.module.cloudmold.partnermarketing.api.PartnerMarketingQueryApi");
            assertThat(capability.operationType()).isEqualTo(CapabilityOperationType.READ);
        });
        assertThat(catalog.all()).anySatisfy(capability -> {
            assertThat(capability.capabilityId()).isEqualTo(
                    "capability.cloudmold.integration.yudao-mes-command.create-feedback.v1");
            assertThat(capability.interfaceName()).isEqualTo(
                    "cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoMesCommandApi");
            assertThat(capability.operationType()).isEqualTo(CapabilityOperationType.WRITE);
        });
        assertThat(catalog.all()).anySatisfy(capability -> {
            assertThat(capability.capabilityId()).isEqualTo(
                    "capability.cloudmold.integration.yudao-legacy-operations-query.get-mes-production-execution.v1");
            assertThat(capability.interfaceName()).isEqualTo(
                    "cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoLegacyOperationsQueryApi");
            assertThat(capability.operationType()).isEqualTo(CapabilityOperationType.READ);
        });
    }

    @Test
    void shouldGovernEveryCloudMoldPublicApiOnTheExecutorClasspath() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        CachingMetadataReaderFactory metadata = new CachingMetadataReaderFactory(resolver);
        List<String> trustedPrefixes = publishedApiPrefixes();
        Set<String> discovered = new TreeSet<>();
        for (Resource resource : resolver.getResources(
                "classpath*:cn/iocoder/yudao/module/cloudmold/**/*Api.class")) {
            String className = metadata.getMetadataReader(resource).getClassMetadata().getClassName();
            if (trustedPrefixes.stream().noneMatch(prefix -> className.startsWith(prefix + "."))) {
                continue;
            }
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
        List<String> trustedPrefixes = publishedApiPrefixes();

        assertThat(trustedPrefixes).doesNotContain("cn.iocoder.yudao.module.cloudmold");
        assertThat(CloudMoldDubboServiceAllowlist.load()).allSatisfy(interfaceName ->
                assertThat(trustedPrefixes).anySatisfy(prefix ->
                        assertThat(interfaceName).startsWith(prefix + ".")));
    }

    private static List<String> publishedApiPrefixes() throws IOException {
        ClassPathResource resource = new ClassPathResource("security/serialize.allowlist");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        }
    }
}
