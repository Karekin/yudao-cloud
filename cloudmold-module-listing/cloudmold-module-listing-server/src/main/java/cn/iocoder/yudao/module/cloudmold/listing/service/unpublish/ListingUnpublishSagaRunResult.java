package cn.iocoder.yudao.module.cloudmold.listing.service.unpublish;

public record ListingUnpublishSagaRunResult(int candidates, int claimed, int completed,
                                             int retryScheduled, int manualReview) {
}
