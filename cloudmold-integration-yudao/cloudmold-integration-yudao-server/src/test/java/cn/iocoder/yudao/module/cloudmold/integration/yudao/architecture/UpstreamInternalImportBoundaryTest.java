package cn.iocoder.yudao.module.cloudmold.integration.yudao.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class UpstreamInternalImportBoundaryTest {

    private static final Pattern UPSTREAM_INTERNAL_IMPORT = Pattern.compile(
            "^import cn\\.iocoder\\.yudao\\.module\\.(erp|wms|mes)\\.(controller\\.admin|dal\\.dataobject|service)\\..*;$");
    private static final String ALLOWED_PACKAGE_PREFIX =
            "package cn.iocoder.yudao.module.cloudmold.integration.yudao.bridge";

    @Test
    void shouldRestrictUpstreamInternalImportsToBridgePackages() throws IOException {
        Path root = Path.of("src/main/java/cn/iocoder/yudao/module/cloudmold/integration/yudao");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> inspect(path, violations));
        }
        assertThat(violations).isEmpty();
    }

    private static void inspect(Path path, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(path);
            boolean importsUpstreamInternal = lines.stream()
                    .map(String::trim)
                    .anyMatch(line -> UPSTREAM_INTERNAL_IMPORT.matcher(line).matches());
            if (!importsUpstreamInternal) {
                return;
            }
            boolean inBridgePackage = lines.stream()
                    .map(String::trim)
                    .anyMatch(line -> line.startsWith(ALLOWED_PACKAGE_PREFIX));
            if (!inBridgePackage) {
                violations.add(path.toString());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to inspect " + path, ex);
        }
    }
}
