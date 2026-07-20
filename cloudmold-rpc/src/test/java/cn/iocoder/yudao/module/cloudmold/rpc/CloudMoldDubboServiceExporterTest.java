package cn.iocoder.yudao.module.cloudmold.rpc;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudMoldDubboServiceExporterTest {

    @Test
    void defaultsToTheCompleteGovernedAllowlist() {
        Set<String> allowlist = new LinkedHashSet<>(Set.of("a.Api", "b.Api"));

        assertThat(CloudMoldDubboServiceExporter.selectServiceInterfaces(allowlist, Set.of()))
                .isSameAs(allowlist);
    }

    @Test
    void limitsAStandaloneProviderToItsDeclaredInterfaces() {
        Set<String> selected = CloudMoldDubboServiceExporter.selectServiceInterfaces(
                Set.of("a.Api", "b.Api"), Set.of("b.Api"));

        assertThat(selected).containsExactly("b.Api");
    }

    @Test
    void rejectsInterfacesOutsideTheGovernedCatalog() {
        assertThatThrownBy(() -> CloudMoldDubboServiceExporter.selectServiceInterfaces(
                Set.of("a.Api"), Set.of("unsafe.Api")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-allowlisted")
                .hasMessageContaining("unsafe.Api");
    }

    @Test
    void neverExportsAnExternallyProvidedInterfaceEvenWhenAClientProxyBeanExists() {
        assertThat(CloudMoldDubboServiceExporter.isExternallyProvided(
                "external.SkillTaskApi", Set.of("external.SkillTaskApi"))).isTrue();
        assertThat(CloudMoldDubboServiceExporter.isExternallyProvided(
                "local.AgentControlApi", Set.of("external.SkillTaskApi"))).isFalse();
    }
}
