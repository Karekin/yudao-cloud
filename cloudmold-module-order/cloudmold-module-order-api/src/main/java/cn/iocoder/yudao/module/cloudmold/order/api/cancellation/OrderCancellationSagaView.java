package cn.iocoder.yudao.module.cloudmold.order.api.cancellation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancellationSagaView {
    private String sagaId;
    private String cancellationMode;
    private String runId;
    private String orderId;
    private String orderNo;
    private String orderStatusAtRequest;
    private Long orderVersionAtRequest;
    private String status;
    private String activeStep;
    private Integer expectedReservationCount;
    private Integer releasedReservationCount;
    private Integer attemptCount;
    private Integer maxAttempts;
    private Long aggregateVersion;
    private String reason;
    private String lastErrorCode;
    private String lastErrorMessage;
    private String paymentId;
    private Long paymentRefundTransactionId;
    private String paymentStatus;
    private String fulfillmentId;
    private String fulfillmentStatus;
    private Integer expectedFulfillmentCount;
    private Integer cancelledFulfillmentCount;
    private Instant nextRetryAt;
    private Instant completedAt;
    private List<OrderCancellationSagaItemView> items;
    private Boolean duplicate;
}
