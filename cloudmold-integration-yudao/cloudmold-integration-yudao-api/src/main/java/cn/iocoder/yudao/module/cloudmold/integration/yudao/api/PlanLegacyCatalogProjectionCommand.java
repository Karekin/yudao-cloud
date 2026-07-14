package cn.iocoder.yudao.module.cloudmold.integration.yudao.api;

import lombok.Data;

import java.util.Set;

@Data
public class PlanLegacyCatalogProjectionCommand {
    private String canonicalSkuId;
    private Set<LegacyCatalogTargetSystem> targets;
}
