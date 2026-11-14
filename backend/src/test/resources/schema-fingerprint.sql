-- 输出当前库（mysql -D 选中的库）的结构指纹，每行一项，已排序。
-- CI 分别对“01-04 全新建库”和“旧结构 + M001 + M002 升级库”执行，两份结果必须完全相同。
-- 比较：表的引擎与排序规则；列的类型、可空、默认值、附加属性、排序规则；索引；外键；CHECK 约束。
-- 不比较：注释、列顺序。
-- 排除 M002 说明里故意保留的历史资料：second_hand_task 表、t_task_flea.item_condition、
-- t_task_flea.requester_payment_code、orders.payment_code。
SELECT CONCAT_WS('|', 'TABLE', table_name, engine, table_collation)
  FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' AND table_name <> 'second_hand_task'
UNION ALL
SELECT CONCAT_WS('|', 'COLUMN', table_name, column_name, column_type, is_nullable,
                 IFNULL(column_default, '<null>'), extra, IFNULL(collation_name, '-'))
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name <> 'second_hand_task'
   AND (table_name, column_name) NOT IN (('t_task_flea', 'item_condition'),
                                         ('t_task_flea', 'requester_payment_code'),
                                         ('orders', 'payment_code'))
UNION ALL
SELECT CONCAT_WS('|', 'INDEX', table_name, index_name, non_unique,
                 GROUP_CONCAT(column_name ORDER BY seq_in_index))
  FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name <> 'second_hand_task'
 GROUP BY table_name, index_name, non_unique
UNION ALL
SELECT CONCAT_WS('|', 'FK', table_name, constraint_name, referenced_table_name, delete_rule)
  FROM information_schema.referential_constraints
 WHERE constraint_schema = DATABASE() AND table_name <> 'second_hand_task'
UNION ALL
SELECT CONCAT_WS('|', 'CHECK', tc.table_name, tc.constraint_name, cc.check_clause)
  FROM information_schema.table_constraints tc
  JOIN information_schema.check_constraints cc
    ON cc.constraint_schema = tc.constraint_schema AND cc.constraint_name = tc.constraint_name
 WHERE tc.table_schema = DATABASE() AND tc.constraint_type = 'CHECK'
ORDER BY 1;
