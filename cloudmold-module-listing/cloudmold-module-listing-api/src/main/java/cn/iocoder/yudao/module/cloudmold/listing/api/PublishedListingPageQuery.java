package cn.iocoder.yudao.module.cloudmold.listing.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishedListingPageQuery {
    private String keyword;
    private String channelCode;
    private Integer pageNo;
    private Integer pageSize;
}
