package cn.iocoder.yudao.module.cloudmold.supplyplanning.api;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReplenishmentExecutionProposalSerializationTest {

    @Test
    void roundTripsReadyProposalAcrossRpcSerializationBoundary() throws Exception {
        ReplenishmentExecutionProposalView source =
                ReplenishmentExecutionProposalView.builder()
                        .proposalId("proposal-01")
                        .recommendationId("recommendation-01")
                        .expectedRecommendationVersion(2L)
                        .targetType("PURCHASE_REQUEST")
                        .mappingEvidenceSha256("c".repeat(64))
                        .supplierId(11L)
                        .accountId(12L)
                        .erpProductId(13L)
                        .erpProductUnitId(14L)
                        .unitCostMinor(1299L)
                        .taxPercent(new BigDecimal("13.0000"))
                        .targetWarehouseId(31L)
                        .proposedByPrincipalId("principal-planner-01")
                        .policyCode("REPLENISHMENT_EXECUTION_V1")
                        .policySha256("d".repeat(64))
                        .proposedAt(LocalDateTime.of(2026, 7, 29, 9, 30))
                        .build();

        byte[] bytes;
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ObjectOutputStream output = new ObjectOutputStream(buffer)) {
            output.writeObject(source);
            bytes = buffer.toByteArray();
        }
        ReplenishmentExecutionProposalView restored;
        try (ObjectInputStream input =
                     new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            restored = (ReplenishmentExecutionProposalView) input.readObject();
        }

        assertThat(restored).usingRecursiveComparison().isEqualTo(source);
    }
}
