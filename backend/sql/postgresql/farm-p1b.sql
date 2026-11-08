-- 第一期补齐新增业务表（配送批次、畜禽批次）。在 farm.sql / farm-p2.sql 之后执行，可重复执行（先 DROP 再建）。
-- 订单补字段、收成环境快照、配送单 batch_id 已直接写在 farm.sql 的建表定义里，本文件不重复。
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['farm_ship_batch','farm_livestock_batch']
  LOOP
    EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', t);
    EXECUTE format('DROP SEQUENCE IF EXISTS %I', t || '_seq');
    EXECUTE format('CREATE SEQUENCE %I START 1000 INCREMENT 1', t || '_seq');
  END LOOP;
END $$;

-- 配送批次：status 0 待发 / 10 已发出 / 20 已完成
CREATE TABLE farm_ship_batch (
  id int8 PRIMARY KEY, no varchar(32) NOT NULL, harvest_id int8 NOT NULL, plan_date date NOT NULL,
  status int2 NOT NULL DEFAULT 0, remark varchar(255) DEFAULT '',
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
CREATE INDEX idx_farm_ship_batch_tenant ON farm_ship_batch (tenant_id, status, create_time DESC);
CREATE INDEX idx_farm_shipment_batch ON farm_shipment (tenant_id, batch_id);

-- 畜禽批次：status 0 在栏 / 10 出栏中 / 20 已出栏
CREATE TABLE farm_livestock_batch (
  id int8 PRIMARY KEY, no varchar(32) NOT NULL, base_id int8 NOT NULL, species varchar(32) NOT NULL, breed varchar(64) DEFAULT '',
  in_date date NOT NULL, count int4 NOT NULL DEFAULT 0, out_count int4 NOT NULL DEFAULT 0, status int2 NOT NULL DEFAULT 0,
  pen varchar(64) DEFAULT '', remark varchar(255) DEFAULT '',
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
CREATE INDEX idx_farm_livestock_batch_tenant ON farm_livestock_batch (tenant_id, status, create_time DESC);
