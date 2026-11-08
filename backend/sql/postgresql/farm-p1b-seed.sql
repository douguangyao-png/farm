-- 第一期补齐演示数据（与 mock-server/seed 一致）。在 farm-p1b.sql 之后执行。
-- 订单补字段：只给前 3 单设置有差异的渠道/交付方式，其余用列默认值（h5 / express）
UPDATE farm_order SET channel='mini', package_name='全托管 · 一年', coupon_name='新人券', coupon_amount=5000, gps_lng=120.155100, gps_lat=30.274100, gps_text='杭州市西湖区', deliver_way='express' WHERE id=1;
UPDATE farm_order SET channel='scan', package_name='半托管 · 一年', deliver_way='pickup' WHERE id=2;
UPDATE farm_order SET channel='steward', package_name='半托管 · 一年', deliver_way='farm_deliver' WHERE id=3;

INSERT INTO farm_ship_batch (id, tenant_id, no, harvest_id, plan_date, status, remark, create_time, creator) VALUES (1, 2, 'PC20261102001', 1, '2026-11-02', 20, '第一批生菜、萝卜（演示）', '2026-11-02 10:00:00', 'seed');
INSERT INTO farm_ship_batch (id, tenant_id, no, harvest_id, plan_date, status, remark, create_time, creator) VALUES (2, 2, 'PC20261108002', 2, '2026-11-09', 0, '待发的生菜批次（演示）', '2026-11-08 09:00:00', 'seed');
UPDATE farm_shipment SET batch_id=1 WHERE id=1;

INSERT INTO farm_livestock_batch (id, tenant_id, no, base_id, species, breed, in_date, count, out_count, status, pen, remark, create_time, creator) VALUES (1, 2, 'XB20261008001', 1, '鸡', '三黄鸡', '2026-10-08', 200, 0, 0, 'A 栏', '春季雏鸡批次（演示）', '2026-10-08 09:00:00', 'seed');
INSERT INTO farm_livestock_batch (id, tenant_id, no, base_id, species, breed, in_date, count, out_count, status, pen, remark, create_time, creator) VALUES (2, 2, 'XB20260915002', 1, '鸭', '绍兴麻鸭', '2026-09-15', 120, 40, 10, 'B 栏', '已出栏 40 只（演示）', '2026-09-15 09:00:00', 'seed');
INSERT INTO farm_livestock_batch (id, tenant_id, no, base_id, species, breed, in_date, count, out_count, status, pen, remark, create_time, creator) VALUES (3, 3, 'XB20261001003', 3, '鹅', '太湖白鹅', '2026-10-01', 80, 0, 0, '东棚', '（演示）', '2026-10-01 09:00:00', 'seed');

SELECT setval('farm_ship_batch_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM farm_ship_batch) + 1, 1000), false);
SELECT setval('farm_livestock_batch_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM farm_livestock_batch) + 1, 1000), false);
