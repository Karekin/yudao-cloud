package cn.iocoder.yudao.module.cloudmold.dreamplant.service.worker;

public record DreamPlantExplorationRunResult(
        int scanned,
        int claimed,
        int completed,
        int retried,
        int needsReview
) {
}
