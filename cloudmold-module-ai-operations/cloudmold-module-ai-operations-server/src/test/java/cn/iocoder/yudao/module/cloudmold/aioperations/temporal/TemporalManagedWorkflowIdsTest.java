package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemporalManagedWorkflowIdsTest {

    @Test
    void shouldDeriveStableDeterministicIdentifiers() {
        TemporalManagedRunRequest request = TemporalManagedRunRequest.builder()
                .tenantId(162L)
                .scheduleId("cloudmold-t162-auto-shelf-hourly")
                .skillId("skill.cloudmold.commerce.catalog-matrix.v1")
                .skillVersion("1.0.0")
                .build();

        assertThat(TemporalManagedWorkflowIds.scheduleWorkflowId(request))
                .isEqualTo(TemporalManagedWorkflowIds.scheduleWorkflowId(request));
        assertThat(TemporalManagedWorkflowIds.workOrderId("run-1"))
                .isEqualTo("twr-4e65d3fbe8ad6535681b021b");
        assertThat(TemporalManagedWorkflowIds.approvalId("run-1"))
                .isEqualTo("tap-4e65d3fbe8ad6535681b021b");
        assertThat(TemporalManagedWorkflowIds.managedRunId("run-1"))
                .isEqualTo("tsr-4e65d3fbe8ad6535681b021b");
    }
}
