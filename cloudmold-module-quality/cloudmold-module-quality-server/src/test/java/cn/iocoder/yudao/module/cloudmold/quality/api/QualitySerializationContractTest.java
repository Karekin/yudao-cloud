package cn.iocoder.yudao.module.cloudmold.quality.api;

import cn.iocoder.yudao.module.cloudmold.quality.api.workflow.QualityRecallWorkflowResult;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class QualitySerializationContractTest {

    @Test
    void everyPublicDtoIncludingNestedDefinitionsIsSerializable() {
        Stream.concat(Stream.of(QualityCommand.class, QualityResult.class,
                                QualityConsumerEvidenceView.class,
                                ProcurementReceiptInspectionCommand.class,
                                ProcurementReceiptInspectionResult.class,
                                ProcurementReceiptInspectionView.class,
                                QualityRecallWorkflowResult.class),
                        Stream.concat(Stream.concat(
                                                Arrays.stream(QualityCommand.class.getDeclaredClasses()),
                                                Stream.concat(
                                                        Arrays.stream(ProcurementReceiptInspectionCommand.class
                                                                .getDeclaredClasses()),
                                                        Arrays.stream(ProcurementReceiptInspectionView.class
                                                                .getDeclaredClasses()))),
                                        Arrays.stream(QualityRecallWorkflowResult.class
                                                .getDeclaredClasses()))
                                .filter(type -> !type.isEnum())
                                .filter(type -> !type.getSimpleName().endsWith("Builder")))
                .forEach(type -> assertThat(Serializable.class.isAssignableFrom(type))
                        .as(type.getName() + " must support RPC serialization")
                        .isTrue());
    }
}
