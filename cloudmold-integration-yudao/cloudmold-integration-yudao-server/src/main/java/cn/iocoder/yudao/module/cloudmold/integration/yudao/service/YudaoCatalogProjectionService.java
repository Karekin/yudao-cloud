package cn.iocoder.yudao.module.cloudmold.integration.yudao.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionApi;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionView;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.*;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.dal.dataobject.LegacyCatalogProjectionDO;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.dal.mysql.LegacyCatalogProjectionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class YudaoCatalogProjectionService implements YudaoCatalogProjectionApi {

    private final CatalogSkuProjectionApi catalogSkuProjectionApi;
    private final LegacyCatalogProjectionMapper projectionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LegacyCatalogProjectionResult plan(PlanLegacyCatalogProjectionCommand command) {
        require(command != null && command.getCanonicalSkuId() != null
                && !command.getCanonicalSkuId().isBlank(), "canonicalSkuId is required");
        require(command.getTargets() != null && !command.getTargets().isEmpty(), "at least one target is required");
        CatalogSkuProjectionView sku = catalogSkuProjectionApi.getActiveSku(command.getCanonicalSkuId());
        require("ACTIVE".equals(sku.getCatalogStatus()), "canonical SKU must be ACTIVE");
        require(sku.getAggregateVersion() != null && sku.getAggregateVersion() > 0,
                "canonical SKU aggregateVersion is required");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<LegacyCatalogProjectionItem> items = new ArrayList<>();
        for (LegacyCatalogTargetSystem target : EnumSet.copyOf(command.getTargets())) {
            String targetEntity = targetEntity(target);
            String payload = JsonUtils.toJsonString(payload(target, targetEntity, sku));
            String payloadHash = DigestUtil.sha256Hex(payload);
            LegacyCatalogProjectionDO existing = projectionMapper.selectByBusinessKeyForUpdate(tenantId,
                    target.name(), targetEntity, sku.getCanonicalSkuId());
            Long projectionId;
            boolean changed;
            if (existing == null) {
                projectionMapper.insertOrResolve(tenantId, target.name(), targetEntity,
                        sku.getCanonicalSkuId(), sku.getAggregateVersion(), payload, payloadHash, now);
                projectionId = projectionMapper.selectLastInsertId();
                require(projectionId != null && projectionId > 0, "failed to resolve legacy Catalog projection");
                changed = true;
            } else {
                projectionId = existing.getProjectionId();
                require(existing.getAggregateVersion() <= sku.getAggregateVersion(),
                        "legacy Catalog projection is ahead of canonical Catalog");
                changed = false;
                if (!Objects.equals(existing.getAggregateVersion(), sku.getAggregateVersion())
                        || !Objects.equals(existing.getPayloadHash(), payloadHash)) {
                    require(projectionMapper.refresh(projectionId, tenantId, sku.getAggregateVersion(),
                            payload, payloadHash, now) == 1, "legacy Catalog projection refresh conflict");
                    changed = true;
                }
            }
            items.add(LegacyCatalogProjectionItem.builder().projectionId(projectionId)
                    .targetSystem(target).targetEntity(targetEntity).canonicalSkuId(sku.getCanonicalSkuId())
                    .aggregateVersion(sku.getAggregateVersion()).payloadHash(payloadHash)
                    .state("PENDING").changed(changed).build());
        }
        return LegacyCatalogProjectionResult.builder().canonicalSkuId(sku.getCanonicalSkuId())
                .aggregateVersion(sku.getAggregateVersion()).projections(List.copyOf(items)).build();
    }

    static Map<String, Object> payload(LegacyCatalogTargetSystem target, String targetEntity,
                                       CatalogSkuProjectionView sku) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("target_system", target.name());
        value.put("target_entity", targetEntity);
        value.put("canonical_style_id", sku.getCanonicalStyleId());
        value.put("canonical_spu_id", sku.getCanonicalSpuId());
        value.put("canonical_sku_id", sku.getCanonicalSkuId());
        value.put("aggregate_version", sku.getAggregateVersion());
        value.put("style_code", sku.getStyleCode());
        value.put("spu_code", sku.getSpuCode());
        value.put("sku_code", sku.getSkuCode());
        value.put("product_name", sku.getProductName());
        value.put("color_code", sku.getColorCode());
        value.put("color_name", sku.getColorName());
        value.put("size_group_code", sku.getSizeGroupCode());
        value.put("size_code", sku.getSizeCode());
        value.put("size_name", sku.getSizeName());
        value.put("primary_barcode", sku.getPrimaryBarcode());
        value.put("base_uom_code", sku.getBaseUomCode());
        value.put("catalog_status", sku.getCatalogStatus());
        value.put("projection_policy", switch (target) {
            case MALL -> "identity_and_sales_attributes_only";
            case ERP -> "identity_and_procurement_attributes_only";
            case WMS -> "identity_and_physical_attributes_only";
        });
        return value;
    }

    private static String targetEntity(LegacyCatalogTargetSystem target) {
        return switch (target) {
            case MALL -> "SKU";
            case ERP -> "PRODUCT";
            case WMS -> "ITEM_SKU";
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

}
