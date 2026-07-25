package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppAddressSnapshotDO;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.mysql.AppAddressSnapshotMapper;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.member.api.address.MemberAddressApi;
import cn.iocoder.yudao.module.member.api.address.dto.MemberAddressRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppAddressVaultService {
    private static final long SNAPSHOT_VERSION = 1L;

    private final AppMemberPrincipalResolver principalResolver;
    private final MemberAddressApi memberAddressApi;
    private final AppAddressSnapshotMapper mapper;
    private final AppAddressVaultCrypto crypto;
    private final OutboxAppender outboxAppender;

    @Transactional(rollbackFor = Exception.class)
    public AppAddressSnapshotView createSnapshot(String idempotencyKey, Long sourceAddressId) {
        requireKey(idempotencyKey);
        require(sourceAddressId != null && sourceAddressId > 0, "sourceAddressId is required");
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        MemberAddressRespDTO address =
                memberAddressApi.getAddress(sourceAddressId, principal.getMemberUserId()).getCheckedData();
        require(address != null
                        && Objects.equals(address.getUserId(), principal.getMemberUserId()),
                "member address does not exist");
        require(address.getAreaId() != null && address.getAreaId() > 0,
                "member address has no governed destination region");
        validatePii(address);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String piiJson = JsonUtils.toJsonString(address);
        String sourceFingerprint =
                crypto.fingerprint(piiJson.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("tenantId", tenantId);
        fingerprint.put("principalId", principal.getPrincipalId());
        fingerprint.put("sourceAddressId", sourceAddressId);
        fingerprint.put("sourceFingerprintSha256", sourceFingerprint);
        String requestHash = DigestUtil.sha256Hex(JsonUtils.toJsonString(fingerprint));
        String addressRef = UUID.randomUUID().toString();
        AppAddressVaultCrypto.Encrypted encrypted = crypto.encrypt(
                aad(tenantId, principal.getPrincipalId(), addressRef, SNAPSHOT_VERSION),
                piiJson.getBytes(StandardCharsets.UTF_8));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        AppAddressSnapshotDO row = new AppAddressSnapshotDO()
                .setAddressRef(addressRef).setTenantId(tenantId)
                .setOwnerPrincipalId(principal.getPrincipalId())
                .setMemberUserId(principal.getMemberUserId()).setSourceAddressId(sourceAddressId)
                .setIdempotencyKey(idempotencyKey).setRequestHash(requestHash)
                .setSourceFingerprintSha256(sourceFingerprint).setSnapshotVersion(SNAPSHOT_VERSION)
                .setDestinationRegionCode(Integer.toString(address.getAreaId()))
                .setKeyId(encrypted.keyId())
                .setInitializationVector(encrypted.initializationVector())
                .setCiphertext(encrypted.ciphertext()).setStatus("ACTIVE")
                .setCreatedAt(now).setUpdatedAt(now);
        boolean inserted = mapper.insertIgnore(row) == 1;
        if (!inserted) {
            AppAddressSnapshotDO existing = mapper.selectByIdempotency(
                    tenantId, principal.getPrincipalId(), idempotencyKey);
            require(existing != null && Objects.equals(existing.getRequestHash(), requestHash),
                    "idempotency key conflicts with a different address snapshot");
            return toView(existing, address, true);
        }
        appendCreated(row);
        return toView(row, address, false);
    }

    public AppAddressSnapshotView requireOwned(String addressRef, String principalId) {
        require(addressRef != null && !addressRef.isBlank(), "addressRef is required");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        AppAddressSnapshotDO row = mapper.selectOwned(tenantId, addressRef, principalId);
        require(row != null, "Address Vault snapshot does not exist");
        MemberAddressRespDTO address = decrypt(row);
        return toView(row, address, false);
    }

    private MemberAddressRespDTO decrypt(AppAddressSnapshotDO row) {
        byte[] plaintext = crypto.decrypt(
                aad(row.getTenantId(), row.getOwnerPrincipalId(),
                        row.getAddressRef(), row.getSnapshotVersion()),
                row.getKeyId(), row.getInitializationVector(), row.getCiphertext());
        MemberAddressRespDTO address = JsonUtils.parseObject(
                new String(plaintext, StandardCharsets.UTF_8), MemberAddressRespDTO.class);
        require(address != null
                        && Objects.equals(address.getUserId(), row.getMemberUserId())
                        && Objects.equals(address.getId(), row.getSourceAddressId()),
                "Address Vault ownership evidence is invalid");
        return address;
    }

    private void appendCreated(AppAddressSnapshotDO row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("address_ref", row.getAddressRef());
        payload.put("owner_principal_id", row.getOwnerPrincipalId());
        payload.put("snapshot_version", row.getSnapshotVersion());
        payload.put("destination_region_code", row.getDestinationRegionCode());
        payload.put("status", row.getStatus());
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType("app_commerce.address_snapshot.created").schemaVersion(1)
                .sourceSystem("cloudmold-app-commerce").tenantId(row.getTenantId())
                .aggregateType("app_address_snapshot").aggregateId(row.getAddressRef())
                .aggregateVersion(row.getSnapshotVersion()).eventSequence((short) 1)
                .occurredAt(row.getCreatedAt().toInstant(ZoneOffset.UTC))
                .traceId(row.getAddressRef()).correlationId(row.getAddressRef())
                .idempotencyKey("app-address:" + row.getAddressRef() + ":event:1")
                .payload(payload).headers(Map.of("pii_safe", true, "tokenized", true))
                .destination("lakehouse").build());
    }

    private static AppAddressSnapshotView toView(
            AppAddressSnapshotDO row, MemberAddressRespDTO address, boolean duplicate) {
        return AppAddressSnapshotView.builder()
                .addressRef(row.getAddressRef()).snapshotVersion(row.getSnapshotVersion())
                .destinationRegionCode(row.getDestinationRegionCode())
                .receiverSummary(maskName(address.getName()))
                .mobileSummary(maskMobile(address.getMobile()))
                .duplicate(duplicate).build();
    }

    private static String aad(Long tenantId, String principalId, String addressRef, Long version) {
        return tenantId + "|" + principalId + "|" + addressRef + "|" + version + "|DELIVERY";
    }

    private static String maskName(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? "*" : "*" + trimmed.substring(trimmed.length() - 1);
    }

    private static String maskMobile(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() < 7) {
            return "****";
        }
        return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private static void validatePii(MemberAddressRespDTO value) {
        require(value.getName() != null && !value.getName().isBlank(),
                "member address receiver is incomplete");
        require(value.getMobile() != null && !value.getMobile().isBlank(),
                "member address mobile is incomplete");
        require(value.getDetailAddress() != null && !value.getDetailAddress().isBlank(),
                "member address detail is incomplete");
    }

    private static void requireKey(String value) {
        require(value != null && value.length() >= 8 && value.length() <= 128,
                "idempotencyKey length must be between 8 and 128");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
