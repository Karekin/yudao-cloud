package cn.iocoder.yudao.module.cloudmold.datacontract.service.outbox;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomainEventCanonicalizerTest {

    @Test
    void shouldProduceStableHashRegardlessOfMapInsertionOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("sku", "sku-1");
        first.put("balance", Map.of("reserved", "0", "onHand", "10"));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("balance", Map.of("onHand", "10", "reserved", "0"));
        second.put("sku", "sku-1");

        DomainEventCanonicalizer.CanonicalPayload firstPayload = DomainEventCanonicalizer.canonicalize(first);
        DomainEventCanonicalizer.CanonicalPayload secondPayload = DomainEventCanonicalizer.canonicalize(second);

        assertEquals(firstPayload.json(), secondPayload.json());
        assertEquals(firstPayload.sha256(), secondPayload.sha256());
    }

}
