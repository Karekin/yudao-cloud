-- This rollback fails closed while recommendation behavior rows still exist.
-- Archive or migrate those rows explicitly before applying it.
ALTER TABLE cloudmold_commerce_behavior_event
    DROP CHECK chk_commerce_behavior_type,
    ADD CONSTRAINT chk_commerce_behavior_type CHECK (
        behavior_type IN (
            'PDP_VIEWED',
            'SEARCH_REQUESTED',
            'SEARCH_RESULT_EXPOSED',
            'SEARCH_RESULT_CLICKED',
            'CART_ADDED',
            'CART_REMOVED',
            'CHECKOUT_STARTED',
            'CHECKOUT_ABANDONED'
        )
    );
