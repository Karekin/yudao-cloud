package cn.iocoder.yudao.module.cloudmold.listing.service.workflow;

import cn.iocoder.yudao.module.cloudmold.listing.api.ListingQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.ListingTerminalReadbackCommand;
import cn.iocoder.yudao.module.cloudmold.listing.api.ListingTerminalReadbackView;
import cn.iocoder.yudao.module.cloudmold.listing.api.unpublish.ListingUnpublishSagaQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.unpublish.ListingUnpublishSagaView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListingLifecycleWorkflowQueryServiceTest {

    private final ListingQueryApi listingQueryApi = mock(ListingQueryApi.class);
    private final ListingUnpublishSagaQueryApi unpublishSagaQueryApi =
            mock(ListingUnpublishSagaQueryApi.class);
    private final ListingLifecycleWorkflowQueryService service =
            new ListingLifecycleWorkflowQueryService(
                    listingQueryApi, unpublishSagaQueryApi);

    @Test
    void reportsConfirmedListingAsSucceeded() {
        when(listingQueryApi.getListingTerminalReadback(argThat(
                command -> "listing-1".equals(command.getListingId()))))
                .thenReturn(ListingTerminalReadbackView.builder()
                        .listingId("listing-1")
                        .currentStatus("PUBLISHED")
                        .overallResultCode("CONFIRMED_PUBLISHED")
                        .evidenceSource("REAL_CHANNEL_RECEIPT")
                        .summary("已收到渠道确认")
                        .build());

        assertThat(service.inspect("listing", "listing-1"))
                .satisfies(result -> {
                    assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
                    assertThat(result.getLifecycleState()).isEqualTo("PUBLISHED");
                    assertThat(result.getTerminal()).isTrue();
                    assertThat(result.getEvidenceSource())
                            .isEqualTo("REAL_CHANNEL_RECEIPT");
                });
    }

    @Test
    void keepsPublishedListingWaitingUntilChannelReceipt() {
        when(listingQueryApi.getListingTerminalReadback(
                new ListingTerminalReadbackCommand("listing-2")))
                .thenReturn(ListingTerminalReadbackView.builder()
                        .listingId("listing-2")
                        .currentStatus("PUBLISHED")
                        .overallResultCode("PENDING_CONFIRMATION")
                        .evidenceSource("CANONICAL_LISTING_ONLY")
                        .summary("待渠道确认")
                        .build());

        assertThat(service.inspect("LISTING", "listing-2"))
                .satisfies(result -> {
                    assertThat(result.getStatus()).isEqualTo("WAITING");
                    assertThat(result.getTerminal()).isFalse();
                });
    }

    @Test
    void reportsEligibilityEnforcementTerminalStatesHonestly() {
        when(unpublishSagaQueryApi.get("saga-complete"))
                .thenReturn(ListingUnpublishSagaView.builder()
                        .sagaId("saga-complete")
                        .status("COMPLETED")
                        .build());
        when(unpublishSagaQueryApi.get("saga-review"))
                .thenReturn(ListingUnpublishSagaView.builder()
                        .sagaId("saga-review")
                        .status("MANUAL_REVIEW")
                        .build());

        assertThat(service.inspect(
                "ELIGIBILITY_ENFORCEMENT", "saga-complete").getStatus())
                .isEqualTo("SUCCEEDED");
        assertThat(service.inspect(
                "ELIGIBILITY_ENFORCEMENT", "saga-review").getStatus())
                .isEqualTo("FAILED");
    }
}
