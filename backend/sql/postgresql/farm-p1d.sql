-- 第一期补齐 D：农资订单收货信息（快递到家时必填，其它方式可空）。可重复执行。
ALTER TABLE farm_supply_order ADD COLUMN IF NOT EXISTS receiver_name varchar(32) DEFAULT '';
ALTER TABLE farm_supply_order ADD COLUMN IF NOT EXISTS receiver_mobile varchar(16) DEFAULT '';
ALTER TABLE farm_supply_order ADD COLUMN IF NOT EXISTS address varchar(200) DEFAULT '';
