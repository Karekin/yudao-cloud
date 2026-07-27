package cn.iocoder.yudao.module.cloudmold.listing.api;

public interface ListingCommandApi {
    ListingCommandResult execute(ListingCommand command);

    ListingChannelPublishReceiptResult recordChannelPublishReceipt(ListingChannelPublishReceiptCommand command);
}
