package cn.iocoder.yudao.module.cloudmold.risk.api;

import java.time.Instant;

public interface RiskQueryApi {
    RiskView getPolicy(String policyId);
    RiskView getIntelligenceEventTaxonomy(String taxonomyId);
    IntelligenceEventTaxonomyReference validateIntelligenceEventLevel(
            String taxonomyId, Long definitionVersion, String eventCode, String intelligenceLevel, Instant observedAt);
    RiskView getCluster(String clusterId);
    RiskView getReviewCase(String caseId);
}
