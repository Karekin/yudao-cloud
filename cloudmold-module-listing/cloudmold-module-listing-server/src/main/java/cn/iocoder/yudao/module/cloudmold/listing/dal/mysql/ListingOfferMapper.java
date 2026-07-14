package cn.iocoder.yudao.module.cloudmold.listing.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.ListingOfferDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ListingOfferMapper extends BaseMapperX<ListingOfferDO> {
    @Select("""
            SELECT listing_offer_id,tenant_id,listing_id,revision,canonical_sku_id,price_minor,currency_code,
                   enabled,external_offer_id,created_at,updated_at
            FROM cloudmold_listing_offer
            WHERE tenant_id=#{tenantId} AND listing_id=#{listingId} AND revision=#{revision}
            ORDER BY canonical_sku_id
            """)
    List<ListingOfferDO> selectByListingRevision(@Param("tenantId") Long tenantId,
                                                 @Param("listingId") String listingId,
                                                 @Param("revision") Integer revision);
}
