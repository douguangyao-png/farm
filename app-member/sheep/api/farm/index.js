// 认养农场 C 端接口（farmer-saas 新增），契约见 docs/api/farm-接口契约.md 第 4 节
import request from '@/sheep/request';

const get = (url, params, custom = {}) =>
  request({ url, method: 'GET', params, custom: { showLoading: false, ...custom } });
const post = (url, data, custom = {}) =>
  request({ url, method: 'POST', data, custom: { showLoading: true, loadingMsg: '提交中', ...custom } });

const FarmApi = {
  // 首页
  getHome: () => get('/farm/home'),
  // 认养商品
  getProduct: (id) => get('/farm/product/get', { id }),
  // 认养订单
  createOrder: (data) => post('/farm/order/create', data),
  payOrder: (id) => post('/farm/order/pay', { id }, { loadingMsg: '支付中' }),
  getOrder: (id) => get('/farm/order/get', { id }),
  // 我的认养
  getAdoptionList: () => get('/farm/adoption/list'),
  getAdoption: (id) => get('/farm/adoption/get', { id }),
  getAdoptionRecords: (adoptionId) => get('/farm/adoption/records', { adoptionId }),
  getAdoptionTrace: (adoptionId) => get('/farm/adoption/trace', { adoptionId }),
  getAdoptionEnv: (adoptionId) => get('/farm/adoption/env', { adoptionId }),
  getAdoptionCamera: (adoptionId) => get('/farm/adoption/camera', { adoptionId }),
  // 自管任务
  getSelfTaskList: (params) => get('/farm/self-task/list', params),
  doneSelfTask: (id) => post('/farm/self-task/done', { id }),
  entrustSelfTask: (id) => post('/farm/self-task/entrust', { id }),
  // 农资
  getSupplyList: (cat) => get('/farm/supply/list', cat ? { cat } : {}),
  getSupply: (id) => get('/farm/supply/get', { id }),
  createSupplyOrder: (data) => post('/farm/supply-order/create', data),
  getSupplyOrderList: () => get('/farm/supply-order/list'),
  // 农场资讯（后台文章管理里已发布的文章）
  getArticleList: () => get('/farm/article/list'),
  getArticle: (id) => get('/farm/article/get', { id }),
  // 收获配送
  getShipmentList: (adoptionId) => get('/farm/shipment/list', adoptionId ? { adoptionId } : {}),
};

export default FarmApi;
