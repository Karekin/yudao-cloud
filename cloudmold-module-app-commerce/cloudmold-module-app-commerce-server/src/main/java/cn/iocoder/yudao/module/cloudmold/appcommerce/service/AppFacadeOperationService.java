package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppFacadeOperationDO;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.mysql.AppFacadeOperationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AppFacadeOperationService {

    private final AppFacadeOperationMapper mapper;

    public <T> Replay<T> execute(String operationType, String idempotencyKey, String principalId,
                                 Object payload, Class<T> resultType, Operation<T> operation) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("tenantId", tenantId);
        envelope.put("principalId", principalId);
        envelope.put("operationType", operationType);
        envelope.put("payload", payload);
        String requestHash = DigestUtil.sha256Hex(JsonUtils.toJsonString(envelope));
        String attemptToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        mapper.insertOrResolve(tenantId, principalId, operationType, idempotencyKey,
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve App facade operation");
        AppFacadeOperationDO row = mapper.selectForUpdate(tenantId, operationId);
        require(row != null, "App facade operation disappeared");
        if (!attemptToken.equals(row.getAttemptToken())) {
            require(Objects.equals(row.getRequestHash(), requestHash),
                    "idempotency key conflicts with a different App operation payload");
            require(row.getStatus() == 10 && row.getResultJson() != null,
                    "existing App facade operation is not complete");
            return new Replay<>(JsonUtils.parseObject(row.getResultJson(), resultType), true);
        }
        T result = operation.run(row.getFirstOccurredAt().toInstant(ZoneOffset.UTC));
        require(mapper.markSucceeded(tenantId, operationId, attemptToken,
                JsonUtils.toJsonString(result), LocalDateTime.now(ZoneOffset.UTC)) == 1,
                "App facade operation completion conflict");
        return new Replay<>(result, false);
    }

    @FunctionalInterface
    public interface Operation<T> {
        T run(Instant firstOccurredAt);
    }

    public record Replay<T>(T value, boolean duplicate) {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
