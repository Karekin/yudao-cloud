SELECT
    'recommendation_behavior_constraint_missing' AS check_name,
    CASE
        WHEN COUNT(*) = 1
         AND MAX(CHECK_CLAUSE) LIKE '%RECOMMENDATION_EXPOSED%'
         AND MAX(CHECK_CLAUSE) LIKE '%RECOMMENDATION_CLICKED%'
        THEN 0
        ELSE 1
    END AS violation_count
FROM information_schema.CHECK_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'chk_commerce_behavior_type';

SELECT
    'recommendation_behavior_type_invalid' AS check_name,
    COUNT(*) AS violation_count
FROM cloudmold_commerce_behavior_event
WHERE behavior_type NOT IN (
    'PDP_VIEWED',
    'SEARCH_REQUESTED',
    'SEARCH_RESULT_EXPOSED',
    'SEARCH_RESULT_CLICKED',
    'CART_ADDED',
    'CART_REMOVED',
    'CHECKOUT_STARTED',
    'CHECKOUT_ABANDONED',
    'RECOMMENDATION_EXPOSED',
    'RECOMMENDATION_CLICKED'
);
