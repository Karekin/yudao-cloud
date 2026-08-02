package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ProcurementSourcingCutoverArchitectureTest {

    private static final Path BASE_DIR = Path.of(System.getProperty("basedir"));

    @Test
    void procurementSourcingHasNoLegacySupplierOrCompatibilitySurface() throws IOException {
        String factory = source("RotatingBusinessScenarioInputFactory.java");
        String routeCatalog = source("ManagedWorkflowOutboxRouteCatalog.java");
        String pom = Files.readString(BASE_DIR.resolve("pom.xml"));
        String sourcingSlice = factory.substring(
                factory.indexOf("private static ObjectNode procurementSourcing("),
                factory.indexOf("private static PurchaseRequisitionCommand.LineDefinition"));

        assertThat(sourcingSlice)
                .doesNotContain("Supplier" + "Sourcing", "supplier" + "_ref",
                        "supplier" + "Ref", "compat", "fallback", "projection");
        assertThat(routeCatalog)
                .doesNotContain("Supplier" + "Sourcing", "supplier" + "_ref",
                        "supplier" + "Ref", "compat", "fallback", "projection");
        assertThat(pom).doesNotContain("cloudmold-module-" + "supplier-api");

        Path repositoryRoot = BASE_DIR.getParent().getParent();
        String skillTaskSources = sourceTree(repositoryRoot.resolve(
                "cloudmold-module-skill-task/cloudmold-module-skill-task-server/src/main/java"));
        assertThat(factory + routeCatalog + skillTaskSources)
                .doesNotContain("skill.cloudmold." + "supplier.sourcing",
                        "Supplier" + "Sourcing", "supplier" + "_ref", "supplier" + "Ref",
                        "supplier-" + "sourcing", "supplier_" + "sourcing");
    }

    private static String source(String fileName) throws IOException {
        return Files.readString(BASE_DIR.resolve(
                "src/main/java/cn/iocoder/yudao/module/cloudmold/aioperations/temporal/" + fileName));
    }

    private static String sourceTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            StringBuilder result = new StringBuilder();
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                result.append(Files.readString(path));
            }
            return result.toString();
        }
    }
}
