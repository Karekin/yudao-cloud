package cn.iocoder.yudao.module.cloudmold.quality.api;

public interface ProcurementReceiptInspectionCommandApi {

    default ProcurementReceiptInspectionResult execute(ProcurementReceiptInspectionCommand command) {
        throw new IllegalStateException("attested actor Principal is required");
    }

    ProcurementReceiptInspectionResult execute(
            ProcurementReceiptInspectionCommand command, String actorPrincipalId);
}
