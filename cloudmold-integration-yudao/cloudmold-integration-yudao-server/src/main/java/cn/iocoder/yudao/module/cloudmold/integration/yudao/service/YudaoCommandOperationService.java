package cn.iocoder.yudao.module.cloudmold.integration.yudao.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.dal.YudaoCommandOperationMapper;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.dal.YudaoCommandOperationRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Makes adapter commands safe for Skill timeout retries. The ledger and the
 * upstream yudao mutation commit in one database transaction.
 */
@Service
@RequiredArgsConstructor
public class YudaoCommandOperationService {

    private static final int SUCCEEDED = 10;
    private final YudaoCommandOperationMapper mapper;

    @Transactional(rollbackFor = Exception.class)
    public Long executeLong(String operationType, String idempotencyKey, Object command, Supplier<Long> action) {
        return execute(operationType, idempotencyKey, command, Long.class, action);
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean executeBoolean(String operationType, String idempotencyKey, Object command,
                                  Supplier<Boolean> action) {
        return execute(operationType, idempotencyKey, command, Boolean.class, action);
    }

    private <T> T execute(String operationType, String idempotencyKey, Object command, Class<T> resultType,
                          Supplier<T> action) {
        requireText(operationType, "operationType", 64);
        requireText(idempotencyKey, "idempotencyKey", 128);
        if (idempotencyKey.length() < 8) {
            throw new IllegalArgumentException("idempotencyKey is too short");
        }
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(action, "action");

        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(tenantId + "\u001f" + operationType + "\u001f"
                + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolve(tenantId, operationType, idempotencyKey, requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        if (operationId == null || operationId <= 0) {
            throw new IllegalStateException("failed to resolve yudao command operation");
        }
        YudaoCommandOperationRow operation = mapper.selectForUpdate(operationId, tenantId);
        if (operation == null) {
            throw new IllegalStateException("yudao command operation disappeared");
        }
        if (!attemptToken.equals(operation.getAttemptToken())) {
            if (!Objects.equals(operation.getRequestHash(), requestHash)) {
                throw new IllegalArgumentException("idempotency key conflicts with a different yudao command payload");
            }
            if (operation.getStatus() != SUCCEEDED || operation.getResultJson() == null) {
                throw new IllegalStateException("existing yudao command operation is not complete");
            }
            return JsonUtils.parseObject(operation.getResultJson(), resultType);
        }

        T result = Objects.requireNonNull(action.get(), "command result");
        if (mapper.markSucceeded(operationId, tenantId, JsonUtils.toJsonString(result), now) != 1) {
            throw new IllegalStateException("yudao command operation completion conflict");
        }
        return result;
    }

    private static void requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must contain 1.." + maxLength + " characters");
        }
    }
}
