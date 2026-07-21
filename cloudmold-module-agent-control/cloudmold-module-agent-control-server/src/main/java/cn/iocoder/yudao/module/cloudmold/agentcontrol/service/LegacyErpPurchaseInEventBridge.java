package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlResult;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.MissionEventCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.LegacyPurchaseInBridgeState;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.LegacyPurchaseInFact;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@ConditionalOnProperty(prefix = "cloudmold.agent-control", name = "enabled", havingValue = "true")
@Service
public class LegacyErpPurchaseInEventBridge {

    static final String EVENT_TYPE = "legacy.erp.purchase_in.status_changed";
    private final AgentControlStoreMapper mapper;
    private final MissionRuntimeService runtime;
    private final Clock clock;

    @Autowired
    public LegacyErpPurchaseInEventBridge(AgentControlStoreMapper mapper, MissionRuntimeService runtime) {
        this(mapper, runtime, Clock.systemUTC());
    }

    LegacyErpPurchaseInEventBridge(AgentControlStoreMapper mapper, MissionRuntimeService runtime, Clock clock) {
        this.mapper = mapper;
        this.runtime = runtime;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentControlResult capture(Long purchaseInId, String correlationId) {
        require(purchaseInId != null && purchaseInId > 0, "purchaseInId is required");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LegacyPurchaseInFact fact = requireNonNull(mapper.selectLegacyPurchaseInFact(tenantId, purchaseInId),
                "legacy ERP purchase-in does not exist");
        require(fact.getSourceUpdatedAt() != null && fact.getStatus() != null,
                "legacy ERP purchase-in has incomplete lifecycle facts");
        LegacyPurchaseInBridgeState previous = mapper.selectLegacyPurchaseInBridgeForUpdate(tenantId, purchaseInId);
        if (previous != null && Objects.equals(previous.getObservedStatus(), fact.getStatus())
                && Objects.equals(previous.getSourceUpdatedAt(), fact.getSourceUpdatedAt())) {
            return AgentControlResult.builder().aggregateType("legacy_erp_purchase_in")
                    .aggregateId(String.valueOf(purchaseInId)).aggregateVersion(1L).status("DUPLICATE")
                    .duplicate(true).build();
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        String eventId = DigestUtil.sha256Hex(String.join("|", String.valueOf(tenantId),
                String.valueOf(purchaseInId), String.valueOf(fact.getStatus()), fact.getSourceUpdatedAt().toString()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("adapterMode", "LEGACY_ERP_ADAPTER");
        payload.put("authority", "LOCAL_TEST");
        payload.put("purchaseInId", fact.getPurchaseInId());
        payload.put("purchaseInNo", fact.getPurchaseInNo());
        payload.put("status", fact.getStatus());
        payload.put("supplierId", fact.getSupplierId());
        payload.put("purchaseOrderId", fact.getOrderId());
        payload.put("totalCount", fact.getTotalCount());
        payload.put("sourceUpdatedAt", fact.getSourceUpdatedAt().toString());
        String payloadJson = JsonUtils.toJsonString(payload);
        String payloadSha256 = DigestUtil.sha256Hex(payloadJson);
        require(mapper.upsertLegacyPurchaseInBridge(new LegacyPurchaseInBridgeState().setTenantId(tenantId)
                .setPurchaseInId(purchaseInId).setObservedStatus(fact.getStatus())
                .setSourceUpdatedAt(fact.getSourceUpdatedAt()).setEventId(eventId).setCapturedAt(now)) > 0,
                "failed to advance legacy ERP bridge cursor");
        require(mapper.insertAgentOutbox(eventId, tenantId, "legacy_erp_purchase_in", String.valueOf(purchaseInId),
                EVENT_TYPE, payloadJson, now) == 1, "failed to append legacy ERP purchase-in Outbox event");
        return runtime.matchEvent(MissionEventCommand.builder().tenantId(tenantId).eventId(eventId)
                .eventType(EVENT_TYPE).schemaVersion("v1").sourceSystem("legacy-erp")
                .aggregateType("purchase_in").aggregateId(String.valueOf(purchaseInId))
                .correlationId(correlationId).payloadSha256(payloadSha256).build());
    }

    private static <T> T requireNonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
