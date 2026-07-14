package cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class AppendDomainEventResult {

    String eventId;
    String payloadHash;
    boolean duplicate;

}
