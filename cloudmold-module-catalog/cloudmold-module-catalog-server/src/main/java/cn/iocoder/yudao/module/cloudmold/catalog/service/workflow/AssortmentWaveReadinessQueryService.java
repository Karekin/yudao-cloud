package cn.iocoder.yudao.module.cloudmold.catalog.service.workflow;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.workflow.AssortmentWaveReadinessQueryPort;
import cn.iocoder.yudao.module.cloudmold.catalog.api.workflow.AssortmentWaveReadinessRequest;
import cn.iocoder.yudao.module.cloudmold.catalog.api.workflow.AssortmentWaveReadinessResult;
import cn.iocoder.yudao.module.cloudmold.catalog.api.workflow.AssortmentWaveReadinessResult.Artifact;
import cn.iocoder.yudao.module.cloudmold.catalog.api.workflow.AssortmentWaveReadinessResult.Blocker;
import cn.iocoder.yudao.module.cloudmold.catalog.api.workflow.AssortmentWaveReadinessResult.CatalogSnapshot;
import cn.iocoder.yudao.module.cloudmold.catalog.api.workflow.AssortmentWaveReadinessResult.Status;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.CatalogQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AssortmentWaveReadinessQueryService implements AssortmentWaveReadinessQueryPort {

    private static final Set<String> SEASONS = Set.of("SPRING", "SUMMER", "AUTUMN", "WINTER", "ALL_SEASON");
    private static final String WORKFLOW_TYPE = "AssortmentWaveReadinessWorkflow";

    private final CatalogQueryMapper queryMapper;

    @Override
    public AssortmentWaveReadinessResult inspectWave(AssortmentWaveReadinessRequest request) {
        NormalizedRequest normalized = normalize(request);
        CatalogWaveAggregateRow row = queryMapper.selectWaveAggregate(TenantContextHolder.getRequiredTenantId(),
                normalized.planningYear(), normalized.seasonCode(), normalized.waveCode());
        CatalogSnapshot snapshot = snapshot(normalized, row);
        List<Blocker> blockers = blockers(snapshot);
        List<String> nextActions = nextActions(snapshot, blockers);
        boolean ready = blockers.isEmpty();
        return AssortmentWaveReadinessResult.builder()
                .workflowType(WORKFLOW_TYPE)
                .workflowInstanceKey(instanceKey(normalized))
                .status(ready ? Status.READY : snapshot.getStyleCount() == 0 ? Status.NEEDS_CATALOG : Status.WAITING)
                .phase(snapshot.getStyleCount() == 0 ? "CATALOG_BASELINE_MISSING" : "CATALOG_BASELINE_READY")
                .terminal(ready)
                .actionRequired(!ready)
                .summary(summary(snapshot, blockers))
                .catalogSnapshot(snapshot)
                .blockers(blockers)
                .nextActions(nextActions)
                .artifacts(List.of(Artifact.builder()
                        .type("CATALOG_WAVE_SNAPSHOT")
                        .id(instanceKey(normalized))
                        .label(label(normalized))
                        .status(snapshot.getStyleCount() == 0 ? "EMPTY" : "BASELINED")
                        .build()))
                .build();
    }

    private static CatalogSnapshot snapshot(NormalizedRequest request, CatalogWaveAggregateRow row) {
        CatalogWaveAggregateRow aggregate = row == null ? new CatalogWaveAggregateRow() : row;
        return CatalogSnapshot.builder()
                .planningYear(request.planningYear())
                .seasonCode(request.seasonCode())
                .waveCode(request.waveCode())
                .styleCount(orZero(aggregate.getStyleCount()))
                .activeStyleCount(orZero(aggregate.getActiveStyleCount()))
                .spuCount(orZero(aggregate.getSpuCount()))
                .activeSpuCount(orZero(aggregate.getActiveSpuCount()))
                .skuCount(orZero(aggregate.getSkuCount()))
                .activeSkuCount(orZero(aggregate.getActiveSkuCount()))
                .lastCatalogUpdatedAt(aggregate.getLastCatalogUpdatedAt())
                .build();
    }

    private static List<Blocker> blockers(CatalogSnapshot snapshot) {
        List<Blocker> blockers = new ArrayList<>();
        if (snapshot.getStyleCount() == 0) {
            blockers.add(blocker("CATALOG_SCOPE_EMPTY", "波段内没有 Catalog 款式底稿", "CRITICAL",
                    "当前波段在 Catalog 权威数据里没有任何 Style/SPU/SKU，连波段底稿都还未建立。",
                    "Catalog Style / SPU / SKU", "先完成至少一版款式、SPU、SKU 的权威建档，再进入波段企划。"));
        } else if (snapshot.getActiveSkuCount() == 0) {
            blockers.add(blocker("CATALOG_SKU_NOT_ACTIVE", "波段 SKU 仍未激活", "HIGH",
                    "当前波段已有建档，但没有 ACTIVE SKU，无法把企划拆成可经营的款色码组合。",
                    "Catalog ACTIVE SKU", "补齐 SPU 审批和 SKU 激活，形成可经营的波段商品池。"));
        }
        blockers.add(blocker("TREND_SIGNAL_MISSING", "趋势输入缺失", "CRITICAL",
                "缺少趋势主题、消费信号或竞品趋势结论，不能判断这一波该做什么风格和主题。",
                "Trend Brief / 买手趋势结论", "接入趋势洞察结论，并与当前波段款式池建立明确映射。"));
        blockers.add(blocker("PRICE_BAND_MISSING", "价格带策略缺失", "CRITICAL",
                "缺少价格带结构与渠道定价策略，无法判断当前波段的款式结构是否匹配经营目标。",
                "Price Band Strategy", "补录目标价格带、主推价格段和渠道价盘。"));
        blockers.add(blocker("TARGET_STYLE_COUNT_MISSING", "目标款量缺失", "CRITICAL",
                "只有现有款量，没有目标款量，无法判断当前波段是缺款、超款还是结构失衡。",
                "Wave Style Count Target", "补录该波段目标款量和关键品类目标占比。"));
        blockers.add(blocker("GROSS_MARGIN_TARGET_MISSING", "毛利目标缺失", "CRITICAL",
                "缺少目标毛利线，无法判断波段定价、折扣空间和品类结构是否健康。",
                "Gross Margin Target", "补录波段毛利率目标与最低可接受利润线。"));
        blockers.add(blocker("SUPPLY_PLAN_LINK_MISSING", "Supply Plan 关联缺失", "CRITICAL",
                "当前 Catalog 款色码没有和 Supply Plan 形成明确关联，无法验证交付能力与上市节奏。",
                "Supply Plan Link", "把波段款式池映射到 Supply Plan，补齐产能、交期和到货节奏。"));
        return List.copyOf(blockers);
    }

    private static List<String> nextActions(CatalogSnapshot snapshot, List<Blocker> blockers) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        if (snapshot.getStyleCount() == 0) {
            actions.add("先在 Catalog 建立该波段的 Style / SPU / SKU 权威底稿。");
        }
        if (snapshot.getStyleCount() > 0 && snapshot.getActiveSkuCount() == 0) {
            actions.add("推进 SPU 提交审批和 SKU 激活，形成可经营的 ACTIVE SKU 池。");
        }
        blockers.stream().map(Blocker::getUnblockAction).forEach(actions::add);
        return List.copyOf(actions);
    }

    private static String summary(CatalogSnapshot snapshot, List<Blocker> blockers) {
        String catalogPart = "Catalog 已覆盖 " + snapshot.getStyleCount() + " 个 Style / " + snapshot.getSpuCount()
                + " 个 SPU / " + snapshot.getSkuCount() + " 个 SKU";
        if (snapshot.getStyleCount() == 0) {
            return catalogPart + "，当前波段尚无权威底稿，不能称为波段企划案。";
        }
        return catalogPart + "，但仍缺少趋势、价格带、目标款量、毛利目标与 Supply Plan 关联等 "
                + blockers.size() + " 个关键 blocker，不能称为完整波段企划案。";
    }

    private static String label(NormalizedRequest request) {
        return request.planningYear() + " " + request.seasonCode() + " " + request.waveCode() + " 波段企划准备度";
    }

    private static String instanceKey(NormalizedRequest request) {
        return request.planningYear() + ":" + request.seasonCode() + ":" + request.waveCode();
    }

    private static Blocker blocker(String code, String title, String severity, String summary,
                                   String requiredSourceOfTruth, String unblockAction) {
        return Blocker.builder()
                .code(code)
                .title(title)
                .severity(severity)
                .summary(summary)
                .requiredSourceOfTruth(requiredSourceOfTruth)
                .unblockAction(unblockAction)
                .build();
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static NormalizedRequest normalize(AssortmentWaveReadinessRequest request) {
        require(request != null, "assortment wave readiness request is required");
        require(request.getPlanningYear() != null && request.getPlanningYear() >= 2000 && request.getPlanningYear() <= 2100,
                "planningYear must be between 2000 and 2100");
        String seasonCode = upper(request.getSeasonCode());
        require(SEASONS.contains(seasonCode), "seasonCode is invalid");
        require(StringUtils.hasText(request.getWaveCode()), "waveCode is required");
        return new NormalizedRequest(request.getPlanningYear(), seasonCode, upper(request.getWaveCode()));
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private record NormalizedRequest(Integer planningYear, String seasonCode, String waveCode) {
    }
}
