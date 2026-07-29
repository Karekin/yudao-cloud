package cn.iocoder.yudao.module.cloudmold.catalog.api.workflow;

/**
 * Governed read contract for the assortment-wave planning readiness workflow.
 */
public interface AssortmentWaveReadinessWorkflowQueryApi {

    AssortmentWaveReadinessResult inspectWave(AssortmentWaveReadinessRequest request);
}
