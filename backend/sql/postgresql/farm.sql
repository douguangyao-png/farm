-- 认养农场业务表（PostgreSQL）。对应 mock-server/src/store.ts 的实体，字段口径见 docs/api/farm-接口契约.md
-- 约定：金额=分(int8)；重量=斤(numeric(10,1))；JSON 字段用 text 存，由 Java 侧 JacksonTypeHandler 读写；
-- 每张表都带芋道的审计列 + tenant_id；主键序列名 = 表名_seq（@KeySequence）。

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['farm_base','farm_asset','farm_product','farm_member','farm_order','farm_adoption','farm_record','farm_task',
    'farm_service_order','farm_supply','farm_supply_order','farm_input_record','farm_harvest','farm_shipment','farm_risk_alert',
    'farm_article_category','farm_article','farm_feedback','farm_compliance_hit','farm_steward']
  LOOP
    EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', t);
    EXECUTE format('DROP SEQUENCE IF EXISTS %I', t || '_seq');
    EXECUTE format('CREATE SEQUENCE %I START 1000 INCREMENT 1', t || '_seq');
  END LOOP;
END $$;

-- 基地
CREATE TABLE farm_base (
  id int8 PRIMARY KEY, name varchar(100) NOT NULL, address varchar(255) NOT NULL,
  lng numeric(10,6) NOT NULL DEFAULT 0, lat numeric(10,6) NOT NULL DEFAULT 0, area_mu numeric(10,1) NOT NULL DEFAULT 0,
  cert_no varchar(100) DEFAULT '', crop_summary varchar(255) DEFAULT '', status int2 NOT NULL DEFAULT 0, cover text DEFAULT '',
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 资产：地块 / 畜禽 / 果树
CREATE TABLE farm_asset (
  id int8 PRIMARY KEY, base_id int8 NOT NULL, asset_type varchar(16) NOT NULL, code varchar(64) NOT NULL, name varchar(100) NOT NULL,
  specs text DEFAULT '{}', status int2 NOT NULL DEFAULT 0, camera_name varchar(100), sensor_name varchar(100),
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 认养商品（三档托管配置放 care_modes JSON）
CREATE TABLE farm_product (
  id int8 PRIMARY KEY, base_id int8 NOT NULL, asset_type varchar(16) NOT NULL, title varchar(200) NOT NULL, cover text DEFAULT '',
  description text DEFAULT '', cycle_days int4 NOT NULL DEFAULT 365, area_m2 numeric(10,1), base_price int8 NOT NULL DEFAULT 0,
  stock int4 NOT NULL DEFAULT 0, sold int4 NOT NULL DEFAULT 0, status int2 NOT NULL DEFAULT 0, care_modes text NOT NULL DEFAULT '[]',
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- C 端会员（认养人）
CREATE TABLE farm_member (
  id int8 PRIMARY KEY, mobile varchar(20) NOT NULL, password varchar(100) NOT NULL, nickname varchar(64) NOT NULL DEFAULT '',
  avatar text DEFAULT '', address varchar(255) DEFAULT '',
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 认养订单
CREATE TABLE farm_order (
  id int8 PRIMARY KEY, no varchar(32) NOT NULL, member_id int8 NOT NULL, product_id int8 NOT NULL, care_mode varchar(8) NOT NULL,
  amount int8 NOT NULL DEFAULT 0, status int2 NOT NULL DEFAULT 0, asset_id int8, adoption_id int8, contract_version varchar(16) DEFAULT '',
  contract_agreed bool NOT NULL DEFAULT false, receiver_name varchar(64), receiver_mobile varchar(20), address varchar(255), pay_time timestamp,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 认养关系（订单支付后产生）
CREATE TABLE farm_adoption (
  id int8 PRIMARY KEY, order_id int8 NOT NULL, member_id int8 NOT NULL, product_id int8 NOT NULL, asset_id int8 NOT NULL, care_mode varchar(8) NOT NULL,
  start_at timestamp NOT NULL, end_at timestamp NOT NULL, status int2 NOT NULL DEFAULT 0, promised_jin numeric(10,1), harvested_jin numeric(10,1) NOT NULL DEFAULT 0,
  steward_id int8,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 农事记录（生长档案）
CREATE TABLE farm_record (
  id int8 PRIMARY KEY, adoption_id int8, asset_id int8 NOT NULL, steward_id int8, task_id int8, kind varchar(16) NOT NULL DEFAULT 'farming',
  title varchar(200) NOT NULL, content text DEFAULT '', photos text DEFAULT '[]', lng numeric(10,6) DEFAULT 0, lat numeric(10,6) DEFAULT 0,
  env text DEFAULT '{}', checks text DEFAULT '{}', status int2 NOT NULL DEFAULT 0, audit_remark varchar(255), audit_time timestamp,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 农事任务（管家任务 / 认养人自管任务）
CREATE TABLE farm_task (
  id int8 PRIMARY KEY, adoption_id int8, asset_id int8 NOT NULL, owner varchar(16) NOT NULL, steward_id int8, title varchar(200) NOT NULL,
  description text DEFAULT '', source varchar(16) NOT NULL DEFAULT 'manual', service_order_id int8, deadline timestamp NOT NULL,
  status int2 NOT NULL DEFAULT 0, entrust_price int8, finish_time timestamp,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 代管工单
CREATE TABLE farm_service_order (
  id int8 PRIMARY KEY, no varchar(32) NOT NULL, from_task_id int8, task_title varchar(200) DEFAULT '', adoption_id int8, asset_id int8 NOT NULL,
  member_id int8 NOT NULL, steward_id int8, service_type varchar(32) DEFAULT '', price int8 NOT NULL DEFAULT 0, status int2 NOT NULL DEFAULT 0,
  split text DEFAULT '{}', proof_photos text DEFAULT '[]', remark varchar(255) DEFAULT '', accept_time timestamp, finish_time timestamp,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 农资商品
CREATE TABLE farm_supply (
  id int8 PRIMARY KEY, name varchar(100) NOT NULL, cat varchar(16) NOT NULL, cover text DEFAULT '', price int8 NOT NULL DEFAULT 0, cost int8 NOT NULL DEFAULT 0,
  stock int4 NOT NULL DEFAULT 0, unit varchar(16) DEFAULT '', spec varchar(64) DEFAULT '', fit_modes text DEFAULT '[]', batch_no varchar(64) DEFAULT '',
  retail_allowed bool NOT NULL DEFAULT true, need_workorder bool NOT NULL DEFAULT false, status int2 NOT NULL DEFAULT 0, sold int4 NOT NULL DEFAULT 0,
  description text DEFAULT '',
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 农资订单
CREATE TABLE farm_supply_order (
  id int8 PRIMARY KEY, no varchar(32) NOT NULL, member_id int8 NOT NULL, adoption_id int8 NOT NULL, asset_id int8 NOT NULL, spu_id int8 NOT NULL,
  qty int4 NOT NULL DEFAULT 1, amount int8 NOT NULL DEFAULT 0, deliver_way varchar(16) NOT NULL, batch_no varchar(64) DEFAULT '',
  trace_state int2 NOT NULL DEFAULT 0, used_at timestamp, dosage varchar(64),
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 投入品台账
CREATE TABLE farm_input_record (
  id int8 PRIMARY KEY, asset_id int8 NOT NULL, source varchar(16) NOT NULL, name varchar(100) NOT NULL, batch_no varchar(64) DEFAULT '',
  used_at timestamp NOT NULL, dosage varchar(64) DEFAULT '', operator varchar(64) DEFAULT '',
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 收成
CREATE TABLE farm_harvest (
  id int8 PRIMARY KEY, adoption_id int8 NOT NULL, asset_id int8 NOT NULL, crop varchar(64) NOT NULL, weight_jin numeric(10,1) NOT NULL,
  harvest_date date NOT NULL, photos text DEFAULT '[]', remark varchar(255) DEFAULT '', shipment_id int8,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 配送批次
CREATE TABLE farm_shipment (
  id int8 PRIMARY KEY, no varchar(32) NOT NULL, harvest_id int8 NOT NULL, adoption_id int8 NOT NULL, member_id int8 NOT NULL,
  receiver_name varchar(64) DEFAULT '', receiver_mobile varchar(20) DEFAULT '', address varchar(255) DEFAULT '', weight_jin numeric(10,1) NOT NULL DEFAULT 0,
  carrier varchar(64) DEFAULT '', logistics_no varchar(64) DEFAULT '', cold_chain bool NOT NULL DEFAULT false, status int2 NOT NULL DEFAULT 0,
  tracks text DEFAULT '[]',
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 风控预警（平台处理；按租户记录）
CREATE TABLE farm_risk_alert (
  id int8 PRIMARY KEY, type varchar(16) NOT NULL, level int2 NOT NULL DEFAULT 1, title varchar(200) NOT NULL, content text DEFAULT '', source varchar(64) DEFAULT '',
  status int2 NOT NULL DEFAULT 0, handle_remark varchar(255), handle_time timestamp,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 文章分类、文章
CREATE TABLE farm_article_category (
  id int8 PRIMARY KEY, name varchar(64) NOT NULL, pic_url text DEFAULT '', sort int4 NOT NULL DEFAULT 0, status int2 NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
CREATE TABLE farm_article (
  id int8 PRIMARY KEY, category_id int8 NOT NULL, title varchar(200) NOT NULL, author varchar(64) DEFAULT '', pic_url text DEFAULT '', introduction varchar(500) DEFAULT '',
  content text DEFAULT '', sort int4 NOT NULL DEFAULT 0, status int2 NOT NULL DEFAULT 0, browse_count int4 NOT NULL DEFAULT 0,
  recommend_hot bool NOT NULL DEFAULT false, recommend_banner bool NOT NULL DEFAULT false, spu_id int8 NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 问题反馈（平台看全部租户）
CREATE TABLE farm_feedback (
  id int8 PRIMARY KEY, no varchar(16) NOT NULL, type varchar(16) NOT NULL, content text NOT NULL, screenshots text DEFAULT '[]',
  page_title varchar(100) DEFAULT '', page_path varchar(255) DEFAULT '', user_agent varchar(255) DEFAULT '', creator_id int8, creator_name varchar(64) DEFAULT '',
  status int2 NOT NULL DEFAULT 0, replies text DEFAULT '[]',
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 违禁词拦截记录（风险自检用）
CREATE TABLE farm_compliance_hit (
  id int8 PRIMARY KEY, product_id int8, product_title varchar(200) DEFAULT '', words text DEFAULT '[]',
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);

-- 管家与基地的绑定（芋道用户表没有 baseId）；user_id = system_users.id
CREATE TABLE farm_steward (
  id int8 PRIMARY KEY, user_id int8 NOT NULL, base_id int8 NOT NULL,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);

CREATE INDEX idx_farm_asset_base ON farm_asset (tenant_id, base_id);
CREATE INDEX idx_farm_order_tenant ON farm_order (tenant_id, create_time DESC);
CREATE INDEX idx_farm_adoption_member ON farm_adoption (tenant_id, member_id);
CREATE INDEX idx_farm_record_adoption ON farm_record (tenant_id, adoption_id);
CREATE INDEX idx_farm_task_owner ON farm_task (tenant_id, owner, status);
