package cn.iocoder.yudao.module.cloudmold.aftersale.service;

public record AfterSaleResolutionRunResult(int candidates, int claimed, int completed,
                                           int retryScheduled, int manualReview) {}
