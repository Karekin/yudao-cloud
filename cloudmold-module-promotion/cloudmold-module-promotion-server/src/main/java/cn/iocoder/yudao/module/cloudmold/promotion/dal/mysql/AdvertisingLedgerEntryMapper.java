package cn.iocoder.yudao.module.cloudmold.promotion.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject.AdvertisingLedgerEntryDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdvertisingLedgerEntryMapper {
    @Insert("""
        INSERT INTO cloudmold_promotion_advertising_ledger
          (ledger_entry_id,tenant_id,ledger_entry_code,campaign_id,placement_id,merchant_id,entry_type,
           charge_model,revenue_type,source_interaction_id,order_ref,amount_minor,currency_code,occurred_at,created_at)
        VALUES
          (#{ledgerEntryId},#{tenantId},#{ledgerEntryCode},#{campaignId},#{placementId},#{merchantId},
           #{entryType},#{chargeModel},#{revenueType},#{sourceInteractionId},#{orderRef},#{amountMinor},#{currencyCode},
           #{occurredAt},#{createdAt})
        """)
    int insert(AdvertisingLedgerEntryDO row);

    @Select("SELECT * FROM cloudmold_promotion_advertising_ledger WHERE tenant_id=#{tenantId} AND ledger_entry_id=#{id}")
    AdvertisingLedgerEntryDO selectById(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_promotion_advertising_ledger WHERE tenant_id=#{tenantId} AND ledger_entry_code=#{code}")
    AdvertisingLedgerEntryDO selectByCode(@Param("tenantId") Long tenantId, @Param("code") String code);
}
