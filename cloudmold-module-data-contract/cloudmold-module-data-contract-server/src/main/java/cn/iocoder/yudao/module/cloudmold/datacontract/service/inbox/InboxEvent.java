package cn.iocoder.yudao.module.cloudmold.datacontract.service.inbox;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InboxEvent {

    String consumerId;
    String eventId;
    Long tenantId;
    String eventType;
    Integer schemaVersion;
    String payloadHash;

}
