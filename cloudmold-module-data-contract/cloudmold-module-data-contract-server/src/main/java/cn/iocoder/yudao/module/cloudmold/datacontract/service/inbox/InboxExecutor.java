package cn.iocoder.yudao.module.cloudmold.datacontract.service.inbox;

import cn.iocoder.yudao.module.cloudmold.datacontract.dal.dataobject.CloudmoldEventInboxDO;
import cn.iocoder.yudao.module.cloudmold.datacontract.dal.mysql.CloudmoldEventInboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class InboxExecutor {

    private final CloudmoldEventInboxMapper inboxMapper;

    /**
     * The Inbox claim, side effect, and completion execute in one database transaction.
     * Call this method through the Spring bean proxy; do not self-invoke it.
     */
    @Transactional(rollbackFor = Exception.class)
    public <T> InboxExecutionResult<T> execute(InboxEvent event, Supplier<T> sideEffect,
                                                Function<T, String> resultHasher) {
        validate(event, sideEffect, resultHasher);
        CloudmoldEventInboxDO existing = inboxMapper.selectOne(event.getConsumerId(), event.getEventId());
        if (existing != null) {
            verifySameEvent(existing, event);
            return new InboxExecutionResult<>(true, null, existing.getResultHash());
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        CloudmoldEventInboxDO claim = new CloudmoldEventInboxDO()
                .setConsumerId(event.getConsumerId()).setEventId(event.getEventId())
                .setTenantId(event.getTenantId()).setEventType(event.getEventType())
                .setSchemaVersion(event.getSchemaVersion()).setPayloadHash(event.getPayloadHash())
                .setProcessedAt(now);
        try {
            inboxMapper.insertClaim(claim);
        } catch (DuplicateKeyException concurrentDuplicate) {
            CloudmoldEventInboxDO concurrent = inboxMapper.selectOne(event.getConsumerId(), event.getEventId());
            if (concurrent == null) {
                throw concurrentDuplicate;
            }
            verifySameEvent(concurrent, event);
            return new InboxExecutionResult<>(true, null, concurrent.getResultHash());
        }

        T value = sideEffect.get();
        String resultHash = resultHasher.apply(value);
        if (inboxMapper.complete(event.getConsumerId(), event.getEventId(), resultHash, now) != 1) {
            throw new IllegalStateException("inbox completion lost for event " + event.getEventId());
        }
        return new InboxExecutionResult<>(false, value, resultHash);
    }

    private static void verifySameEvent(CloudmoldEventInboxDO existing, InboxEvent event) {
        if (!Objects.equals(existing.getTenantId(), event.getTenantId())
                || !Objects.equals(existing.getEventType(), event.getEventType())
                || !Objects.equals(existing.getSchemaVersion(), event.getSchemaVersion())
                || !Objects.equals(existing.getPayloadHash(), event.getPayloadHash())) {
            throw new IllegalStateException("Inbox identity conflict with different event content");
        }
    }

    private static <T> void validate(InboxEvent event, Supplier<T> sideEffect, Function<T, String> resultHasher) {
        if (event == null || isBlank(event.getConsumerId()) || isBlank(event.getEventId())
                || event.getTenantId() == null || event.getTenantId() <= 0
                || isBlank(event.getEventType()) || event.getSchemaVersion() == null || event.getSchemaVersion() <= 0
                || event.getPayloadHash() == null || !event.getPayloadHash().matches("^[a-f0-9]{64}$")) {
            throw new IllegalArgumentException("invalid Inbox event identity");
        }
        if (sideEffect == null || resultHasher == null) {
            throw new IllegalArgumentException("sideEffect and resultHasher are required");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
