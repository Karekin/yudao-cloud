package cn.iocoder.yudao.module.cloudmold.promotion.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject.CouponEntitlementLedgerDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CouponEntitlementLedgerMapper {
    @Insert("""
        INSERT INTO cloudmold_promotion_coupon_entitlement_ledger
          (ledger_entry_id,tenant_id,entitlement_id,entitlement_version,operation_id,operation_type,
           previous_status,current_status,order_ref,reason,face_amount_minor,threshold_minor,currency_code,
           occurred_at,created_at)
        VALUES
          (#{ledgerEntryId},#{tenantId},#{entitlementId},#{entitlementVersion},#{operationId},#{operationType},
           #{previousStatus},#{currentStatus},#{orderRef},#{reason},#{faceAmountMinor},#{thresholdMinor},#{currencyCode},
           #{occurredAt},#{createdAt})
        """)
    int insert(CouponEntitlementLedgerDO row);
}
