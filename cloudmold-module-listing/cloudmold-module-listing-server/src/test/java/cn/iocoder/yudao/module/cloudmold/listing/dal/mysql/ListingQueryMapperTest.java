package cn.iocoder.yudao.module.cloudmold.listing.dal.mysql;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.cloudmold.listing.service.query.ListingPageItem;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListingQueryMapperTest extends BaseDbUnitTest {

    @Resource
    private ListingQueryMapper mapper;

    @Resource
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS cloudmold_listing_header (
                    listing_id VARCHAR(36) PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    listing_no VARCHAR(32) NOT NULL,
                    merchant_id VARCHAR(128) NOT NULL,
                    channel_code VARCHAR(32) NOT NULL,
                    shop_id VARCHAR(128) NOT NULL,
                    canonical_spu_id VARCHAR(36) NOT NULL,
                    revision INT NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    currency_code CHAR(3) NOT NULL,
                    publish_start_at TIMESTAMP NULL,
                    publish_end_at TIMESTAMP NULL,
                    status VARCHAR(32) NOT NULL,
                    completion_passed BOOLEAN NOT NULL,
                    business_approved BOOLEAN NOT NULL,
                    risk_approved BOOLEAN NOT NULL,
                    version BIGINT NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS cloudmold_listing_offer (
                    listing_offer_id VARCHAR(36) PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    listing_id VARCHAR(36) NOT NULL,
                    revision INT NOT NULL,
                    canonical_sku_id VARCHAR(36) NOT NULL,
                    price_minor BIGINT NOT NULL,
                    currency_code CHAR(3) NOT NULL,
                    enabled BOOLEAN NOT NULL
                )
                """);
    }

    @Test
    void shouldSelectOnlyCurrentTenantCurrentRevisionAndStableUpdatedAtOrdering() {
        insertHeader("listing-a", 1L, "CML-A", "merchant-1", "YSHOPPING", "shop-1", "spu-1", 2, "Alpha Dress",
                "PUBLISHED", true, true, true, 6L, LocalDateTime.of(2026, 7, 19, 10, 0));
        insertHeader("listing-b", 1L, "CML-B", "merchant-1", "YSHOPPING", "shop-1", "spu-1", 1, "Beta Dress",
                "PUBLISHED", true, true, true, 3L, LocalDateTime.of(2026, 7, 19, 10, 0));
        insertHeader("listing-c", 1L, "CML-C", "merchant-2", "YSHOPPING_INTERNAL", "shop-2", "spu-2", 1,
                "Gamma Coat", "DRAFT", false, false, false, 1L, LocalDateTime.of(2026, 7, 18, 10, 0));
        insertHeader("listing-x", 2L, "CML-X", "merchant-9", "YSHOPPING", "shop-9", "spu-9", 1, "Other Tenant",
                "PUBLISHED", true, true, true, 5L, LocalDateTime.of(2026, 7, 19, 12, 0));

        insertOffer("offer-a-old", 1L, "listing-a", 1, "sku-old", 99900L, true);
        insertOffer("offer-a-1", 1L, "listing-a", 2, "sku-a1", 19900L, true);
        insertOffer("offer-a-2", 1L, "listing-a", 2, "sku-a2", 25900L, true);
        insertOffer("offer-b-1", 1L, "listing-b", 1, "sku-b1", 12900L, true);
        insertOffer("offer-b-2", 1L, "listing-b", 1, "sku-b2", 18900L, false);
        insertOffer("offer-x-1", 2L, "listing-x", 1, "sku-x1", 9999L, true);

        long total = mapper.countListingPage(1L, null, null, null, null, null, null, null, null);
        List<ListingPageItem> page = mapper.selectListingPage(1L, null, null, null, null, null, null, null, null,
                0L, 10);

        assertThat(total).isEqualTo(3L);
        assertThat(page).extracting(ListingPageItem::getListingId)
                .containsExactly("listing-b", "listing-a", "listing-c");
        assertThat(page.get(0).getOfferCount()).isEqualTo(2L);
        assertThat(page.get(0).getEnabledOfferCount()).isEqualTo(1L);
        assertThat(page.get(0).getMinPriceMinor()).isEqualTo(12900L);
        assertThat(page.get(0).getMaxPriceMinor()).isEqualTo(12900L);
        assertThat(page.get(1).getOfferCount()).isEqualTo(2L);
        assertThat(page.get(1).getEnabledOfferCount()).isEqualTo(2L);
        assertThat(page.get(1).getMinPriceMinor()).isEqualTo(19900L);
        assertThat(page.get(1).getMaxPriceMinor()).isEqualTo(25900L);
        assertThat(page.get(2).getOfferCount()).isEqualTo(0L);
    }

    @Test
    void shouldApplyHeaderFiltersToCountAndPageSelection() {
        insertHeader("listing-1", 1L, "CML-RED-1", "merchant-1", "YSHOPPING", "shop-1", "spu-1", 1,
                "Red Dress", "PUBLISHED", true, true, true, 2L, LocalDateTime.of(2026, 7, 19, 9, 0));
        insertHeader("listing-2", 1L, "CML-BLUE-1", "merchant-1", "YSHOPPING", "shop-1", "spu-2", 1,
                "Blue Dress", "PUBLISHED", true, true, true, 2L, LocalDateTime.of(2026, 7, 19, 8, 0));
        insertHeader("listing-3", 1L, "CML-RED-2", "merchant-2", "YSHOPPING_INTERNAL", "shop-2", "spu-1", 1,
                "Red Coat", "DRAFT", false, false, false, 1L, LocalDateTime.of(2026, 7, 19, 7, 0));
        insertOffer("offer-1", 1L, "listing-1", 1, "sku-1", 10000L, true);
        insertOffer("offer-2", 1L, "listing-2", 1, "sku-2", 12000L, true);
        insertOffer("offer-3", 1L, "listing-3", 1, "sku-3", 14000L, true);

        long total = mapper.countListingPage(1L, null, "RED", "Dress", "merchant-1", "shop-1",
                "YSHOPPING", "spu-1", "PUBLISHED");
        List<ListingPageItem> page = mapper.selectListingPage(1L, null, "RED", "Dress", "merchant-1", "shop-1",
                "YSHOPPING", "spu-1", "PUBLISHED", 0L, 10);

        assertThat(total).isOne();
        assertThat(page).singleElement().extracting(ListingPageItem::getListingId).isEqualTo("listing-1");
    }

    private void insertHeader(String listingId, Long tenantId, String listingNo, String merchantId, String channelCode,
                              String shopId, String canonicalSpuId, int revision, String title, String status,
                              boolean completionPassed, boolean businessApproved, boolean riskApproved, long version,
                              LocalDateTime updatedAt) {
        jdbcTemplate.update("""
                        INSERT INTO cloudmold_listing_header (
                            listing_id, tenant_id, listing_no, merchant_id, channel_code, shop_id,
                            canonical_spu_id, revision, title, currency_code, publish_start_at, publish_end_at,
                            status, completion_passed, business_approved, risk_approved, version, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'CNY', ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                listingId, tenantId, listingNo, merchantId, channelCode, shopId, canonicalSpuId, revision, title,
                Timestamp.valueOf(updatedAt.minusHours(1)), null, status, completionPassed, businessApproved,
                riskApproved, version, Timestamp.valueOf(updatedAt));
    }

    private void insertOffer(String listingOfferId, Long tenantId, String listingId, int revision, String canonicalSkuId,
                             long priceMinor, boolean enabled) {
        jdbcTemplate.update("""
                        INSERT INTO cloudmold_listing_offer (
                            listing_offer_id, tenant_id, listing_id, revision, canonical_sku_id,
                            price_minor, currency_code, enabled
                        ) VALUES (?, ?, ?, ?, ?, ?, 'CNY', ?)
                        """,
                listingOfferId, tenantId, listingId, revision, canonicalSkuId, priceMinor, enabled);
    }
}
