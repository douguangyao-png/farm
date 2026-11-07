/** IoT 设备页说明文案（第二期 B；原型 08 assetDev）。权限串：farm:assetdevice:query / create / update */
export const DEVICE_TYPES = ['摄像头', '土壤墒情站', '气象站', 'RFID 耳标/脚环']

/** 接入方案说明（真实 MQTT / RTMP 接入已排除，仅作说明） */
export const ACCESS_ROWS: { k: string; v: string }[] = [
  { k: '通信协议', v: 'MQTT 3.1.1 over TLS' },
  { k: '数据上行频率', v: '5 分钟 / 次（可调）' },
  { k: '视频流', v: 'RTMP 推流 + HLS 分发，CDN 加速' },
  { k: '开放方式', v: 'C 端按标的授权，非认养人不可见' }
]

/** 告警规则说明。土壤湿度阈值 22% 与托管模式配置、自管任务触发口径一致（原型此处写 35%，以现有任务引擎的 22% 为准） */
export const ALARM_RULES: { k: string; v: string }[] = [
  { k: '设备离线 > 30 分钟', v: '通知责任人 + 后台待办' },
  { k: '土壤湿度 < 22% 且 48h 无降雨', v: '推送认养人 + 自动生成浇水任务' },
  { k: '温度 > 38℃ 持续 2h', v: '推送高温预警 + 建议遮阳' },
  { k: '摄像头画面静止 > 6h', v: '疑似画面造假，触发人工复核' }
]
