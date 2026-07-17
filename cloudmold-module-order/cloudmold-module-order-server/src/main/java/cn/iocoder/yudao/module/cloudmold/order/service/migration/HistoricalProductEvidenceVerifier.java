package cn.iocoder.yudao.module.cloudmold.order.service.migration;

public interface HistoricalProductEvidenceVerifier {

    EvidenceVerification verify(String evidenceUri, String expectedSha256);

    record EvidenceVerification(String verifierVersion, long contentLength) {
    }
}
