package cn.iocoder.yudao.module.cloudmold.dreamplant.service;

import cn.iocoder.yudao.module.cloudmold.dreamplant.api.*;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DreamPlantRpcSerializationContractTest {

    @Test
    void allDubboDataTransferObjectsMustBeSerializable() {
        List<Class<?>> dataTransferObjects = List.of(
                DreamPlantAssetCommand.class, DreamPlantAssetView.class,
                DreamPlantCommand.class, DreamPlantCommandResult.class,
                DreamPlantDriftCommand.class, DreamPlantDriftView.class,
                DreamPlantEvidenceCommand.class, DreamPlantEvidenceView.class,
                DreamPlantExplorationView.class,
                DreamPlantMetricCommand.class, DreamPlantMetricView.class,
                DreamPlantRelationCommand.class, DreamPlantRelationView.class,
                DreamPlantSyncCommand.class, DreamPlantSyncView.class,
                DreamPlantWorldMapSnapshot.class);

        assertThat(dataTransferObjects)
                .allSatisfy(type -> assertThat(Serializable.class.isAssignableFrom(type))
                        .as(type.getName()).isTrue());
    }
}
