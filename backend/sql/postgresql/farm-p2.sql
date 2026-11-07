-- 第二期新增业务表（退款/证照/资金公示/消息模板/设备/C 端小数据/管家端）。已有库只执行本文件即可（不影响一期表与数据）。
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['farm_refund','farm_cert','farm_fund_notice','farm_msg_template','farm_device','farm_app_extra','farm_steward_alarm','farm_steward_message','farm_steward_remind']
  LOOP
    EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', t);
    EXECUTE format('DROP SEQUENCE IF EXISTS %I', t || '_seq');
    EXECUTE format('CREATE SEQUENCE %I START 1000 INCREMENT 1', t || '_seq');
  END LOOP;
END $$;
-- 退款单（第二期 A）。金额单位：分
CREATE TABLE farm_refund (
  id int8 PRIMARY KEY, no varchar(32) NOT NULL, order_id int8 NOT NULL, member_id int8 NOT NULL,
  amount int8 NOT NULL DEFAULT 0, invest_deduct int8 NOT NULL DEFAULT 0, deliver_deduct int8 NOT NULL DEFAULT 0,
  deduct int8 NOT NULL DEFAULT 0, refund_amount int8 NOT NULL DEFAULT 0,
  reason varchar(255) NOT NULL, status int2 NOT NULL DEFAULT 0, audit_remark varchar(255), prev_order_status int2 NOT NULL DEFAULT 10,
  audit_time timestamp,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
CREATE INDEX idx_farm_refund_order ON farm_refund (tenant_id, order_id);
CREATE INDEX idx_farm_refund_member ON farm_refund (tenant_id, member_id, create_time DESC);
CREATE INDEX idx_farm_refund_status ON farm_refund (tenant_id, status, create_time DESC);

-- 资质证照：预置演示证照（demo=1）+ 经营者上传的证照。基地备案证不入这张表，由 farm_base.cert_no 派生
CREATE TABLE farm_cert (
  id int8 PRIMARY KEY, name varchar(100) NOT NULL, no varchar(100) NOT NULL, cert_type varchar(8) NOT NULL,
  valid_until timestamp, verify varchar(16) NOT NULL DEFAULT 'pending', public_mode varchar(16) NOT NULL DEFAULT 'public',
  file_url text DEFAULT '', demo int2 NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 资金公示开关：每个租户一行，fields 是 JSON（text 存，JacksonTypeHandler 读写）
CREATE TABLE farm_fund_notice (
  id int8 PRIMARY KEY, fields text NOT NULL DEFAULT '{}',
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 消息模板（trigger 是 SQL 关键字，列名用 trigger_text）
CREATE TABLE farm_msg_template (
  id int8 PRIMARY KEY, name varchar(100) NOT NULL, trigger_text varchar(100) NOT NULL, channels text NOT NULL DEFAULT '[]',
  content text NOT NULL, status int2 NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- IoT 设备台账：custom=1 是手动添加的设备；custom=0 是对「资产派生设备」的覆盖（改位置/固件、派单），code 与派生编号一致
CREATE TABLE farm_device (
  id int8 PRIMARY KEY, code varchar(32) NOT NULL, custom int2 NOT NULL DEFAULT 0, kind varchar(16) NOT NULL DEFAULT '',
  type_name varchar(32) NOT NULL DEFAULT '', name varchar(100) NOT NULL DEFAULT '', position varchar(100) DEFAULT '',
  bind_text varchar(255) DEFAULT '', firmware varchar(32) DEFAULT '', dispatched int2 NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);

CREATE TABLE farm_app_extra (
  id int8 PRIMARY KEY, member_id int8 NOT NULL, kind varchar(16) NOT NULL, target_id int8,
  payload text NOT NULL DEFAULT '{}',
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT now(),
  updater varchar(64) DEFAULT '', update_time timestamp NOT NULL DEFAULT now(),
  deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
CREATE INDEX idx_farm_app_extra_member ON farm_app_extra (tenant_id, member_id, kind);
CREATE INDEX idx_farm_app_extra_target ON farm_app_extra (tenant_id, kind, target_id);
COMMENT ON TABLE farm_app_extra IS 'C 端会员小数据：favorite 收藏 / address 地址 / review 评价(target=商品) / visit 预约到访(target=基地) / transfer 转赠(target=认养) / chat 管家聊天 / msg_read 消息已读';


-- 管家异常上报（设备异常 / 损耗登记 / 气象灾害 / 病虫害爆发）
CREATE TABLE farm_steward_alarm (
  id int8 PRIMARY KEY, steward_id int8 NOT NULL, type varchar(16) NOT NULL, level varchar(4) NOT NULL DEFAULT '中', asset_id int8,
  title varchar(200) NOT NULL, content text DEFAULT '', qty int4, photos text DEFAULT '[]', insurance_reported bool NOT NULL DEFAULT false, status int2 NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 管家与认养人聊天
CREATE TABLE farm_steward_message (
  id int8 PRIMARY KEY, steward_id int8 NOT NULL, member_id int8 NOT NULL, asset_id int8, sender varchar(16) NOT NULL, content varchar(1000) NOT NULL,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
CREATE INDEX idx_farm_steward_message_pair ON farm_steward_message (steward_id, member_id, create_time);
-- 自管提醒记录（哪条自管任务已被管家提醒 / 推送过委托代办）
CREATE TABLE farm_steward_remind (
  id int8 PRIMARY KEY, steward_id int8 NOT NULL, task_id int8 NOT NULL, entrust bool NOT NULL DEFAULT false, remind_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
