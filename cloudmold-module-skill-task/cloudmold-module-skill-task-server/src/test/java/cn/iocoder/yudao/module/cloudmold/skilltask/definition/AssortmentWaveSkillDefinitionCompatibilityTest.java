package cn.iocoder.yudao.module.cloudmold.skilltask.definition;

import cn.iocoder.yudao.module.cloudmold.executor.CloudMoldCapabilityCatalog;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcProperties;
import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssortmentWaveSkillDefinitionCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validatesAssortmentWaveSkillAgainstGovernedCapabilityCatalog() throws IOException {
        Path skillRoot = findWorkspaceSkillRoot();
        Assumptions.assumeTrue(skillRoot != null,
                "Sibling useful-scripts registry is not available in this checkout");

        SkillTaskDefinitionRegistry registry = new SkillTaskDefinitionRegistry(
                objectMapper,
                new SkillTaskProperties(),
                new CloudMoldCapabilityCatalog(new CloudMoldRpcProperties()));

        SkillTaskDefinition definition = read(skillRoot.resolve("cloudmold-assortment-wave/skill-task.json"));
        var indexed = registry.validateAndIndex(List.of(definition));
        SkillTaskDefinition actual = indexed.get("skill.cloudmold.catalog.assortment-wave-readiness.v1@1.0.0");

        assertThat(actual).isNotNull();
        assertThat(actual.getRiskLevel()).isEqualTo("R1");
        assertThat(actual.getSteps()).hasSize(1);
        assertThat(actual.getSteps().get(0).getCapabilityId())
                .isEqualTo("capability.cloudmold.catalog.assortment-wave-readiness-workflow-query.inspect-wave.v1");
    }

    private SkillTaskDefinition read(Path path) throws IOException {
        return objectMapper.readValue(path.toFile(), SkillTaskDefinition.class);
    }

    private static Path findWorkspaceSkillRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            for (Path candidate : List.of(current.resolve("useful-scripts/skills"),
                    current.resolveSibling("useful-scripts").resolve("skills"))) {
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
            }
            current = current.getParent();
        }
        return null;
    }
}
