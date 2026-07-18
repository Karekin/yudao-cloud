-- Root release migration sequence: V20260718_59.
ALTER TABLE `cloudmold_order_item`
    ADD COLUMN `merchandise_cost_minor` BIGINT NULL
        COMMENT 'Exact merchandise cost snapshot captured at order placement'
        AFTER `net_amount_minor`,
    ADD CONSTRAINT `ck_cloudmold_order_item_merchandise_cost`
        CHECK (`merchandise_cost_minor` IS NULL OR `merchandise_cost_minor` >= 0);

ALTER TABLE `cloudmold_fulfillment_item`
    ADD COLUMN `variable_fulfillment_cost_minor` BIGINT NULL
        COMMENT 'Exact variable fulfillment cost snapshot captured at fulfillment creation'
        AFTER `reservation_id`,
    ADD CONSTRAINT `ck_cloudmold_fulfillment_item_variable_cost`
        CHECK (`variable_fulfillment_cost_minor` IS NULL OR `variable_fulfillment_cost_minor` >= 0);
