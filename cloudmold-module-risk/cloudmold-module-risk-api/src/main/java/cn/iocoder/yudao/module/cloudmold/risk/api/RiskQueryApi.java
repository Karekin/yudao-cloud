package cn.iocoder.yudao.module.cloudmold.risk.api;

public interface RiskQueryApi {
    RiskView getPolicy(String policyId);
    RiskView getCluster(String clusterId);
    RiskView getReviewCase(String caseId);
}
