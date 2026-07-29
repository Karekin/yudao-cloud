package cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.mysql;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SupplyPlanningMapperSqlTest {

    @Test
    void readyProposalQueriesRevalidateRecommendationAndConversionState() throws Exception {
        Method list = SupplyPlanningMapper.class.getMethod(
                "selectReadyReplenishmentExecutionProposals", Long.class, int.class);
        Method require = SupplyPlanningMapper.class.getMethod(
                "selectReadyReplenishmentExecutionProposal", Long.class, String.class);

        assertLifecycleGuards(sql(list));
        assertLifecycleGuards(sql(require));
        assertThat(sql(list)).contains(
                "ORDER BY proposal.proposed_at, proposal.proposal_id",
                "LIMIT #{limit}");
        assertThat(sql(require)).contains("proposal.proposal_id=#{proposalId}");
    }

    private static void assertLifecycleGuards(String sql) {
        assertThat(sql).contains(
                "recommendation.status='APPROVED'",
                "recommendation.version=proposal.expected_recommendation_version",
                "proposal.status='READY'",
                "conversion.conversion_id IS NULL",
                "proposal.tenant_id=#{tenantId}");
    }

    private static String sql(Method method) {
        return String.join("\n", method.getAnnotation(Select.class).value());
    }
}
