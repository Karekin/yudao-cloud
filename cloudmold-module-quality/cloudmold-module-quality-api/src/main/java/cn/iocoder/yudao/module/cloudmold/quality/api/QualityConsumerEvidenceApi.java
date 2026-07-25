package cn.iocoder.yudao.module.cloudmold.quality.api;

public interface QualityConsumerEvidenceApi {
    QualityConsumerEvidenceView getLatestBySku(String canonicalSkuId);
}
