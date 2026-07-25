package cn.iocoder.yudao.module.cloudmold.catalog.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.*;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.CatalogMasterDataMapper;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.CatalogLifecycleMapper;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.CatalogOperationMapper;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogCommandServiceImpl implements CatalogCommandApi {

    static final int OPERATION_PROCESSING = 0;
    static final int OPERATION_SUCCEEDED = 10;
    static final int STATUS_DRAFT = 0;
    static final int STATUS_ACTIVE = 10;
    static final int STATUS_INACTIVE = 20;
    static final int STATUS_ARCHIVED = 90;
    static final int SPU_STATUS_SUBMITTED = 10;
    static final int SPU_STATUS_APPROVED = 20;
    static final int SPU_STATUS_ACTIVE = 30;
    static final int SPU_STATUS_INACTIVE = 40;
    static final int BARCODE_ACTIVE = 10;
    static final int BARCODE_RETIRED = 90;
    private static final Set<String> SEASONS = Set.of("SPRING", "SUMMER", "AUTUMN", "WINTER", "ALL_SEASON");
    private static final Set<String> BARCODE_TYPES = Set.of("EAN13", "EAN8", "UPC", "CODE128", "INTERNAL");

    private final CatalogOperationMapper operationMapper;
    private final CatalogMasterDataMapper masterDataMapper;
    private final CatalogLifecycleMapper lifecycleMapper;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DefineCatalogSkuResult defineSku(DefineCatalogSkuCommand rawCommand) {
        NormalizedCommand command = normalize(rawCommand);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();

        operationMapper.insertOrResolve(tenantId, command.idempotencyKey(), requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve Catalog operation");
        CatalogOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "Catalog operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with a different Catalog payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing Catalog operation is not complete");
            DefineCatalogSkuResult replay = JsonUtils.parseObject(operation.getResultJson(), DefineCatalogSkuResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        CatalogStyleDO style = resolveStyle(tenantId, command, now);
        CatalogSpuDO spu = resolveSpu(tenantId, style, command, now);
        CatalogColorDO color = resolveColor(tenantId, command, now);
        CatalogSizeGroupDO sizeGroup = resolveSizeGroup(tenantId, command, now);
        CatalogSizeDO size = resolveSize(tenantId, sizeGroup, command, now);
        ResolvedSku resolvedSku = resolveSku(tenantId, spu, color, sizeGroup, size, command, now);
        CatalogBarcodeDO barcode = resolveBarcode(tenantId, resolvedSku.sku(), command, now);

        if (resolvedSku.created()) {
            appendSkuDefinedEvent(tenantId, style, spu, color, sizeGroup, size, resolvedSku.sku(), barcode, command);
        }

        DefineCatalogSkuResult result = DefineCatalogSkuResult.builder()
                .operationId(operationId)
                .canonicalStyleId(style.getStyleId())
                .canonicalSpuId(spu.getSpuId())
                .canonicalSkuId(resolvedSku.sku().getSkuId())
                .colorId(color.getColorId())
                .sizeGroupId(sizeGroup.getSizeGroupId())
                .sizeId(size.getSizeId())
                .primaryBarcodeId(barcode.getBarcodeId())
                .styleStatus(statusName(CatalogEntityType.STYLE, style.getStatus()))
                .styleVersion(style.getVersion())
                .spuStatus(statusName(CatalogEntityType.SPU, spu.getStatus()))
                .spuVersion(spu.getVersion())
                .colorStatus(statusName(CatalogEntityType.COLOR, color.getStatus()))
                .colorVersion(color.getVersion())
                .sizeGroupStatus(statusName(CatalogEntityType.SIZE_GROUP, sizeGroup.getStatus()))
                .sizeGroupVersion(sizeGroup.getVersion())
                .sizeStatus(statusName(CatalogEntityType.SIZE, size.getStatus()))
                .sizeVersion(size.getVersion())
                .skuStatus(statusName(CatalogEntityType.SKU, resolvedSku.sku().getStatus()))
                .aggregateVersion(resolvedSku.sku().getVersion())
                .created(resolvedSku.created())
                .duplicate(false)
                .build();
        require(operationMapper.markSucceeded(operationId, tenantId, JsonUtils.toJsonString(result), now) == 1,
                "Catalog operation completion conflict");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CatalogMetadataUpdateResult updateMetadata(CatalogMetadataUpdateCommand rawCommand) {
        NormalizedMetadataCommand command = normalize(rawCommand);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(tenantId + "\u001f" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();

        operationMapper.insertOrResolve(tenantId, command.idempotencyKey(), requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve Catalog metadata operation");
        CatalogOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "Catalog metadata operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with a different Catalog metadata payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing Catalog metadata operation is not complete");
            CatalogMetadataUpdateResult replay = JsonUtils.parseObject(operation.getResultJson(), CatalogMetadataUpdateResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        MetadataOutcome outcome = switch (command.entityType()) {
            case STYLE -> updateStyleMetadata(tenantId, command, now);
            case SPU -> updateSpuMetadata(tenantId, command, now);
            case SKU -> updateSkuMetadata(tenantId, command, now);
            default -> throw new IllegalArgumentException("Catalog metadata update only supports STYLE, SPU, and SKU");
        };
        if (outcome.changed()) {
            appendMetadataUpdatedEvent(tenantId, command, outcome);
        }
        CatalogMetadataUpdateResult result = CatalogMetadataUpdateResult.builder()
                .operationId(operationId).entityType(command.entityType()).entityId(command.entityId())
                .businessCode(outcome.businessCode()).currentStatus(statusName(command.entityType(), outcome.status()))
                .aggregateVersion(outcome.aggregateVersion()).duplicate(false).build();
        require(operationMapper.markSucceeded(operationId, tenantId, JsonUtils.toJsonString(result), now) == 1,
                "Catalog metadata operation completion conflict");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CatalogBarcodeRotateResult rotateBarcode(CatalogBarcodeRotateCommand rawCommand) {
        NormalizedBarcodeRotateCommand command = normalize(rawCommand);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(tenantId + "\u001f" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();

        operationMapper.insertOrResolve(tenantId, command.idempotencyKey(), requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve Catalog barcode operation");
        CatalogOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "Catalog barcode operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with a different Catalog barcode payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing Catalog barcode operation is not complete");
            CatalogBarcodeRotateResult replay = JsonUtils.parseObject(operation.getResultJson(), CatalogBarcodeRotateResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        CatalogSkuDO sku = requireNonNull(lifecycleMapper.selectSkuForUpdate(tenantId, command.skuId()),
                "Catalog SKU does not exist");
        require(Objects.equals(sku.getVersion(), command.expectedVersion()), "Catalog expectedVersion conflict");
        require(sku.getStatus() != STATUS_ARCHIVED, "archived Catalog SKU cannot rotate its primary barcode");
        CatalogBarcodeDO previous = requireNonNull(masterDataMapper.selectActivePrimaryBarcode(tenantId, sku.getSkuId()),
                "Catalog SKU has no active primary Barcode to rotate");
        require(!Objects.equals(previous.getBarcode(), command.barcode()),
                "new primary barcode must differ from the current primary barcode");
        CatalogBarcodeDO existing = masterDataMapper.selectBarcode(tenantId, command.barcode());
        require(existing == null, "barcode already exists in Catalog history");
        require(masterDataMapper.retirePrimaryBarcode(tenantId, previous.getBarcodeId(), previous.getVersion(), now) == 1,
                "Catalog primary Barcode retirement conflict");
        require(masterDataMapper.insertPrimaryBarcode(UUID.randomUUID().toString(), tenantId, sku.getSkuId(),
                command.barcode(), command.barcodeType(), now) == 1, "failed to persist the new primary Barcode");
        CatalogBarcodeDO current = masterDataMapper.selectBarcode(tenantId, command.barcode());
        require(current != null && Objects.equals(current.getSkuId(), sku.getSkuId())
                        && Boolean.TRUE.equals(current.getIsPrimary()) && current.getStatus() == BARCODE_ACTIVE,
                "rotated primary barcode was not persisted");
        require(masterDataMapper.bumpSkuVersion(tenantId, sku.getSkuId(), sku.getVersion(), now) == 1,
                "Catalog SKU version bump conflict");
        long newVersion = sku.getVersion() + 1;
        appendBarcodeRotatedEvent(tenantId, command, sku, previous, current, newVersion);

        CatalogBarcodeRotateResult result = CatalogBarcodeRotateResult.builder()
                .operationId(operationId).skuId(sku.getSkuId()).previousBarcodeId(previous.getBarcodeId())
                .previousBarcode(previous.getBarcode()).currentBarcodeId(current.getBarcodeId())
                .currentBarcode(current.getBarcode()).barcodeType(current.getBarcodeType())
                .aggregateVersion(newVersion).duplicate(false).build();
        require(operationMapper.markSucceeded(operationId, tenantId, JsonUtils.toJsonString(result), now) == 1,
                "Catalog barcode operation completion conflict");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CatalogLifecycleResult changeStatus(CatalogLifecycleCommand rawCommand) {
        NormalizedLifecycleCommand command = normalize(rawCommand);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(tenantId + "\u001f" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();

        operationMapper.insertOrResolve(tenantId, command.idempotencyKey(), requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve Catalog lifecycle operation");
        CatalogOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "Catalog lifecycle operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with a different Catalog lifecycle payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing Catalog lifecycle operation is not complete");
            CatalogLifecycleResult replay = JsonUtils.parseObject(operation.getResultJson(), CatalogLifecycleResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        LifecycleEntity entity = loadForUpdate(tenantId, command.entityType(), command.entityId());
        require(entity.version().equals(command.expectedVersion()), "Catalog expectedVersion conflict");
        int targetStatus = targetStatus(command.entityType(), entity.status(), command.action());
        validateLifecyclePreconditions(tenantId, command, entity, targetStatus);
        require(updateStatus(tenantId, command.entityType(), entity, targetStatus, now) == 1,
                "Catalog lifecycle version conflict");
        long newVersion = entity.version() + 1;
        appendStatusEvent(tenantId, command, entity, targetStatus, newVersion);

        CatalogLifecycleResult result = CatalogLifecycleResult.builder()
                .operationId(operationId).entityType(command.entityType()).entityId(entity.id())
                .businessCode(entity.businessCode()).previousStatus(statusName(command.entityType(), entity.status()))
                .currentStatus(statusName(command.entityType(), targetStatus)).aggregateVersion(newVersion)
                .duplicate(false).build();
        require(operationMapper.markSucceeded(operationId, tenantId, JsonUtils.toJsonString(result), now) == 1,
                "Catalog lifecycle operation completion conflict");
        return result;
    }

    private LifecycleEntity loadForUpdate(Long tenantId, CatalogEntityType type, String id) {
        return switch (type) {
            case STYLE -> {
                CatalogStyleDO value = lifecycleMapper.selectStyleForUpdate(tenantId, id);
                require(value != null, "Catalog Style does not exist");
                yield new LifecycleEntity(value.getStyleId(), value.getStyleCode(), value.getStatus(), value.getVersion(), value);
            }
            case SPU -> {
                CatalogSpuDO value = lifecycleMapper.selectSpuForUpdate(tenantId, id);
                require(value != null, "Catalog SPU does not exist");
                yield new LifecycleEntity(value.getSpuId(), value.getSpuCode(), value.getStatus(), value.getVersion(), value);
            }
            case SKU -> {
                CatalogSkuDO value = lifecycleMapper.selectSkuForUpdate(tenantId, id);
                require(value != null, "Catalog SKU does not exist");
                yield new LifecycleEntity(value.getSkuId(), value.getSkuCode(), value.getStatus(), value.getVersion(), value);
            }
            case COLOR -> {
                CatalogColorDO value = lifecycleMapper.selectColorForUpdate(tenantId, id);
                require(value != null, "Catalog Color does not exist");
                yield new LifecycleEntity(value.getColorId(), value.getColorCode(), value.getStatus(), value.getVersion(), value);
            }
            case SIZE_GROUP -> {
                CatalogSizeGroupDO value = lifecycleMapper.selectSizeGroupForUpdate(tenantId, id);
                require(value != null, "Catalog Size Group does not exist");
                yield new LifecycleEntity(value.getSizeGroupId(), value.getSizeGroupCode(), value.getStatus(), value.getVersion(), value);
            }
            case SIZE -> {
                CatalogSizeDO value = lifecycleMapper.selectSizeForUpdate(tenantId, id);
                require(value != null, "Catalog Size does not exist");
                yield new LifecycleEntity(value.getSizeId(), value.getSizeCode(), value.getStatus(), value.getVersion(), value);
            }
        };
    }

    private void validateLifecyclePreconditions(Long tenantId, NormalizedLifecycleCommand command,
                                                LifecycleEntity entity, int targetStatus) {
        if (command.entityType() == CatalogEntityType.SPU && command.action() == CatalogLifecycleAction.SUBMIT) {
            CatalogSpuDO spu = (CatalogSpuDO) entity.value();
            CatalogStyleDO style = lifecycleMapper.selectStyle(tenantId, spu.getStyleId());
            require(style != null && style.getStatus() == STATUS_ACTIVE, "Style must be ACTIVE before SPU submission");
        }
        if (command.entityType() == CatalogEntityType.SPU && targetStatus == 30) {
            require(lifecycleMapper.countActiveSkus(tenantId, entity.id()) > 0,
                    "SPU requires at least one ACTIVE SKU before activation");
        }
        if (command.entityType() == CatalogEntityType.SIZE && targetStatus == 10) {
            CatalogSizeDO size = (CatalogSizeDO) entity.value();
            CatalogSizeGroupDO group = lifecycleMapper.selectSizeGroup(tenantId, size.getSizeGroupId());
            require(group != null && group.getStatus() == STATUS_ACTIVE, "Size Group must be ACTIVE before Size activation");
        }
        if (command.entityType() == CatalogEntityType.SKU && targetStatus == 10) {
            CatalogSkuDO sku = (CatalogSkuDO) entity.value();
            CatalogSpuDO spu = lifecycleMapper.selectSpu(tenantId, sku.getSpuId());
            CatalogColorDO color = lifecycleMapper.selectColor(tenantId, sku.getColorId());
            CatalogSizeDO size = lifecycleMapper.selectSize(tenantId, sku.getSizeId());
            CatalogSizeGroupDO group = size == null ? null : lifecycleMapper.selectSizeGroup(tenantId, size.getSizeGroupId());
            require(spu != null && (spu.getStatus() == SPU_STATUS_APPROVED || spu.getStatus() == SPU_STATUS_ACTIVE),
                    "SPU must be APPROVED or ACTIVE before SKU activation");
            require(color != null && color.getStatus() == STATUS_ACTIVE, "Color must be ACTIVE before SKU activation");
            require(size != null && size.getStatus() == STATUS_ACTIVE, "Size must be ACTIVE before SKU activation");
            require(group != null && group.getStatus() == STATUS_ACTIVE, "Size Group must be ACTIVE before SKU activation");
            require(lifecycleMapper.countActivePrimaryBarcodes(tenantId, sku.getSkuId()) == 1,
                    "SKU requires exactly one active primary Barcode before activation");
        }
        if (command.action() == CatalogLifecycleAction.DEACTIVATE) {
            switch (command.entityType()) {
                case STYLE -> require(lifecycleMapper.countActiveSpus(tenantId, entity.id()) == 0,
                        "Style cannot be deactivated while ACTIVE SPU still reference it");
                case SPU -> require(lifecycleMapper.countActiveSkus(tenantId, entity.id()) == 0,
                        "SPU cannot be deactivated while ACTIVE SKU still reference it");
                case COLOR -> require(lifecycleMapper.countActiveColorSkus(tenantId, entity.id()) == 0,
                        "Color cannot be deactivated while ACTIVE SKU still reference it");
                case SIZE_GROUP -> {
                    require(lifecycleMapper.countActiveSizes(tenantId, entity.id()) == 0,
                            "Size Group cannot be deactivated while ACTIVE Size still reference it");
                    require(lifecycleMapper.countActiveSizeGroupSkus(tenantId, entity.id()) == 0,
                            "Size Group cannot be deactivated while ACTIVE SKU still reference it");
                }
                case SIZE -> require(lifecycleMapper.countActiveSizeSkus(tenantId, entity.id()) == 0,
                        "Size cannot be deactivated while ACTIVE SKU still reference it");
                case SKU -> {
                    // no additional child fence
                }
            }
        }
        if (command.action() == CatalogLifecycleAction.ARCHIVE) {
            int children = switch (command.entityType()) {
                case STYLE -> lifecycleMapper.countNonArchivedSpus(tenantId, entity.id());
                case SPU -> lifecycleMapper.countNonArchivedSkus(tenantId, entity.id());
                case COLOR -> lifecycleMapper.countNonArchivedColorSkus(tenantId, entity.id());
                case SIZE_GROUP -> lifecycleMapper.countNonArchivedSizes(tenantId, entity.id());
                case SIZE -> lifecycleMapper.countNonArchivedSizeSkus(tenantId, entity.id());
                case SKU -> 0;
            };
            require(children == 0, "Catalog entity cannot be archived while non-archived children reference it");
        }
    }

    private int updateStatus(Long tenantId, CatalogEntityType type, LifecycleEntity entity,
                             int target, LocalDateTime now) {
        return switch (type) {
            case STYLE -> lifecycleMapper.updateStyle(tenantId, entity.id(), entity.status(), target, entity.version(), now);
            case SPU -> lifecycleMapper.updateSpu(tenantId, entity.id(), entity.status(), target, entity.version(), now);
            case SKU -> lifecycleMapper.updateSku(tenantId, entity.id(), entity.status(), target, entity.version(), now);
            case COLOR -> lifecycleMapper.updateColor(tenantId, entity.id(), entity.status(), target, entity.version(), now);
            case SIZE_GROUP -> lifecycleMapper.updateSizeGroup(tenantId, entity.id(), entity.status(), target, entity.version(), now);
            case SIZE -> lifecycleMapper.updateSize(tenantId, entity.id(), entity.status(), target, entity.version(), now);
        };
    }

    private void appendStatusEvent(Long tenantId, NormalizedLifecycleCommand command, LifecycleEntity entity,
                                   int targetStatus, long newVersion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("entity_type", command.entityType().name());
        payload.put("entity_id", entity.id());
        payload.put("business_code", entity.businessCode());
        payload.put("previous_status", statusName(command.entityType(), entity.status()));
        payload.put("current_status", statusName(command.entityType(), targetStatus));
        payload.put("action", command.action().name());
        payload.put("reason", command.reason());
        payload.put("effective_at", command.occurredAt().toString());
        payload.put("version", newVersion);
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType("catalog.entity.status_changed").schemaVersion(1).sourceSystem("cloudmold-catalog")
                .tenantId(tenantId).aggregateType("catalog_entity")
                .aggregateId(entity.id()).aggregateVersion(newVersion).eventSequence((short) 1)
                .occurredAt(command.occurredAt()).correlationId(command.correlationId())
                .causationId(command.causationId()).idempotencyKey(command.idempotencyKey()).payload(payload)
                .headers(Map.of("business_code", entity.businessCode())).destination("catalog-events").build());
    }

    private static int targetStatus(CatalogEntityType type, int current, CatalogLifecycleAction action) {
        if (type == CatalogEntityType.SPU) {
            if (action == CatalogLifecycleAction.SUBMIT && current == 0) return 10;
            if (action == CatalogLifecycleAction.APPROVE && current == 10) return 20;
            if (action == CatalogLifecycleAction.REJECT && current == 10) return 50;
            if (action == CatalogLifecycleAction.RESET_DRAFT && current == 50) return 0;
            if (action == CatalogLifecycleAction.ACTIVATE && (current == 20 || current == 40)) return 30;
            if (action == CatalogLifecycleAction.DEACTIVATE && current == 30) return 40;
            if (action == CatalogLifecycleAction.ARCHIVE && Set.of(0, 20, 40, 50).contains(current)) return 90;
        } else {
            if (action == CatalogLifecycleAction.ACTIVATE && (current == 0 || current == 20)) return 10;
            if (action == CatalogLifecycleAction.DEACTIVATE && current == 10) return 20;
            if (action == CatalogLifecycleAction.ARCHIVE && (current == 0 || current == 20)) return 90;
        }
        throw new IllegalArgumentException("illegal Catalog lifecycle transition: " + type + " "
                + statusName(type, current) + " -> " + action);
    }

    private static String statusName(CatalogEntityType type, int status) {
        if (type == CatalogEntityType.SPU) {
            return switch (status) {
                case 0 -> "DRAFT"; case 10 -> "SUBMITTED"; case 20 -> "APPROVED";
                case 30 -> "ACTIVE"; case 40 -> "INACTIVE"; case 50 -> "REJECTED";
                case 90 -> "ARCHIVED"; default -> "UNKNOWN";
            };
        }
        return switch (status) {
            case 0 -> "DRAFT"; case 10 -> "ACTIVE"; case 20 -> "INACTIVE";
            case 90 -> "ARCHIVED"; default -> "UNKNOWN";
        };
    }

    private CatalogStyleDO resolveStyle(Long tenantId, NormalizedCommand command, LocalDateTime now) {
        masterDataMapper.insertStyle(UUID.randomUUID().toString(), tenantId, command.styleCode(), command.styleName(),
                command.planningCategoryRef(), command.brandRef(), command.planningYear(), command.seasonCode(),
                command.waveCode(), now);
        CatalogStyleDO value = masterDataMapper.selectStyle(tenantId, command.styleCode());
        require(value != null, "Catalog Style was not persisted");
        require(Objects.equals(value.getStyleName(), command.styleName())
                        && Objects.equals(value.getPlanningCategoryRef(), command.planningCategoryRef())
                        && Objects.equals(value.getBrandRef(), command.brandRef())
                        && Objects.equals(value.getPlanningYear(), command.planningYear())
                        && Objects.equals(value.getSeasonCode(), command.seasonCode())
                        && Objects.equals(value.getWaveCode(), command.waveCode()),
                "styleCode already belongs to a different Style definition");
        return value;
    }

    private CatalogSpuDO resolveSpu(Long tenantId, CatalogStyleDO style, NormalizedCommand command, LocalDateTime now) {
        masterDataMapper.insertSpu(UUID.randomUUID().toString(), tenantId, style.getStyleId(), command.spuCode(),
                command.productName(), command.salesCategoryRef(), now);
        CatalogSpuDO value = masterDataMapper.selectSpu(tenantId, command.spuCode());
        require(value != null, "Catalog SPU was not persisted");
        require(Objects.equals(value.getStyleId(), style.getStyleId())
                        && Objects.equals(value.getProductName(), command.productName())
                        && Objects.equals(value.getSalesCategoryRef(), command.salesCategoryRef()),
                "spuCode already belongs to a different SPU definition");
        return value;
    }

    private CatalogColorDO resolveColor(Long tenantId, NormalizedCommand command, LocalDateTime now) {
        masterDataMapper.insertColor(UUID.randomUUID().toString(), tenantId, command.colorCode(), command.colorName(), now);
        CatalogColorDO value = masterDataMapper.selectColor(tenantId, command.colorCode());
        require(value != null && Objects.equals(value.getDisplayName(), command.colorName()),
                "colorCode already belongs to a different Color definition");
        return value;
    }

    private CatalogSizeGroupDO resolveSizeGroup(Long tenantId, NormalizedCommand command, LocalDateTime now) {
        masterDataMapper.insertSizeGroup(UUID.randomUUID().toString(), tenantId, command.sizeGroupCode(),
                command.sizeGroupName(), now);
        CatalogSizeGroupDO value = masterDataMapper.selectSizeGroup(tenantId, command.sizeGroupCode());
        require(value != null && Objects.equals(value.getSizeGroupName(), command.sizeGroupName()),
                "sizeGroupCode already belongs to a different Size Group definition");
        return value;
    }

    private CatalogSizeDO resolveSize(Long tenantId, CatalogSizeGroupDO group, NormalizedCommand command,
                                      LocalDateTime now) {
        masterDataMapper.insertSize(UUID.randomUUID().toString(), tenantId, group.getSizeGroupId(), command.sizeCode(),
                command.sizeName(), command.sizeSort(), now);
        CatalogSizeDO value = masterDataMapper.selectSize(tenantId, group.getSizeGroupId(), command.sizeCode());
        require(value != null && Objects.equals(value.getSizeName(), command.sizeName())
                        && Objects.equals(value.getSortOrder(), command.sizeSort()),
                "sizeCode already belongs to a different Size definition");
        return value;
    }

    private ResolvedSku resolveSku(Long tenantId, CatalogSpuDO spu, CatalogColorDO color,
                                   CatalogSizeGroupDO group, CatalogSizeDO size,
                                   NormalizedCommand command, LocalDateTime now) {
        String variantKey = "COLOR=" + command.colorCode() + "|SIZE_GROUP=" + command.sizeGroupCode()
                + "|SIZE=" + command.sizeCode();
        String variantKeyHash = DigestUtil.sha256Hex(variantKey);
        int inserted = masterDataMapper.insertSku(UUID.randomUUID().toString(), tenantId, spu.getSpuId(),
                command.skuCode(), color.getColorId(), size.getSizeId(), variantKey, variantKeyHash,
                command.baseUomCode(), now);
        CatalogSkuDO value = masterDataMapper.selectSku(tenantId, command.skuCode());
        require(value != null, "Catalog SKU was not persisted");
        require(Objects.equals(value.getSpuId(), spu.getSpuId())
                        && Objects.equals(value.getColorId(), color.getColorId())
                        && Objects.equals(value.getSizeId(), size.getSizeId())
                        && Objects.equals(value.getVariantKey(), variantKey)
                        && Objects.equals(value.getVariantKeyHash(), variantKeyHash)
                        && Objects.equals(value.getBaseUomCode(), command.baseUomCode()),
                "skuCode or variant hash already belongs to a different SKU definition");
        return new ResolvedSku(value, inserted == 1);
    }

    private CatalogBarcodeDO resolveBarcode(Long tenantId, CatalogSkuDO sku, NormalizedCommand command,
                                             LocalDateTime now) {
        masterDataMapper.insertPrimaryBarcode(UUID.randomUUID().toString(), tenantId, sku.getSkuId(),
                command.barcode(), command.barcodeType(), now);
        CatalogBarcodeDO value = masterDataMapper.selectBarcode(tenantId, command.barcode());
        require(value != null && Objects.equals(value.getSkuId(), sku.getSkuId())
                        && Objects.equals(value.getBarcodeType(), command.barcodeType())
                        && Boolean.TRUE.equals(value.getIsPrimary()) && value.getStatus() == BARCODE_ACTIVE,
                "barcode already belongs to another SKU or is not the active primary barcode");
        return value;
    }

    private void appendSkuDefinedEvent(Long tenantId, CatalogStyleDO style, CatalogSpuDO spu, CatalogColorDO color,
                                       CatalogSizeGroupDO group, CatalogSizeDO size, CatalogSkuDO sku,
                                       CatalogBarcodeDO barcode, NormalizedCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("canonical_style_id", style.getStyleId());
        payload.put("style_code", style.getStyleCode());
        payload.put("style_name", style.getStyleName());
        payload.put("brand_ref", style.getBrandRef());
        payload.put("planning_category_ref", style.getPlanningCategoryRef());
        payload.put("planning_year", style.getPlanningYear());
        payload.put("season_code", style.getSeasonCode());
        payload.put("wave_code", style.getWaveCode());
        payload.put("canonical_spu_id", spu.getSpuId());
        payload.put("spu_code", spu.getSpuCode());
        payload.put("product_name", spu.getProductName());
        payload.put("sales_category_ref", spu.getSalesCategoryRef());
        payload.put("canonical_sku_id", sku.getSkuId());
        payload.put("sku_code", sku.getSkuCode());
        payload.put("variant_key", sku.getVariantKey());
        payload.put("variant_key_hash", sku.getVariantKeyHash());
        payload.put("color_id", color.getColorId());
        payload.put("color_code", color.getColorCode());
        payload.put("color_name", color.getDisplayName());
        payload.put("size_group_id", group.getSizeGroupId());
        payload.put("size_group_code", group.getSizeGroupCode());
        payload.put("size_id", size.getSizeId());
        payload.put("size_code", size.getSizeCode());
        payload.put("size_name", size.getSizeName());
        payload.put("base_uom_code", sku.getBaseUomCode());
        payload.put("primary_barcode", barcode.getBarcode());
        payload.put("status", "DRAFT");
        payload.put("version", sku.getVersion());

        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType("catalog.sku.defined").schemaVersion(1).sourceSystem("cloudmold-catalog")
                .tenantId(tenantId).aggregateType("catalog_sku").aggregateId(sku.getSkuId())
                .aggregateVersion(sku.getVersion()).eventSequence((short) 1).occurredAt(command.occurredAt())
                .correlationId(command.correlationId()).causationId(command.causationId())
                .idempotencyKey(command.idempotencyKey()).payload(payload)
                .headers(Map.of("style_id", style.getStyleId(), "spu_id", spu.getSpuId()))
                .destination("catalog-events").build());
    }

    private MetadataOutcome updateStyleMetadata(Long tenantId, NormalizedMetadataCommand command, LocalDateTime now) {
        CatalogStyleDO current = requireNonNull(lifecycleMapper.selectStyleForUpdate(tenantId, command.entityId()),
                "Catalog Style does not exist");
        require(current.getStatus() != STATUS_ARCHIVED, "archived Catalog Style cannot be updated");
        require(Objects.equals(current.getVersion(), command.expectedVersion()), "Catalog expectedVersion conflict");
        CatalogStyleDO existing = masterDataMapper.selectStyle(tenantId, command.styleCode());
        require(existing == null || Objects.equals(existing.getStyleId(), current.getStyleId()),
                "styleCode already belongs to another Style");
        if (Objects.equals(current.getStyleCode(), command.styleCode())
                && Objects.equals(current.getStyleName(), command.styleName())
                && Objects.equals(current.getPlanningCategoryRef(), command.planningCategoryRef())
                && Objects.equals(current.getBrandRef(), command.brandRef())
                && Objects.equals(current.getPlanningYear(), command.planningYear())
                && Objects.equals(current.getSeasonCode(), command.seasonCode())
                && Objects.equals(current.getWaveCode(), command.waveCode())) {
            return new MetadataOutcome(current.getStyleId(), current.getStyleCode(), current.getStatus(),
                    current.getVersion(), false);
        }
        require(masterDataMapper.updateStyleMetadata(tenantId, current.getStyleId(), command.styleCode(),
                command.styleName(), command.planningCategoryRef(), command.brandRef(), command.planningYear(),
                command.seasonCode(), command.waveCode(), current.getVersion(), now) == 1,
                "Catalog Style metadata update conflict");
        return new MetadataOutcome(current.getStyleId(), command.styleCode(), current.getStatus(),
                current.getVersion() + 1, true);
    }

    private MetadataOutcome updateSpuMetadata(Long tenantId, NormalizedMetadataCommand command, LocalDateTime now) {
        CatalogSpuDO current = requireNonNull(lifecycleMapper.selectSpuForUpdate(tenantId, command.entityId()),
                "Catalog SPU does not exist");
        require(current.getStatus() != STATUS_ARCHIVED, "archived Catalog SPU cannot be updated");
        require(Objects.equals(current.getVersion(), command.expectedVersion()), "Catalog expectedVersion conflict");
        CatalogSpuDO existing = masterDataMapper.selectSpu(tenantId, command.spuCode());
        require(existing == null || Objects.equals(existing.getSpuId(), current.getSpuId()),
                "spuCode already belongs to another SPU");
        if (Objects.equals(current.getSpuCode(), command.spuCode())
                && Objects.equals(current.getProductName(), command.productName())
                && Objects.equals(current.getSalesCategoryRef(), command.salesCategoryRef())) {
            return new MetadataOutcome(current.getSpuId(), current.getSpuCode(), current.getStatus(),
                    current.getVersion(), false);
        }
        require(masterDataMapper.updateSpuMetadata(tenantId, current.getSpuId(), command.spuCode(),
                command.productName(), command.salesCategoryRef(), current.getVersion(), now) == 1,
                "Catalog SPU metadata update conflict");
        return new MetadataOutcome(current.getSpuId(), command.spuCode(), current.getStatus(),
                current.getVersion() + 1, true);
    }

    private MetadataOutcome updateSkuMetadata(Long tenantId, NormalizedMetadataCommand command, LocalDateTime now) {
        CatalogSkuDO current = requireNonNull(lifecycleMapper.selectSkuForUpdate(tenantId, command.entityId()),
                "Catalog SKU does not exist");
        require(current.getStatus() != STATUS_ARCHIVED, "archived Catalog SKU cannot be updated");
        require(Objects.equals(current.getVersion(), command.expectedVersion()), "Catalog expectedVersion conflict");
        CatalogSkuDO existing = masterDataMapper.selectSku(tenantId, command.skuCode());
        require(existing == null || Objects.equals(existing.getSkuId(), current.getSkuId()),
                "skuCode already belongs to another SKU");
        if (Objects.equals(current.getSkuCode(), command.skuCode())) {
            return new MetadataOutcome(current.getSkuId(), current.getSkuCode(), current.getStatus(),
                    current.getVersion(), false);
        }
        require(masterDataMapper.updateSkuMetadata(tenantId, current.getSkuId(), command.skuCode(),
                current.getVersion(), now) == 1, "Catalog SKU metadata update conflict");
        return new MetadataOutcome(current.getSkuId(), command.skuCode(), current.getStatus(),
                current.getVersion() + 1, true);
    }

    private void appendMetadataUpdatedEvent(Long tenantId, NormalizedMetadataCommand command, MetadataOutcome outcome) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("entity_type", command.entityType().name());
        payload.put("entity_id", outcome.entityId());
        payload.put("business_code", outcome.businessCode());
        payload.put("status", statusName(command.entityType(), outcome.status()));
        payload.put("reason", command.reason());
        if (command.entityType() == CatalogEntityType.STYLE) {
            payload.put("style_code", command.styleCode());
            payload.put("style_name", command.styleName());
            payload.put("planning_category_ref", command.planningCategoryRef());
            payload.put("brand_ref", command.brandRef());
            payload.put("planning_year", command.planningYear());
            payload.put("season_code", command.seasonCode());
            payload.put("wave_code", command.waveCode());
        } else if (command.entityType() == CatalogEntityType.SPU) {
            payload.put("spu_code", command.spuCode());
            payload.put("product_name", command.productName());
            payload.put("sales_category_ref", command.salesCategoryRef());
        } else {
            payload.put("sku_code", command.skuCode());
        }
        payload.put("version", outcome.aggregateVersion());
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType("catalog.entity.metadata_updated").schemaVersion(1).sourceSystem("cloudmold-catalog")
                .tenantId(tenantId).aggregateType("catalog_entity").aggregateId(outcome.entityId())
                .aggregateVersion(outcome.aggregateVersion()).eventSequence((short) 1)
                .occurredAt(command.occurredAt()).correlationId(command.correlationId())
                .causationId(command.causationId()).idempotencyKey(command.idempotencyKey()).payload(payload)
                .headers(Map.of("business_code", outcome.businessCode())).destination("catalog-events").build());
    }

    private void appendBarcodeRotatedEvent(Long tenantId, NormalizedBarcodeRotateCommand command, CatalogSkuDO sku,
                                           CatalogBarcodeDO previous, CatalogBarcodeDO current, long newVersion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("canonical_sku_id", sku.getSkuId());
        payload.put("sku_code", sku.getSkuCode());
        payload.put("previous_barcode_id", previous.getBarcodeId());
        payload.put("previous_barcode", previous.getBarcode());
        payload.put("current_barcode_id", current.getBarcodeId());
        payload.put("current_barcode", current.getBarcode());
        payload.put("barcode_type", current.getBarcodeType());
        payload.put("reason", command.reason());
        payload.put("version", newVersion);
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType("catalog.barcode.rotated").schemaVersion(1).sourceSystem("cloudmold-catalog")
                .tenantId(tenantId).aggregateType("catalog_sku").aggregateId(sku.getSkuId())
                .aggregateVersion(newVersion).eventSequence((short) 1).occurredAt(command.occurredAt())
                .correlationId(command.correlationId()).causationId(command.causationId())
                .idempotencyKey(command.idempotencyKey()).payload(payload)
                .headers(Map.of("sku_code", sku.getSkuCode())).destination("catalog-events").build());
    }

    private static NormalizedLifecycleCommand normalize(CatalogLifecycleCommand command) {
        require(command != null && command.getEntityType() != null, "Catalog entityType is required");
        require(command.getAction() != null, "Catalog lifecycle action is required");
        requireUuid(command.getEntityId(), "entityId");
        require(command.getExpectedVersion() != null && command.getExpectedVersion() >= 1,
                "expectedVersion must be positive");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireText(command.getReason(), "reason", 512);
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
        return new NormalizedLifecycleCommand(command.getEntityType(), command.getEntityId(), command.getAction(),
                command.getExpectedVersion(), command.getIdempotencyKey().trim(), command.getReason().trim(),
                command.getCorrelationId(), command.getCausationId(), command.getOccurredAt());
    }

    private static NormalizedCommand normalize(DefineCatalogSkuCommand command) {
        require(command != null, "Catalog command is required");
        String status = upper(defaultIfBlank(command.getStatus(), "DRAFT"));
        require("DRAFT".equals(status), "defineSku only creates DRAFT catalog entities");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        String styleCode = code(command.getStyleCode(), "styleCode", 64);
        String spuCode = code(command.getSpuCode(), "spuCode", 64);
        String skuCode = code(command.getSkuCode(), "skuCode", 64);
        String colorCode = code(command.getColorCode(), "colorCode", 32);
        String groupCode = code(command.getSizeGroupCode(), "sizeGroupCode", 32);
        String sizeCode = code(command.getSizeCode(), "sizeCode", 32);
        String season = upper(command.getSeasonCode());
        require(SEASONS.contains(season), "seasonCode is invalid");
        require(command.getPlanningYear() != null && command.getPlanningYear() >= 2000
                && command.getPlanningYear() <= 2100, "planningYear must be between 2000 and 2100");
        requireSourceRef(command.getPlanningCategoryRef(), "planningCategoryRef");
        requireSourceRef(command.getSalesCategoryRef(), "salesCategoryRef");
        requireSourceRef(command.getBrandRef(), "brandRef");
        requireText(command.getStyleName(), "styleName", 255);
        requireText(command.getProductName(), "productName", 255);
        requireText(command.getColorName(), "colorName", 64);
        requireText(command.getSizeGroupName(), "sizeGroupName", 64);
        requireText(command.getSizeName(), "sizeName", 64);
        require(command.getSizeSort() != null && command.getSizeSort() >= 0, "sizeSort must be non-negative");
        require("PCS".equals(upper(command.getBaseUomCode())), "first Catalog slice supports PCS only");
        String barcodeType = upper(command.getBarcodeType());
        require(BARCODE_TYPES.contains(barcodeType), "barcodeType is invalid");
        requireText(command.getBarcode(), "barcode", 64);
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) {
            requireUuid(command.getCausationId(), "causationId");
        }
        return new NormalizedCommand(command.getIdempotencyKey().trim(), styleCode, command.getStyleName().trim(),
                command.getPlanningCategoryRef().trim(), command.getBrandRef().trim(), command.getPlanningYear(),
                season, blankToNull(command.getWaveCode()), spuCode, command.getProductName().trim(),
                command.getSalesCategoryRef().trim(), skuCode, command.getBarcode().trim(), barcodeType, colorCode,
                command.getColorName().trim(), groupCode, command.getSizeGroupName().trim(), sizeCode,
                command.getSizeName().trim(), command.getSizeSort(), "PCS", command.getCorrelationId(),
                command.getCausationId(), command.getOccurredAt());
    }

    private static NormalizedMetadataCommand normalize(CatalogMetadataUpdateCommand command) {
        require(command != null && command.getEntityType() != null, "Catalog entityType is required");
        require(command.getEntityType() == CatalogEntityType.STYLE
                        || command.getEntityType() == CatalogEntityType.SPU
                        || command.getEntityType() == CatalogEntityType.SKU,
                "Catalog metadata update only supports STYLE, SPU, and SKU");
        requireUuid(command.getEntityId(), "entityId");
        require(command.getExpectedVersion() != null && command.getExpectedVersion() >= 1,
                "expectedVersion must be positive");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireText(command.getReason(), "reason", 512);
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");

        return switch (command.getEntityType()) {
            case STYLE -> {
                String styleCode = code(command.getStyleCode(), "styleCode", 64);
                String season = upper(command.getSeasonCode());
                require(SEASONS.contains(season), "seasonCode is invalid");
                require(command.getPlanningYear() != null && command.getPlanningYear() >= 2000
                        && command.getPlanningYear() <= 2100, "planningYear must be between 2000 and 2100");
                requireSourceRef(command.getPlanningCategoryRef(), "planningCategoryRef");
                requireSourceRef(command.getBrandRef(), "brandRef");
                requireText(command.getStyleName(), "styleName", 255);
                yield new NormalizedMetadataCommand(command.getEntityType(), command.getEntityId(),
                        command.getExpectedVersion(), command.getIdempotencyKey().trim(), command.getReason().trim(),
                        styleCode, command.getStyleName().trim(), command.getPlanningCategoryRef().trim(),
                        command.getBrandRef().trim(), command.getPlanningYear(), season, blankToNull(command.getWaveCode()),
                        null, null, null, null, command.getCorrelationId(), command.getCausationId(),
                        command.getOccurredAt());
            }
            case SPU -> {
                String spuCode = code(command.getSpuCode(), "spuCode", 64);
                requireText(command.getProductName(), "productName", 255);
                requireSourceRef(command.getSalesCategoryRef(), "salesCategoryRef");
                yield new NormalizedMetadataCommand(command.getEntityType(), command.getEntityId(),
                        command.getExpectedVersion(), command.getIdempotencyKey().trim(), command.getReason().trim(),
                        null, null, null, null, null, null, null, spuCode, command.getProductName().trim(),
                        command.getSalesCategoryRef().trim(), null, command.getCorrelationId(),
                        command.getCausationId(), command.getOccurredAt());
            }
            case SKU -> new NormalizedMetadataCommand(command.getEntityType(), command.getEntityId(),
                    command.getExpectedVersion(), command.getIdempotencyKey().trim(), command.getReason().trim(),
                    null, null, null, null, null, null, null, null, null, null,
                    code(command.getSkuCode(), "skuCode", 64), command.getCorrelationId(),
                    command.getCausationId(), command.getOccurredAt());
            default -> throw new IllegalArgumentException("Catalog metadata update only supports STYLE, SPU, and SKU");
        };
    }

    private static NormalizedBarcodeRotateCommand normalize(CatalogBarcodeRotateCommand command) {
        require(command != null, "Catalog barcode rotation command is required");
        requireUuid(command.getSkuId(), "skuId");
        require(command.getExpectedVersion() != null && command.getExpectedVersion() >= 1,
                "expectedVersion must be positive");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireText(command.getReason(), "reason", 512);
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
        String barcodeType = upper(command.getBarcodeType());
        require(BARCODE_TYPES.contains(barcodeType), "barcodeType is invalid");
        requireText(command.getBarcode(), "barcode", 64);
        return new NormalizedBarcodeRotateCommand(command.getSkuId(), command.getExpectedVersion(),
                command.getIdempotencyKey().trim(), command.getReason().trim(), command.getBarcode().trim(),
                barcodeType, command.getCorrelationId(), command.getCausationId(), command.getOccurredAt());
    }

    private static String fingerprint(Long tenantId, NormalizedCommand command) {
        return DigestUtil.sha256Hex(tenantId + "\u001f" + JsonUtils.toJsonString(command));
    }

    private static String code(String value, String field, int maxLength) {
        requireText(value, field, maxLength);
        return upper(value.trim());
    }

    private static void requireSourceRef(String value, String field) {
        requireText(value, field, 128);
        require(value.matches("[A-Z0-9_-]+:[A-Z0-9_-]+:.+"), field + " must be source-qualified");
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : upper(value.trim());
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static void requireUuid(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(field + " must be a UUID", error);
        }
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.trim().length() <= maxLength,
                field + " is required or too long");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static <T> T requireNonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private record NormalizedCommand(String idempotencyKey, String styleCode, String styleName,
                                     String planningCategoryRef, String brandRef, Integer planningYear,
                                     String seasonCode, String waveCode, String spuCode, String productName,
                                     String salesCategoryRef, String skuCode, String barcode, String barcodeType,
                                     String colorCode, String colorName, String sizeGroupCode, String sizeGroupName,
                                     String sizeCode, String sizeName, Integer sizeSort, String baseUomCode,
                                     String correlationId, String causationId, java.time.Instant occurredAt) {
    }

    private record ResolvedSku(CatalogSkuDO sku, boolean created) {
    }

    private record MetadataOutcome(String entityId, String businessCode, Integer status,
                                   Long aggregateVersion, boolean changed) {
    }

    private record NormalizedLifecycleCommand(CatalogEntityType entityType, String entityId,
                                              CatalogLifecycleAction action, Long expectedVersion,
                                              String idempotencyKey, String reason, String correlationId,
                                              String causationId, java.time.Instant occurredAt) {
    }

    private record NormalizedMetadataCommand(CatalogEntityType entityType, String entityId, Long expectedVersion,
                                             String idempotencyKey, String reason, String styleCode,
                                             String styleName, String planningCategoryRef, String brandRef,
                                             Integer planningYear, String seasonCode, String waveCode,
                                             String spuCode, String productName, String salesCategoryRef,
                                             String skuCode, String correlationId, String causationId,
                                             java.time.Instant occurredAt) {
    }

    private record NormalizedBarcodeRotateCommand(String skuId, Long expectedVersion, String idempotencyKey,
                                                  String reason, String barcode, String barcodeType,
                                                  String correlationId, String causationId,
                                                  java.time.Instant occurredAt) {
    }

    private record LifecycleEntity(String id, String businessCode, Integer status, Long version, Object value) {
    }
}
