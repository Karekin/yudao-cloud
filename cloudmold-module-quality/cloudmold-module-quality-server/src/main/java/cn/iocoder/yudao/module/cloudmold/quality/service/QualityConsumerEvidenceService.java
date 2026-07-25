package cn.iocoder.yudao.module.cloudmold.quality.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceApi;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceView;
import cn.iocoder.yudao.module.cloudmold.quality.dal.mysql.QualityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class QualityConsumerEvidenceService implements QualityConsumerEvidenceApi {

    private final QualityMapper mapper;

    @Override
    public QualityConsumerEvidenceView getLatestBySku(String canonicalSkuId) {
        if (!StringUtils.hasText(canonicalSkuId)) {
            throw new IllegalArgumentException("canonicalSkuId is required");
        }
        QualityConsumerEvidenceView evidence = mapper.selectLatestConsumerEvidence(
                TenantContextHolder.getRequiredTenantId(), canonicalSkuId.trim());
        return evidence != null ? evidence : QualityConsumerEvidenceView.builder()
                .canonicalSkuId(canonicalSkuId.trim()).status("UNVERIFIED").build();
    }
}
