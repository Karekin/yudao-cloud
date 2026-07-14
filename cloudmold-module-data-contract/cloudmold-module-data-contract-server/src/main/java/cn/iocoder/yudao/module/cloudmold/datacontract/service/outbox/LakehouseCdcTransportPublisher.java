package cn.iocoder.yudao.module.cloudmold.datacontract.service.outbox;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Releases a durable Outbox row to the MySQL CDC transport.
 *
 * <p>Flink reads the same committed row from the binlog, so no second network send is required here.
 * Marking the lease PUBLISHED is the explicit projection gate; it is not proof that a downstream model
 * has consumed the event. Lakehouse reconciliation remains responsible for that proof.</p>
 */
@Component
public class LakehouseCdcTransportPublisher implements EventTransportPublisher {

    /**
     * Canonical destinations whose transport is the committed MySQL Outbox row itself.
     *
     * <p>{@code catalog-events} is retained for compatibility with the first canonical Catalog
     * slice. Newer canonical modules use {@code lakehouse}. Both are consumed through the same
     * binlog transport and therefore share the same acknowledgement semantics.</p>
     */
    static final Set<String> CDC_DESTINATIONS = Set.of("lakehouse", "catalog-events");

    @Override
    public boolean supports(String destination) {
        return CDC_DESTINATIONS.contains(destination);
    }

    @Override
    public void publish(OutboxMessage message) {
        if (message == null || message.getEventId() == null || message.getPayloadHash() == null) {
            throw new IllegalArgumentException("durable Outbox message identity and payload hash are required");
        }
    }
}
