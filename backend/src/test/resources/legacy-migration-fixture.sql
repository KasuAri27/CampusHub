-- 仅供隔离的CI迁移库使用；验证原有accepted订单和错误二手子表迁移，不含真实资料。
INSERT INTO users(id,name,email,phone,password_hash,role,credit_score,status) VALUES
 (1,'legacy requester','req@example.invalid','fixture-phone-1','fixture-hash','requester',5,1),
 (2,'legacy provider','pro@example.invalid','fixture-phone-2','fixture-hash','provider',5,1);
INSERT INTO categories(id,name,has_review,sort_order) VALUES(1,'fixture category',1,1);
INSERT INTO tasks(id,requester_id,category_id,task_type,title,description,reward,status) VALUES
 (1,1,1,'RUNNING','fixture running','legacy migration fixture',6.50,'in_progress'),
 (2,1,1,'FLEA','fixture item','legacy second hand fixture',12.34,'published');
INSERT INTO t_task_running(task_id,pickup_location,delivery_location,sub_reward) VALUES(1,'fixture pickup','fixture destination',6.50);
ALTER TABLE orders ADD COLUMN payment_code VARCHAR(255) NULL;
INSERT INTO orders(id,task_id,requester_id,provider_id,status,payment_code) VALUES(1,1,1,2,'accepted','old-proof.png');
CREATE TABLE second_hand_task(task_id BIGINT PRIMARY KEY,trade_mode VARCHAR(10),item_price DECIMAL(10,2),requester_payment_code VARCHAR(255));
INSERT INTO second_hand_task VALUES(2,'SELL',12.34,'old-seller-proof.png');
