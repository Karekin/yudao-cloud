package cn.iocoder.yudao.module.cloudmold.quality.api;

public interface QualityCommandApi {
    default QualityResult execute(QualityCommand command) {
        throw new IllegalStateException("attested actor Principal is required");
    }

    QualityResult execute(QualityCommand command, String actorPrincipalId);
}
