UPDATE `cloudmold_finance_inventory_valuation_layer` layer_state
JOIN (
  SELECT `tenant_id`, `valuation_layer_id`, SUM(`quantity`) AS `returned_quantity`
  FROM `cloudmold_finance_inventory_valuation_effect`
  WHERE `effect_type` = 'SUPPLIER_RETURN'
  GROUP BY `tenant_id`, `valuation_layer_id`
) return_effect
  ON return_effect.tenant_id = layer_state.tenant_id
 AND return_effect.valuation_layer_id = layer_state.valuation_layer_id
SET layer_state.status = CASE
      WHEN layer_state.remaining_quantity = 0
       AND layer_state.remaining_cost_amount_minor = 0
       AND return_effect.returned_quantity = layer_state.quantity THEN 'REVERSED'
      ELSE 'OPEN'
    END,
    layer_state.updated_at = UTC_TIMESTAMP(6);
