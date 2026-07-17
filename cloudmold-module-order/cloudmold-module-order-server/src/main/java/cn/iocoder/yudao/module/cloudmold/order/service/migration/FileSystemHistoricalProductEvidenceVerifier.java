package cn.iocoder.yudao.module.cloudmold.order.service.migration;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;

@Component
public class FileSystemHistoricalProductEvidenceVerifier implements HistoricalProductEvidenceVerifier {

    static final String VERIFIER_VERSION = "filesystem-content-addressed-sha256-v1";
    static final long MAX_EVIDENCE_BYTES = 64 * 1024;

    private final String configuredRoot;

    public FileSystemHistoricalProductEvidenceVerifier(
            @Value("${cloudmold.order.migration.historical-product-evidence-root:}") String configuredRoot) {
        this.configuredRoot = configuredRoot == null ? "" : configuredRoot.trim();
    }

    @Override
    public EvidenceVerification verify(String evidenceUri, String expectedSha256) {
        String expected = requireSha256(expectedSha256);
        URI uri = parseUri(evidenceUri);
        require("evidence".equals(uri.getScheme()) && "sha256".equals(uri.getHost())
                        && uri.getUserInfo() == null && uri.getPort() == -1
                        && uri.getQuery() == null && uri.getFragment() == null,
                "sourceEvidenceUri must use evidence://sha256/<content-sha256>");
        String pathHash = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "")
                .toLowerCase(Locale.ROOT);
        require(pathHash.equals(expected) && pathHash.matches("[0-9a-f]{64}"),
                "sourceEvidenceUri content address must equal historicalProductSnapshotHash");
        require(!configuredRoot.isBlank(), "historical product evidence root is not configured");
        try {
            Path root = Path.of(configuredRoot).toAbsolutePath().normalize();
            require(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(root),
                    "historical product evidence root is unavailable or unsafe");
            Path realRoot = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path candidate = realRoot.resolve(pathHash).normalize();
            require(candidate.getParent() != null && candidate.getParent().equals(realRoot)
                            && Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                            && !Files.isSymbolicLink(candidate),
                    "historical product evidence object does not exist or is unsafe");
            long contentLength = Files.size(candidate);
            require(contentLength > 0 && contentLength <= MAX_EVIDENCE_BYTES,
                    "historical product evidence object size is invalid");
            byte[] content = Files.readAllBytes(candidate);
            require(content.length == contentLength && DigestUtil.sha256Hex(content).equals(expected),
                    "historical product evidence content hash mismatch");
            return new EvidenceVerification(VERIFIER_VERSION, contentLength);
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw new ServiceException(400, "historical product evidence verification failed");
        }
    }

    private static URI parseUri(String value) {
        require(value != null && !value.isBlank(), "sourceEvidenceUri is required");
        try {
            return new URI(value.trim());
        } catch (URISyntaxException ex) {
            throw new ServiceException(400, "sourceEvidenceUri is invalid");
        }
    }

    private static String requireSha256(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        require(normalized.matches("[0-9a-f]{64}"), "historicalProductSnapshotHash must be lowercase SHA-256");
        return normalized;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new ServiceException(400, message);
        }
    }
}
