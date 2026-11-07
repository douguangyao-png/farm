// 认养农场 C 端公共方法（farmer-saas 新增）
import dayjs from 'dayjs';
import $store from '@/sheep/store';
import { showAuthModal } from '@/sheep/hooks/useModal';

/** 是否显示「演示数据」标识（R-7）：只在 mock / demo 构建里为 true */
export const SHOW_DEMO = import.meta.env.SHOPRO_MOCK_BADGE === 'true';

export const CARE_MODE_NAME = { full: '全托管', semi: '半托管', self: '不托管' };

export const SUPPLY_CATS = [
  { value: '', label: '全部' },
  { value: 'seed', label: '种子' },
  { value: 'fert', label: '肥料' },
  { value: 'tool', label: '农具' },
  { value: 'bio', label: '生物防治' },
  { value: 'svc', label: '代管服务' },
  { value: 'ins', label: '保险' },
];

export const DELIVER_WAYS = [
  { value: 'store', label: '寄存基地', tip: '放在基地仓库，下次去地里时取用' },
  { value: 'express', label: '快递到家', tip: '寄到您的收货地址' },
  { value: 'pickup', label: '基地自提', tip: '到基地服务台领取' },
  { value: 'apply', label: '农场代施', tip: '由管家直接用到您的地块' },
];
export const DELIVER_WAY_NAME = Object.fromEntries(DELIVER_WAYS.map((x) => [x.value, x.label]));

export const SHIPMENT_STATUS = { 0: '待发货', 10: '运输中', 20: '已签收' };
/** 订单状态（原型：待付款 / 认养中 / 已完成；已取消为系统状态） */
export const ORDER_STATUS = { 0: '待付款', 10: '认养中', 20: '已完成', 30: '已取消', 40: '退款中', 50: '已退款' };
export const ADOPTION_STATUS = { 0: '待支付', 10: '认养中', 20: '已完成', 30: '已终止' };

/** 金额：分 → ¥，整元不带小数，带千分位 */
export function yuan(fen) {
  if (fen === null || fen === undefined || fen === '') return '';
  const v = Number(fen) / 100;
  const whole = Number.isInteger(v);
  const [i, d] = (whole ? String(v) : v.toFixed(2)).split('.');
  const withComma = i.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return '¥' + withComma + (d ? '.' + d : '');
}

/** 毫秒时间戳格式化 */
export function fmtTime(ms, fmt = 'YYYY-MM-DD HH:mm') {
  if (!ms) return '';
  return dayjs(Number(ms)).format(fmt);
}

/** 合同标题，与 mock 的 contractOf 一致：《认养服务合同（半托管）》v2.1 */
export function contractTitle(mode, version) {
  return `《认养服务合同（${CARE_MODE_NAME[mode] || ''}）》${version || ''}`;
}

/** 未登录时弹登录框，返回 true 表示需要登录 */
export function needLogin() {
  if (!$store('user').isLogin) {
    showAuthModal();
    return true;
  }
  return false;
}

/** 调接口并取 data：失败（业务错误已由 request 拦截器弹 toast）返回 null */
export async function call(promise) {
  try {
    const res = await promise;
    if (!res || res.code !== 0) return null;
    return res.data === undefined ? null : res.data;
  } catch (e) {
    return null;
  }
}

/** 调接口并返回完整结果 { code, data, msg }，失败时 code 非 0 */
export async function callRaw(promise) {
  try {
    const res = await promise;
    return res || { code: -1, data: null, msg: '网络请求出错' };
  } catch (e) {
    return { code: e?.code || -1, data: null, msg: e?.msg || '' };
  }
}
