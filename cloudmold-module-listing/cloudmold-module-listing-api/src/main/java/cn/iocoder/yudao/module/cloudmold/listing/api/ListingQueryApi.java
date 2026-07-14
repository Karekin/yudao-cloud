package cn.iocoder.yudao.module.cloudmold.listing.api;

public interface ListingQueryApi {
    PublishedListingOfferView requirePublishedOffer(PublishedOfferValidationCommand command);
}
