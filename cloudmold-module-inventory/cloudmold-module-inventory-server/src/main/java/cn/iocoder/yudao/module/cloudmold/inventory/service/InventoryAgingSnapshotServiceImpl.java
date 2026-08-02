package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryAgingSnapshotApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryAgingSnapshotCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryAgingSnapshotLineView;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryAgingSnapshotQueryApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryAgingSnapshotResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryAgingSnapshotView;
import cn.iocoder.yudao.module.cloudmold.inventory.controller.admin.vo.InventoryAgingSnapshotPageReqVO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryAgingSnapshotDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryAgingSnapshotLineDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryAgingSnapshotOperationDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryAgingSnapshotMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.service.query.InventoryAgingSnapshotCaptureItem;
import cn.iocoder.yudao.module.cloudmold.inventory.service.query.InventoryAgingSnapshotWatermark;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryAgingSnapshotServiceImpl
        implements InventoryAgingSnapshotApi, InventoryAgingSnapshotQueryApi {

    private static final int OPERATION_SUCCEEDED = 10;
    private static final String SOURCE_SYSTEM = "cloudmold-inventory";
    private static final BigDecimal ZERO = new BigDecimal("0.000000");

    private final InventoryAgingSnapshotMapper mapper;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public InventoryAgingSnapshotResult capture(InventoryAgingSnapshotCommand command) {
        Command normalized = normalize(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(normalized.occurredAt(), ZoneOffset.UTC);
        String requestHash = requestHash(normalized);
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, normalized.idempotencyKey(), normalized.sourceEventId(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve inventory aging snapshot operation");
        InventoryAgingSnapshotOperationDO operation = mapper.selectOperationForUpdate(tenantId, operationId);
        require(operation != null, "inventory aging snapshot operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "inventory aging snapshot idempotency payload conflict");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing inventory aging snapshot operation is incomplete");
            InventoryAgingSnapshotResult replay =
                    JsonUtils.parseObject(operation.getResultJson(), InventoryAgingSnapshotResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        List<InventoryAgingSnapshotCaptureItem> items =
                mapper.selectCaptureItems(tenantId, normalized.ownerType(), normalized.ownerId(), normalized.warehouseId());
        InventoryAgingSnapshotWatermark watermark =
                mapper.selectWatermark(tenantId, normalized.ownerType(), normalized.ownerId(), normalized.warehouseId());
        require(watermark != null && watermark.getLedgerEntryCount() != null && watermark.getLedgerEntryCount() > 0
                        && watermark.getMaxLedgerEntryId() != null && watermark.getMaxLedgerEntryAt() != null,
                "inventory aging snapshot scope has no ledger watermark");
        String policyHash = canonicalPolicyHash(normalized);
        String snapshotId = snapshotId(tenantId, normalized, watermark, policyHash);
        String snapshotCode = snapshotCode(normalized.snapshotDate(), snapshotId);
        String ledgerWatermarkRef = ledgerWatermarkRef(tenantId, normalized, watermark);
        InventoryAgingSnapshotDO existingSnapshot = mapper.selectSnapshotHeadForUpdate(tenantId, snapshotId);
        if (existingSnapshot != null) {
            InventoryAgingSnapshotResult result = InventoryAgingSnapshotResult.builder()
                    .operationId(operationId)
                    .snapshotId(existingSnapshot.getSnapshotId())
                    .snapshotCode(existingSnapshot.getSnapshotCode())
                    .duplicate(true)
                    .snapshotVersion(existingSnapshot.getVersion())
                    .lineCount(existingSnapshot.getLineCount())
                    .status(existingSnapshot.getStatus())
                    .build();
            require(mapper.markOperationSucceeded(tenantId, operationId, JsonUtils.toJsonString(result), now) == 1,
                    "inventory aging snapshot completion conflict");
            return result;
        }
        int unknownAgeCount = 0;
        int unknownExpiryCount = 0;
        List<InventoryAgingSnapshotLineDO> lines = new ArrayList<>(items.size());
        for (InventoryAgingSnapshotCaptureItem item : items) {
            SnapshotLine computed = computeLine(snapshotId, tenantId, normalized.snapshotDate(), normalized, item, now);
            lines.add(computed.line());
            if ("UNKNOWN".equals(computed.line().getAgeBucket())) {
                unknownAgeCount++;
            }
            if ("UNKNOWN".equals(computed.line().getExpiryStatus())) {
                unknownExpiryCount++;
            }
        }

        InventoryAgingSnapshotDO snapshot = new InventoryAgingSnapshotDO();
        snapshot.setSnapshotId(snapshotId);
        snapshot.setTenantId(tenantId);
        snapshot.setSnapshotCode(snapshotCode);
        snapshot.setOwnerType(normalized.ownerType());
        snapshot.setOwnerId(normalized.ownerId());
        snapshot.setWarehouseId(normalized.warehouseId());
        snapshot.setBucketPolicyCode(normalized.bucketPolicyCode());
        snapshot.setBucketPolicyVersion(normalized.bucketPolicyVersion());
        snapshot.setBucketPolicyHash(policyHash);
        snapshot.setAgeFreshMaxDays(normalized.ageFreshMaxDays());
        snapshot.setAgeAgingMaxDays(normalized.ageAgingMaxDays());
        snapshot.setAgeStaleMaxDays(normalized.ageStaleMaxDays());
        snapshot.setExpiryWarningMaxDays(normalized.expiryWarningMaxDays());
        snapshot.setExpiryCriticalMaxDays(normalized.expiryCriticalMaxDays());
        snapshot.setLedgerWatermarkRef(ledgerWatermarkRef);
        snapshot.setLedgerWatermarkOccurredAt(watermark.getMaxLedgerEntryAt());
        snapshot.setSnapshotDate(normalized.snapshotDate());
        snapshot.setLineCount(lines.size());
        snapshot.setUnknownAgeCount(unknownAgeCount);
        snapshot.setUnknownExpiryCount(unknownExpiryCount);
        snapshot.setStatus("CAPTURED");
        snapshot.setVersion(1L);
        snapshot.setCreatedAt(now);
        require(mapper.insertSnapshot(snapshot) == 1, "failed to persist inventory aging snapshot");
        for (InventoryAgingSnapshotLineDO line : lines) {
            require(mapper.insertSnapshotLine(line) == 1, "failed to persist inventory aging snapshot line");
        }
        appendSnapshotCapturedEvent(tenantId, normalized, snapshot, operationId);
        InventoryAgingSnapshotResult result = InventoryAgingSnapshotResult.builder()
                .operationId(operationId)
                .snapshotId(snapshotId)
                .snapshotCode(snapshotCode)
                .duplicate(false)
                .snapshotVersion(1L)
                .lineCount(lines.size())
                .status(snapshot.getStatus())
                .build();
        require(mapper.markOperationSucceeded(tenantId, operationId, JsonUtils.toJsonString(result), now) == 1,
                "inventory aging snapshot completion conflict");
        return result;
    }

    @Override
    public InventoryAgingSnapshotView requireSnapshot(String snapshotId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String id = requireText(snapshotId, "snapshotId");
        InventoryAgingSnapshotView snapshot = mapper.selectSnapshot(tenantId, id);
        require(snapshot != null, "inventory aging snapshot not found");
        List<InventoryAgingSnapshotLineView> lines = mapper.selectSnapshotLines(tenantId, id);
        snapshot.setLines(lines);
        return snapshot;
    }

    public PageResult<InventoryAgingSnapshotView> getSnapshotPage(InventoryAgingSnapshotPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String keyword = normalizeNullableText(request.getKeyword());
        long total = mapper.countSnapshotPage(tenantId, keyword);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(mapper.selectSnapshotPage(tenantId, keyword, offset, request.getPageSize()), total);
    }

    private SnapshotLine computeLine(String snapshotId, Long tenantId, LocalDate snapshotDate, Command command,
                                     InventoryAgingSnapshotCaptureItem item, LocalDateTime now) {
        LocalDateTime ageBasisAt;
        String ageBasisType;
        if (item.getLotReceivedAt() != null) {
            ageBasisAt = item.getLotReceivedAt();
            ageBasisType = "LOT_RECEIVED_AT";
        } else if (item.getFirstLedgerEntryAt() != null) {
            ageBasisAt = item.getFirstLedgerEntryAt();
            ageBasisType = "LEDGER_FIRST_ENTRY_AT";
        } else {
            ageBasisAt = null;
            ageBasisType = "UNKNOWN";
        }
        Integer ageDays = ageBasisAt == null ? null : Math.toIntExact(ChronoUnit.DAYS.between(ageBasisAt.toLocalDate(), snapshotDate));
        String ageBucket = classifyAgeBucket(ageDays, command);
        Integer expiryDaysRemaining =
                item.getExpiresOn() == null ? null : Math.toIntExact(ChronoUnit.DAYS.between(snapshotDate, item.getExpiresOn()));
        String expiryStatus = item.getExpiresOn() == null ? "UNKNOWN" : classifyExpiryStatus(expiryDaysRemaining, command);
        String expiryBucket = item.getExpiresOn() == null ? "UNKNOWN" : expiryStatus;
        String riskClassification = classifyRisk(item.getStockStatus(), item.getQualityStatus(), ageBucket, expiryStatus);
        BigDecimal onHand = scale(item.getOnHandQuantity());
        BigDecimal reserved = scale(item.getReservedQuantity());
        BigDecimal inTransit = scale(item.getInTransitQuantity());
        BigDecimal available = onHand.subtract(reserved).setScale(6, RoundingMode.HALF_UP);
        InventoryAgingSnapshotLineDO line = new InventoryAgingSnapshotLineDO();
        line.setTenantId(tenantId);
        line.setSnapshotId(snapshotId);
        line.setBalanceId(item.getBalanceId());
        line.setOwnerType(item.getOwnerType());
        line.setOwnerId(item.getOwnerId());
        line.setCanonicalSkuId(item.getCanonicalSkuId());
        line.setWarehouseId(item.getWarehouseId());
        line.setLocationId(item.getLocationId());
        line.setLotId(item.getLotId());
        line.setLotCode(item.getLotCode());
        line.setStockStatus(item.getStockStatus());
        line.setQualityStatus(item.getQualityStatus());
        line.setBaseUomCode(item.getBaseUomCode());
        line.setOnHandQuantity(onHand);
        line.setReservedQuantity(reserved);
        line.setInTransitQuantity(inTransit);
        line.setAvailableQuantity(available);
        line.setBalanceVersion(item.getBalanceVersion());
        line.setManufacturedOn(item.getManufacturedOn());
        line.setExpiresOn(item.getExpiresOn());
        line.setAgeBasisType(ageBasisType);
        line.setAgeBasisAt(ageBasisAt);
        line.setAgeDays(ageDays);
        line.setAgeBucket(ageBucket);
        line.setExpiryDaysRemaining(expiryDaysRemaining);
        line.setExpiryStatus(expiryStatus);
        line.setExpiryBucket(expiryBucket);
        line.setRiskClassification(riskClassification);
        line.setCreatedAt(now);
        return new SnapshotLine(line);
    }

    private void appendSnapshotCapturedEvent(Long tenantId, Command command, InventoryAgingSnapshotDO snapshot,
                                             Long operationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("snapshot_id", snapshot.getSnapshotId());
        payload.put("snapshot_code", snapshot.getSnapshotCode());
        payload.put("snapshot_date", snapshot.getSnapshotDate().toString());
        payload.put("line_count", snapshot.getLineCount());
        payload.put("unknown_age_count", snapshot.getUnknownAgeCount());
        payload.put("unknown_expiry_count", snapshot.getUnknownExpiryCount());
        payload.put("bucket_policy_code", snapshot.getBucketPolicyCode());
        payload.put("bucket_policy_version", snapshot.getBucketPolicyVersion());
        payload.put("bucket_policy_hash", snapshot.getBucketPolicyHash());
        payload.put("ledger_watermark_ref", snapshot.getLedgerWatermarkRef());
        payload.put("ledger_watermark_occurred_at", snapshot.getLedgerWatermarkOccurredAt().toInstant(ZoneOffset.UTC).toString());
        payload.put("operation_id", operationId);
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("inventory.aging_snapshot.captured")
                .schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM)
                .destination("lakehouse")
                .tenantId(tenantId)
                .aggregateType("inventory_aging_snapshot")
                .aggregateId(snapshot.getSnapshotId())
                .aggregateVersion(snapshot.getVersion())
                .eventSequence((short) 1)
                .occurredAt(command.occurredAt())
                .correlationId(command.correlationId())
                .causationId(command.sourceEventId())
                .idempotencyKey(command.idempotencyKey() + ":inventory.aging_snapshot.captured")
                .payload(payload)
                .build());
    }

    private static Command normalize(InventoryAgingSnapshotCommand command) {
        require(command != null, "inventory aging snapshot command is required");
        String idempotencyKey = requireText(command.getIdempotencyKey(), "idempotencyKey");
        String sourceEventId = requireText(command.getSourceEventId(), "sourceEventId");
        String correlationId = requireText(command.getCorrelationId(), "correlationId");
        Instant occurredAt = command.getOccurredAt();
        require(occurredAt != null, "occurredAt is required");
        String ownerType = normalizeNullableUpper(command.getOwnerType());
        String ownerId = normalizeNullableText(command.getOwnerId());
        if ((ownerType == null) != (ownerId == null)) {
            throw new IllegalArgumentException("ownerType and ownerId must appear together");
        }
        String warehouseId = normalizeNullableText(command.getWarehouseId());
        String bucketPolicyCode = requireUpper(command.getBucketPolicyCode(), "bucketPolicyCode");
        String bucketPolicyVersion = requireText(command.getBucketPolicyVersion(), "bucketPolicyVersion");
        Integer ageFreshMaxDays = requireNonNegative(command.getAgeFreshMaxDays(), "ageFreshMaxDays");
        Integer ageAgingMaxDays = requireNonNegative(command.getAgeAgingMaxDays(), "ageAgingMaxDays");
        Integer ageStaleMaxDays = requireNonNegative(command.getAgeStaleMaxDays(), "ageStaleMaxDays");
        require(ageFreshMaxDays <= ageAgingMaxDays && ageAgingMaxDays <= ageStaleMaxDays,
                "age bucket thresholds must be ascending");
        Integer expiryWarningMaxDays = requireNonNegative(command.getExpiryWarningMaxDays(), "expiryWarningMaxDays");
        Integer expiryCriticalMaxDays = requireNonNegative(command.getExpiryCriticalMaxDays(), "expiryCriticalMaxDays");
        require(expiryCriticalMaxDays <= expiryWarningMaxDays,
                "expiryCriticalMaxDays must be less than or equal to expiryWarningMaxDays");
        return new Command(idempotencyKey, sourceEventId, correlationId, occurredAt,
                ownerType, ownerId, warehouseId, bucketPolicyCode, bucketPolicyVersion,
                ageFreshMaxDays, ageAgingMaxDays, ageStaleMaxDays, expiryWarningMaxDays, expiryCriticalMaxDays,
                LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC).toLocalDate());
    }

    private static String classifyAgeBucket(Integer ageDays, Command command) {
        if (ageDays == null) {
            return "UNKNOWN";
        }
        if (ageDays <= command.ageFreshMaxDays()) {
            return "FRESH";
        }
        if (ageDays <= command.ageAgingMaxDays()) {
            return "AGING";
        }
        if (ageDays <= command.ageStaleMaxDays()) {
            return "STALE";
        }
        return "OBSOLETE";
    }

    private static String classifyExpiryStatus(Integer expiryDaysRemaining, Command command) {
        if (expiryDaysRemaining == null) {
            return "UNKNOWN";
        }
        if (expiryDaysRemaining < 0) {
            return "EXPIRED";
        }
        if (expiryDaysRemaining <= command.expiryCriticalMaxDays()) {
            return "CRITICAL";
        }
        if (expiryDaysRemaining <= command.expiryWarningMaxDays()) {
            return "WARNING";
        }
        return "HEALTHY";
    }

    private static String classifyRisk(String stockStatus, String qualityStatus, String ageBucket, String expiryStatus) {
        if ("CRITICAL".equals(expiryStatus) || "EXPIRED".equals(expiryStatus)) {
            return "EXPIRY_CRITICAL";
        }
        if ("REJECTED".equals(qualityStatus) || "DAMAGED".equals(qualityStatus)) {
            return "QUALITY_AT_RISK";
        }
        if ("NON_SELLABLE".equals(stockStatus)) {
            return "STOCK_RESTRICTED";
        }
        if ("OBSOLETE".equals(ageBucket)) {
            return "AGE_OBSOLETE";
        }
        if ("WARNING".equals(expiryStatus)) {
            return "EXPIRY_WARNING";
        }
        if ("STALE".equals(ageBucket) || "AGING".equals(ageBucket)) {
            return "AGE_ATTENTION";
        }
        if ("UNKNOWN".equals(ageBucket) || "UNKNOWN".equals(expiryStatus)) {
            return "UNKNOWN";
        }
        return "HEALTHY";
    }

    private static BigDecimal scale(BigDecimal value) {
        return (value == null ? ZERO : value).setScale(6, RoundingMode.HALF_UP);
    }

    private static String requireText(String value, String field) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static String requireUpper(String value, String field) {
        String normalized = requireText(value, field).toUpperCase(Locale.ROOT);
        require(normalized.matches("[A-Z][A-Z0-9_]{0,63}"), field + " is invalid");
        return normalized;
    }

    private static String requireHash(String value, String field) {
        String normalized = requireText(value, field);
        require(normalized.matches("[0-9a-f]{64}"), field + " must be a lowercase SHA-256 digest");
        return normalized;
    }

    private static String canonicalPolicyHash(Command command) {
        return DigestUtil.sha256Hex(String.join("\u001f",
                command.bucketPolicyCode(),
                command.bucketPolicyVersion(),
                Integer.toString(command.ageFreshMaxDays()),
                Integer.toString(command.ageAgingMaxDays()),
                Integer.toString(command.ageStaleMaxDays()),
                Integer.toString(command.expiryWarningMaxDays()),
                Integer.toString(command.expiryCriticalMaxDays())));
    }

    private static String requestHash(Command command) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("idempotencyKey", command.idempotencyKey());
        canonical.put("sourceEventId", command.sourceEventId());
        canonical.put("correlationId", command.correlationId());
        canonical.put("occurredAt", command.occurredAt().toString());
        canonical.put("ownerType", command.ownerType());
        canonical.put("ownerId", command.ownerId());
        canonical.put("warehouseId", command.warehouseId());
        canonical.put("bucketPolicyCode", command.bucketPolicyCode());
        canonical.put("bucketPolicyVersion", command.bucketPolicyVersion());
        canonical.put("ageFreshMaxDays", command.ageFreshMaxDays());
        canonical.put("ageAgingMaxDays", command.ageAgingMaxDays());
        canonical.put("ageStaleMaxDays", command.ageStaleMaxDays());
        canonical.put("expiryWarningMaxDays", command.expiryWarningMaxDays());
        canonical.put("expiryCriticalMaxDays", command.expiryCriticalMaxDays());
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(canonical));
    }

    private static String ledgerWatermarkRef(Long tenantId, Command command, InventoryAgingSnapshotWatermark watermark) {
        return "inventory-ledger-watermark:v3:"
                + tenantId + ":"
                + nullSafe(command.ownerType()) + ":"
                + nullSafe(command.ownerId()) + ":"
                + nullSafe(command.warehouseId()) + ":"
                + watermark.getMaxLedgerEntryId() + ":"
                + watermark.getMaxLedgerEntryAt().toInstant(ZoneOffset.UTC);
    }

    private static String snapshotId(Long tenantId, Command command, InventoryAgingSnapshotWatermark watermark,
                                     String policyHash) {
        String material = String.join("\u001f",
                tenantId.toString(),
                nullSafe(command.ownerType()),
                nullSafe(command.ownerId()),
                nullSafe(command.warehouseId()),
                command.bucketPolicyCode(),
                command.bucketPolicyVersion(),
                policyHash,
                watermark.getMaxLedgerEntryId().toString(),
                watermark.getMaxLedgerEntryAt().toInstant(ZoneOffset.UTC).toString());
        return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String snapshotCode(LocalDate snapshotDate, String snapshotId) {
        String compactDate = snapshotDate.toString().replace("-", "");
        String suffix = snapshotId.replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "IAS-" + compactDate + "-" + suffix;
    }

    private static String nullSafe(String value) {
        return value == null ? "*" : value;
    }

    private static Integer requireNonNegative(Integer value, String field) {
        require(value != null && value >= 0, field + " must not be negative");
        return value;
    }

    private static String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeNullableUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private record Command(String idempotencyKey,
                           String sourceEventId,
                           String correlationId,
                           Instant occurredAt,
                           String ownerType,
                           String ownerId,
                           String warehouseId,
                           String bucketPolicyCode,
                           String bucketPolicyVersion,
                           Integer ageFreshMaxDays,
                           Integer ageAgingMaxDays,
                           Integer ageStaleMaxDays,
                           Integer expiryWarningMaxDays,
                           Integer expiryCriticalMaxDays,
                           LocalDate snapshotDate) {
    }

    private record SnapshotLine(InventoryAgingSnapshotLineDO line) {
    }
}
