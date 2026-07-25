package cn.iocoder.yudao.module.cloudmold.order.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.cloudmold.order.controller.admin.vo.OrderPageReqVO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Import(OrderQueryService.class)
@TestPropertySource(properties = "yudao.info.base-package=cn.iocoder.yudao")
class OrderQueryServiceIntegrationTest extends BaseDbUnitTest {

    @Resource
    private OrderQueryService orderQueryService;
    @Resource
    private DataSource dataSource;

    private JdbcOperations jdbcTemplate;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        jdbcTemplate.update("DELETE FROM cloudmold_order_item");
        jdbcTemplate.update("DELETE FROM cloudmold_payment");
        jdbcTemplate.update("DELETE FROM cloudmold_fulfillment_order");
        jdbcTemplate.update("DELETE FROM cloudmold_order_header");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldReturnStableOrderedPageWithAggregatedItemCountsAndRefs() {
        insertOrder("order-a", 1L, "CMO-1001", "buyer-1", "PAYMENT_CONFIRMED", new BigDecimal("2.000000"),
                1000L, 200L, 50L, 1150L, "pay-a", "ful-a", "ship-a", null, null,
                2L, ts("2026-07-19T08:00:00"), ts("2026-07-19T10:00:00"));
        insertOrder("order-z", 1L, "CMO-1002", "buyer-2", "SHIPPED", new BigDecimal("1.000000"),
                2000L, 0L, 0L, 2000L, "pay-z", "ful-z", "ship-z", "refund-z", "cancel-z",
                3L, ts("2026-07-19T09:00:00"), ts("2026-07-19T10:00:00"));
        insertOrder("order-new", 1L, "CMO-1003", "buyer-3", "INVENTORY_RESERVED", new BigDecimal("3.000000"),
                3000L, 100L, 150L, 2950L, null, null, null, null, null,
                1L, ts("2026-07-19T09:30:00"), ts("2026-07-19T11:00:00"));

        insertOrder("order-other-tenant", 2L, "CMO-1004", "buyer-1", "PAYMENT_CONFIRMED", new BigDecimal("9.000000"),
                9999L, 0L, 0L, 9999L, "pay-foreign", "ful-foreign", "ship-foreign", null, null,
                5L, ts("2026-07-19T07:00:00"), ts("2026-07-19T12:00:00"));

        insertOrderItem("item-a1", 1L, "order-a");
        insertOrderItem("item-a2", 1L, "order-a");
        insertOrderItem("item-z1", 1L, "order-z");
        insertOrderItem("item-new1", 1L, "order-new");
        insertOrderItem("item-new2", 1L, "order-new");
        insertOrderItem("item-new3", 1L, "order-new");
        insertOrderItem("item-foreign", 2L, "order-other-tenant");

        insertPayment("pay-a", 1L, "CAPTURED");
        insertPayment("pay-z", 1L, "REFUNDED");
        insertPayment("pay-foreign", 2L, "CAPTURED");
        insertFulfillment("ful-a", 1L, "CREATED");
        insertFulfillment("ful-z", 1L, "DELIVERED");
        insertFulfillment("ful-foreign", 2L, "SHIPPED");

        OrderPageReqVO request = new OrderPageReqVO();
        request.setPageNo(1);
        request.setPageSize(10);

        PageResult<OrderPageItem> result = orderQueryService.getOrderPage(request);

        assertThat(result.getTotal()).isEqualTo(3L);
        assertThat(result.getList()).extracting(OrderPageItem::getOrderId)
                .containsExactly("order-new", "order-z", "order-a");
        assertThat(result.getList()).extracting(OrderPageItem::getItemCount)
                .containsExactly(3L, 1L, 2L);
        assertThat(result.getList().get(1).getPaymentStatus()).isEqualTo("REFUNDED");
        assertThat(result.getList().get(1).getFulfillmentStatus()).isEqualTo("DELIVERED");
        assertThat(result.getList().get(1).getRefundId()).isEqualTo("refund-z");
        assertThat(result.getList().get(1).getCancellationSagaId()).isEqualTo("cancel-z");
        assertThat(result.getList().get(0).getPaymentStatus()).isNull();
        assertThat(result.getList().get(0).getFulfillmentStatus()).isNull();
    }

