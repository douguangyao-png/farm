INSERT INTO farm_refund (id, tenant_id, no, order_id, member_id, amount, invest_deduct, deliver_deduct, deduct, refund_amount, reason, status, audit_remark, prev_order_status, create_time, audit_time, creator) VALUES (1, 2, 'R202610300001', 2, 2, 86000, 1200, 0, 1200, 84800, '产出未达约定', 20, '已核对台账，订单仍在约定交付期内，暂不退款；如有疑问请联系管家。', 10, '2026-10-30 10:20:00', '2026-10-30 16:45:00', 'seed');

-- 预置演示证照（每个经营者租户 6 份；检测报告「有效期」取今天 +25 天，演示「30 天内到期」）
INSERT INTO farm_cert (id, tenant_id, name, no, cert_type, valid_until, verify, public_mode, demo, creator) VALUES
 (101, 2, '营业执照', '9233****A', '必备', NULL, 'verified', 'public', 1, 'seed'),
 (102, 2, '土地流转合同', 'LH-2021-0088', '必备', '2029-03-01', 'verified', 'private', 1, 'seed'),
 (103, 2, '农产品质量检测报告', 'CNAS-2026-0918', '建议', CURRENT_DATE + 25, 'verified', 'public', 1, 'seed'),
 (104, 2, '政策性农业保险保单', 'PICC-2026-0188', '必备', '2027-03-15', 'verified', 'public', 1, 'seed'),
 (105, 2, '动物防疫条件合格证', 'FZ-2024-0116', '必备', '2027-06-30', 'verified', 'public', 1, 'seed'),
 (106, 2, '食品经营许可证', 'JY-2023-0421', '按需', '2028-05-20', 'renew', 'public', 1, 'seed'),
 (111, 3, '营业执照', '9233****A', '必备', NULL, 'verified', 'public', 1, 'seed'),
 (112, 3, '土地流转合同', 'LH-2021-0088', '必备', '2029-03-01', 'verified', 'private', 1, 'seed'),
 (113, 3, '农产品质量检测报告', 'CNAS-2026-0918', '建议', CURRENT_DATE + 25, 'verified', 'public', 1, 'seed'),
 (114, 3, '政策性农业保险保单', 'PICC-2026-0188', '必备', '2027-03-15', 'verified', 'public', 1, 'seed'),
 (115, 3, '动物防疫条件合格证', 'FZ-2024-0116', '必备', '2027-06-30', 'verified', 'public', 1, 'seed'),
 (116, 3, '食品经营许可证', 'JY-2023-0421', '按需', '2028-05-20', 'renew', 'public', 1, 'seed');

-- 消息模板（每个经营者租户 6 条）
INSERT INTO farm_msg_template (id, tenant_id, name, trigger_text, channels, content, status, creator)
SELECT t.base + n.i, t.tid, n.name, n.trg, n.ch, n.content, 0, 'seed'
FROM (VALUES (2, 100), (3, 110)) AS t(tid, base),
     (VALUES
       (1, '支付成功通知', '订单支付完成', '["订阅消息"]', '您的认养已生效，合同编号 HT-XXXXXXXX，专属管家将尽快与您联系。'),
       (2, '农事动态提醒', '管家发布记录', '["订阅消息"]', '您家 {地块编号} 有新动态：{记录标题}，点击查看现场照片。'),
       (3, '配送发货通知', '批次发货', '["订阅消息","短信"]', '您的第 {n} 次配送已发出，{承运商}，物流单号 {单号}。'),
       (4, '到期续费提醒', '到期前 30/15/3 天', '["订阅消息"]', '您的认养将于 {到期日} 到期，续养可享老客专属价格。'),
       (5, '异常预警', '设备离线 / 画面静止', '["App 推送"]', '{设备编号} 已离线 30 分钟，请及时处理。'),
       (6, '退款进度', '退款状态变更', '["订阅消息"]', '您的退款申请已通过，款项将在 3 个工作日内原路退回。')
     ) AS n(i, name, trg, ch, content);

SELECT setval('farm_refund_seq', GREATEST((SELECT COALESCE(MAX(id),0) FROM farm_refund)+1,1000), false);
SELECT setval('farm_cert_seq', GREATEST((SELECT COALESCE(MAX(id),0) FROM farm_cert)+1,1000), false);
