package cn.iocoder.yudao.module.cloudmold.datacontract.service.outbox;

/**
 * Replaceable transport adapter. The database Outbox remains the event fact authority.
 */
public interface EventTransportPublisher {

    boolean supports(String destination);

    void publish(OutboxMessage message) throws Exception;

}
