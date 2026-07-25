package cn.iocoder.yudao.module.cloudmold.listing.api;

public interface AppListingQueryApi {

    PublishedListingPageView listPublished(PublishedListingPageQuery query);

    PublishedListingView requirePublished(String listingId);
}
