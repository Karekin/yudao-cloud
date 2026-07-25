package cn.iocoder.yudao.module.cloudmold.listing.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishedListingPageView {
    private List<PublishedListingView> list;
    private Long total;
    private Integer pageNo;
    private Integer pageSize;
}
