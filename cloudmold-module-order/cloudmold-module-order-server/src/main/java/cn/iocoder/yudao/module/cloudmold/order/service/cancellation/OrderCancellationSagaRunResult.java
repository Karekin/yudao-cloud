package cn.iocoder.yudao.module.cloudmold.order.service.cancellation;

public record OrderCancellationSagaRunResult(int candidates, int claimed, int completed,
                                             int retryScheduled, int manualReview) {
}
