package cn.iocoder.yudao.module.cloudmold.integration.yudao.dal;

import org.apache.ibatis.annotations.*;

@Mapper
public interface YudaoPurchasePromiseMapper {

    @Select("""
            SELECT promise_id, promise_key, tenant_id, purchase_order_id, purchase_order_no,
                   purchase_order_line_id, supplier_id, product_id, product_unit_id,
                   ordered_quantity, promised_receipt_at, promise_timezone, grace_minutes,
                   pause_minutes, promise_frozen_at, status, version, run_id, reason,
                   created_at, updated_at
              FROM cloudmold_yudao_purchase_promise
             WHERE tenant_id = #{tenantId} AND purchase_order_line_id = #{purchaseOrderLineId}
            """)
    YudaoPurchasePromiseRow selectByLineId(@Param("tenantId") Long tenantId,
                                           @Param("purchaseOrderLineId") Long purchaseOrderLineId);

    @Select("""
            SELECT promise_id, promise_key, tenant_id, purchase_order_id, purchase_order_no,
                   purchase_order_line_id, supplier_id, product_id, product_unit_id,
                   ordered_quantity, promised_receipt_at, promise_timezone, grace_minutes,
                   pause_minutes, promise_frozen_at, status, version, run_id, reason,
                   created_at, updated_at
              FROM cloudmold_yudao_purchase_promise
             WHERE tenant_id = #{tenantId} AND purchase_order_line_id = #{purchaseOrderLineId}
             FOR UPDATE
            """)
    YudaoPurchasePromiseRow selectForUpdateByLineId(@Param("tenantId") Long tenantId,
                                                    @Param("purchaseOrderLineId") Long purchaseOrderLineId);

    @Insert("""
            INSERT INTO cloudmold_yudao_purchase_promise
              (promise_id, promise_key, tenant_id, purchase_order_id, purchase_order_no,
               purchase_order_line_id, supplier_id, product_id, product_unit_id, ordered_quantity,
               promised_receipt_at, promise_timezone, grace_minutes, pause_minutes, promise_frozen_at,
               status, version, run_id, reason, created_at, updated_at)
            VALUES
              (#{promiseId}, #{promiseKey}, #{tenantId}, #{purchaseOrderId}, #{purchaseOrderNo},
               #{purchaseOrderLineId}, #{supplierId}, #{productId}, #{productUnitId}, #{orderedQuantity},
               #{promisedReceiptAt}, #{promiseTimezone}, #{graceMinutes}, #{pauseMinutes}, #{promiseFrozenAt},
               #{status}, #{version}, #{runId}, #{reason}, #{createdAt}, #{updatedAt})
            """)
    int insert(YudaoPurchasePromiseRow row);

    @Update("""
            UPDATE cloudmold_yudao_purchase_promise
               SET purchase_order_id = #{row.purchaseOrderId},
                   purchase_order_no = #{row.purchaseOrderNo},
                   supplier_id = #{row.supplierId},
                   product_id = #{row.productId},
                   product_unit_id = #{row.productUnitId},
                   ordered_quantity = #{row.orderedQuantity},
                   promised_receipt_at = #{row.promisedReceiptAt},
                   promise_timezone = #{row.promiseTimezone},
                   grace_minutes = #{row.graceMinutes},
                   pause_minutes = #{row.pauseMinutes},
                   promise_frozen_at = #{row.promiseFrozenAt},
                   status = #{row.status},
                   version = #{row.version},
                   run_id = #{row.runId},
                   reason = #{row.reason},
                   updated_at = #{row.updatedAt}
             WHERE tenant_id = #{row.tenantId}
               AND promise_id = #{row.promiseId}
               AND version = #{expectedVersion}
            """)
    int update(@Param("row") YudaoPurchasePromiseRow row, @Param("expectedVersion") Long expectedVersion);
}