    @Test
    void shouldApplyFiltersAndRestrictToCurrentTenant() {
        insertOrder("order-match", 1L, "CMO-2001", "buyer-1", "PAYMENT_CONFIRMED", new BigDecimal("1.000000"),
                1000L, 0L, 0L, 1000L, "pay-match", "ful-match", "ship-match", null, null,
                4L, ts("2026-07-19T08:00:00"), ts("2026-07-19T09:00:00"));
        insertOrder("order-no-match", 1L, "CMO-2002", "buyer-2", "PAYMENT_CONFIRMED", new BigDecimal("1.000000"),
                1000L, 0L, 0L, 1000L, null, null, null, null, null,
                1L, ts("2026-07-19T08:00:00"), ts("2026-07-19T09:00:00"));
        insertOrder("order-foreign", 2L, "CMO-2001", "buyer-1", "PAYMENT_CONFIRMED", new BigDecimal("1.000000"),
                1000L, 0L, 0L, 1000L, null, null, null, null, null,
                1L, ts("2026-07-19T08:00:00"), ts("2026-07-19T09:00:00"));
        insertOrderItem("item-match", 1L, "order-match");

        OrderPageReqVO request = new OrderPageReqVO();
        request.setOrderNo("  2001 ");
        request.setBuyerId(" buyer-1 ");
        request.setStatus(" PAYMENT_CONFIRMED ");

        PageResult<OrderPageItem> result = orderQueryService.getOrderPage(request);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getList()).singleElement().satisfies(item -> {
            assertThat(item.getOrderId()).isEqualTo("order-match");
            assertThat(item.getBuyerId()).isEqualTo("buyer-1");
            assertThat(item.getItemCount()).isEqualTo(1L);
        });
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS cloudmold_order_header (
                    order_id VARCHAR(36) PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    order_no VARCHAR(32) NOT NULL,
                    buyer_id VARCHAR(128) NOT NULL,
                    address_ref VARCHAR(36),
                    address_snapshot_version BIGINT,
                    destination_region_code VARCHAR(32),
                    status VARCHAR(32) NOT NULL,
                    total_quantity DECIMAL(24, 6) NOT NULL,
                    product_amount_minor BIGINT NOT NULL,
                    shipping_amount_minor BIGINT NOT NULL,
                    discount_amount_minor BIGINT NOT NULL,
                    payable_amount_minor BIGINT NOT NULL,
                    currency_code CHAR(3) NOT NULL,
                    payment_id VARCHAR(36),
                    fulfillment_id VARCHAR(36),
                    shipment_id VARCHAR(36),
                    refund_id VARCHAR(128),
                    cancellation_saga_id VARCHAR(36),
                    version BIGINT NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS cloudmold_order_item (
                    order_item_id VARCHAR(36) PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    order_id VARCHAR(36) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS cloudmold_payment (
                    payment_id VARCHAR(36) PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    status VARCHAR(32) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS cloudmold_fulfillment_order (
                    fulfillment_id VARCHAR(36) PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    status VARCHAR(32) NOT NULL
                )
                """);
    }

    private void insertOrder(String orderId, Long tenantId, String orderNo, String buyerId, String status,
                             BigDecimal totalQuantity, Long productAmountMinor, Long shippingAmountMinor,
                             Long discountAmountMinor, Long payableAmountMinor, String paymentId,
                             String fulfillmentId, String shipmentId, String refundId,
                             String cancellationSagaId, Long version, Timestamp createdAt,
                             Timestamp updatedAt) {
        jdbcTemplate.update("""
                        INSERT INTO cloudmold_order_header
                        (order_id, tenant_id, order_no, buyer_id, status, total_quantity,
                         product_amount_minor, shipping_amount_minor, discount_amount_minor,
                         payable_amount_minor, currency_code, payment_id, fulfillment_id, shipment_id,
                         refund_id, cancellation_saga_id, version, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                orderId, tenantId, orderNo, buyerId, status, totalQuantity,
                productAmountMinor, shippingAmountMinor, discountAmountMinor,
                payableAmountMinor, "CNY", paymentId, fulfillmentId, shipmentId,
                refundId, cancellationSagaId, version, createdAt, updatedAt);
    }

    private void insertOrderItem(String orderItemId, Long tenantId, String orderId) {
        jdbcTemplate.update("""
                INSERT INTO cloudmold_order_item (order_item_id, tenant_id, order_id)
                VALUES (?, ?, ?)
                """, orderItemId, tenantId, orderId);
    }

    private void insertPayment(String paymentId, Long tenantId, String status) {
        jdbcTemplate.update("""
                INSERT INTO cloudmold_payment (payment_id, tenant_id, status)
                VALUES (?, ?, ?)
                """, paymentId, tenantId, status);
    }

    private void insertFulfillment(String fulfillmentId, Long tenantId, String status) {
        jdbcTemplate.update("""
                INSERT INTO cloudmold_fulfillment_order (fulfillment_id, tenant_id, status)
                VALUES (?, ?, ?)
                """, fulfillmentId, tenantId, status);
    }

    private static Timestamp ts(String value) {
        return Timestamp.valueOf(LocalDateTime.parse(value));
    }
}
