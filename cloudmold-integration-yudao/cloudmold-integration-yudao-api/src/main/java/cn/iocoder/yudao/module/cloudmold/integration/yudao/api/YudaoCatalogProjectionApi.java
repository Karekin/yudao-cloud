package cn.iocoder.yudao.module.cloudmold.integration.yudao.api;

public interface YudaoCatalogProjectionApi {
    LegacyCatalogProjectionResult plan(PlanLegacyCatalogProjectionCommand command);
}
