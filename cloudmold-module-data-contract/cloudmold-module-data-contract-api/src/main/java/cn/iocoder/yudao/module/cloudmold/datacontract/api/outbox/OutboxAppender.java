package cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox;

/**
 * Transactional port for persisting a domain event.
 */
public interface OutboxAppender {

    AppendDomainEventResult append(AppendDomainEventCommand command);

}
