SELECT CASE
         WHEN COUNT(*) = 1 THEN 'PASS'
         ELSE CONCAT('FAIL: hidden Agent approval form route count=', COUNT(*))
       END AS agent_approval_form_route_check
FROM system_menu
WHERE id = 9100000000074
  AND parent_id = 9100000000000
  AND path = 'agent-control/approval-form'
  AND component = 'cloudmold/agent-control/approval-form'
  AND permission = 'cloudmold:agent-control:query'
  AND status = 0
  AND visible = b'0'
  AND deleted = b'0';

SELECT CASE
         WHEN COUNT(*) = 0 THEN 'PASS'
         ELSE CONCAT('FAIL: roles missing approval form route=', COUNT(*))
       END AS agent_approval_form_role_check
FROM (
    SELECT DISTINCT query_grant.role_id
    FROM system_role_menu query_grant
    LEFT JOIN system_role_menu form_grant
      ON form_grant.role_id = query_grant.role_id
     AND form_grant.menu_id = 9100000000074
    WHERE query_grant.menu_id = 9100000000071
      AND form_grant.role_id IS NULL
) missing;
