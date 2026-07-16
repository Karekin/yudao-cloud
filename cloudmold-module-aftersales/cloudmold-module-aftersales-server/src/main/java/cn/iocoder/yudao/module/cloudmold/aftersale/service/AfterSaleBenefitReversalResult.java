package cn.iocoder.yudao.module.cloudmold.aftersale.service;

public record AfterSaleBenefitReversalResult(String batchId, int reversalCount,
                                             int fundingReversalCount, long amountMinor) {}
