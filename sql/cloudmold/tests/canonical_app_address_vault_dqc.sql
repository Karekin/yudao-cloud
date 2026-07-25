-- All statements must return zero rows.

-- Cryptographic envelope and governance evidence are complete.
SELECT address_ref
FROM cloudmold_app_address_snapshot
WHERE status NOT IN ('ACTIVE','REVOKED')
   OR snapshot_version <= 0
   OR request_hash NOT REGEXP '^[0-9a-f]{64}$'
   OR source_fingerprint_sha256 NOT REGEXP '^[0-9a-f]{64}$'
   OR OCTET_LENGTH(initialization_vector) <> 12
   OR OCTET_LENGTH(ciphertext) <= 16
   OR destination_region_code = '';

-- A checkout cannot carry a partial address token.
SELECT checkout_token
FROM cloudmold_app_checkout
WHERE (address_ref IS NULL) <> (address_snapshot_version IS NULL)
   OR (address_ref IS NULL) <> (destination_region_code IS NULL);

-- A tokenized checkout must reference the same tenant and active snapshot version.
SELECT c.checkout_token
FROM cloudmold_app_checkout c
LEFT JOIN cloudmold_app_address_snapshot a
  ON a.tenant_id = c.tenant_id AND BINARY a.address_ref = BINARY c.address_ref
WHERE c.address_ref IS NOT NULL
  AND (a.address_ref IS NULL
       OR a.status <> 'ACTIVE'
       OR a.snapshot_version <> c.address_snapshot_version
       OR BINARY a.destination_region_code <> BINARY c.destination_region_code
       OR BINARY a.owner_principal_id <> BINARY c.buyer_principal_id);

-- Canonical orders created from tokenized App checkouts must preserve only the token.
SELECT o.order_id
FROM cloudmold_app_checkout c
JOIN cloudmold_order_header o
  ON o.tenant_id = c.tenant_id AND BINARY o.order_id = BINARY c.order_id
WHERE c.status = 'ORDERED'
  AND c.address_ref IS NOT NULL
  AND (BINARY o.address_ref <> BINARY c.address_ref
       OR o.address_snapshot_version <> c.address_snapshot_version
       OR BINARY o.destination_region_code <> BINARY c.destination_region_code);
