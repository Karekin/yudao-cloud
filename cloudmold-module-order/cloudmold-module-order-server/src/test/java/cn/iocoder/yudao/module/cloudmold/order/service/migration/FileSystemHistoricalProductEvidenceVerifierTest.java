package cn.iocoder.yudao.module.cloudmold.order.service.migration;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemHistoricalProductEvidenceVerifierTest {

    @TempDir
    Path evidenceRoot;

    @Test
    void verifiesContentAddressedImmutableEvidence() throws Exception {
        byte[] content = "ORDER_ITEM_ACCEPTED_PRODUCT_SNAPSHOT_V1\u001f1\u001f111\u001f633\u001f1"
                .getBytes(StandardCharsets.UTF_8);
        String hash = DigestUtil.sha256Hex(content);
        Files.write(evidenceRoot.resolve(hash), content);

        HistoricalProductEvidenceVerifier.EvidenceVerification result =
                new FileSystemHistoricalProductEvidenceVerifier(evidenceRoot.toString())
                        .verify("evidence://sha256/" + hash, hash);

        assertThat(result.verifierVersion())
                .isEqualTo(FileSystemHistoricalProductEvidenceVerifier.VERIFIER_VERSION);
        assertThat(result.contentLength()).isEqualTo(content.length);
    }

    @Test
    void rejectsUriWhoseContentAddressDiffersFromExpectedHash() {
        String expected = "a".repeat(64);

        assertThatThrownBy(() -> new FileSystemHistoricalProductEvidenceVerifier(evidenceRoot.toString())
                .verify("evidence://sha256/" + "b".repeat(64), expected))
                .isInstanceOf(ServiceException.class).hasMessageContaining("content address");
    }

    @Test
    void rejectsObjectWhoseBytesDoNotMatchContentAddress() throws Exception {
        String expected = "a".repeat(64);
        Files.writeString(evidenceRoot.resolve(expected), "different content");

        assertThatThrownBy(() -> new FileSystemHistoricalProductEvidenceVerifier(evidenceRoot.toString())
                .verify("evidence://sha256/" + expected, expected))
                .isInstanceOf(ServiceException.class).hasMessageContaining("content hash mismatch");
    }

    @Test
    void failsClosedWhenAuthenticEvidenceRootIsNotConfigured() {
        String expected = "a".repeat(64);

        assertThatThrownBy(() -> new FileSystemHistoricalProductEvidenceVerifier("")
                .verify("evidence://sha256/" + expected, expected))
                .isInstanceOf(ServiceException.class).hasMessageContaining("root is not configured");
    }
}
