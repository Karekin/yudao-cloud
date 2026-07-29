package cn.iocoder.yudao.module.cloudmold.listing.service.workflow;

import cn.iocoder.yudao.module.cloudmold.listing.api.ListingQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.ListingTerminalReadbackCommand;
import cn.iocoder.yudao.module.cloudmold.listing.api.ListingTerminalReadbackView;
import cn.iocoder.yudao.module.cloudmold.listing.api.unpublish.ListingUnpublishSagaQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.unpublish.ListingUnpublishSagaView;
import cn.iocoder.yudao.module.cloudmold.listing.api.workflow.ListingLifecycleWorkflowQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.workflow.ListingLifecycleWorkflowResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ListingLifecycleWorkflowQueryService implements ListingLifecycleWorkflowQueryApi {

    static final String LISTING = "LISTING";
    static final String ELIGIBILITY_ENFORCEMENT = "ELIGIBILITY_ENFORCEMENT";

    private static final Set<String> LISTING_SUCCESS = Set.of(
            "CONFIRMED_PUBLISHED", "LISTING_NOT_PUBLISHED");
    private static final Set<String> LISTING_FAILURE = Set.of(
            "CHANNEL_PUBLISH_FAILED");

    private final ListingQueryApi listingQueryApi;
    private final ListingUnpublishSagaQueryApi unpublishSagaQueryApi;

    @Override
    public ListingLifecycleWorkflowResult inspect(String entityType, String businessId) {
        String normalizedType = requireText(entityType, "entityType", 64)
                .toUpperCase(Locale.ROOT);
        String normalizedId = requireText(businessId, "businessId", 64);
        return switch (normalizedType) {
            case LISTING -> inspectListing(normalizedId);
            case ELIGIBILITY_ENFORCEMENT -> inspectEligibilityEnforcement(normalizedId);
            default -> throw new IllegalArgumentException(
                    "entityType must be LISTING or ELIGIBILITY_ENFORCEMENT");
        };
    }

    private ListingLifecycleWorkflowResult inspectListing(String listingId) {
        ListingTerminalReadbackView view = listingQueryApi.getListingTerminalReadback(
                ListingTerminalReadbackCommand.builder().listingId(listingId).build());
        require(view != null && listingId.equals(view.getListingId()),
                "listing lifecycle query returned a different business object");
        String resultCode = requireText(
                view.getOverallResultCode(), "overallResultCode", 64);
        String status = LISTING_SUCCESS.contains(resultCode)
                ? "SUCCEEDED" : LISTING_FAILURE.contains(resultCode)
                ? "FAILED" : "WAITING";
        return ListingLifecycleWorkflowResult.builder()
                .entityType(LISTING)
                .businessId(listingId)
                .status(status)
                .lifecycleState(view.getCurrentStatus())
                .terminal(!"WAITING".equals(status))
                .evidenceSource(view.getEvidenceSource())
                .summary(view.getSummary())
                .build();
    }

    private ListingLifecycleWorkflowResult inspectEligibilityEnforcement(
            String sagaId) {
        ListingUnpublishSagaView view = unpublishSagaQueryApi.get(sagaId);
        require(view != null && sagaId.equals(view.getSagaId()),
                "Listing eligibility enforcement query returned a different Saga");
        String lifecycleState = requireText(view.getStatus(), "status", 64);
        String status = "COMPLETED".equals(lifecycleState)
                ? "SUCCEEDED" : "MANUAL_REVIEW".equals(lifecycleState)
                ? "FAILED" : "WAITING";
        String summary = "SUCCEEDED".equals(status)
                ? "商家销售资格失效后的商品下架已完成"
                : "FAILED".equals(status)
                ? "商品下架需要人工复核"
                : "商品下架仍在执行或等待重试";
        return ListingLifecycleWorkflowResult.builder()
                .entityType(ELIGIBILITY_ENFORCEMENT)
                .businessId(sagaId)
                .status(status)
                .lifecycleState(lifecycleState)
                .terminal(!"WAITING".equals(status))
                .evidenceSource("LISTING_ELIGIBILITY_ENFORCEMENT_SOR")
                .summary(summary)
                .build();
    }

    private static String requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength,
                field + " is required");
        return value.trim();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
