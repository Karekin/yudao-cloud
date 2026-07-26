package cn.iocoder.yudao.module.cloudmold.listing.controller.admin;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListingQueryControllerSecurityTest {

    @Test
    void skuSellabilityRequiresCatalogAndListingPermissions() throws NoSuchMethodException {
        PreAuthorize authorization = ListingQueryController.class
                .getMethod("getSkuSellability", String.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasPermission('cloudmold:catalog:query') and "
                + "@ss.hasPermission('cloudmold:listing:query')", authorization.value());
    }

}
