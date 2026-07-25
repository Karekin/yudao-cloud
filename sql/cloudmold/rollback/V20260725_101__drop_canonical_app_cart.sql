-- LOCAL/TEST rollback aid only. Production rollback must preserve the cart
-- event ledger and take an explicit backup before removing server-owned carts.
DROP TABLE IF EXISTS cloudmold_app_cart_item;
DROP TABLE IF EXISTS cloudmold_app_cart;
