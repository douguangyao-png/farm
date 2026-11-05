// 认养农场 C 端（farmer-saas 新增）：底部导航和公共常量
//
// 底部导航本应来自店铺装修模板（/promotion/diy-template/used 的 property.tabBar）。
// 演示阶段 mock 的模板只有「首页 / 我的」两项且指向商城页，所以这里写死认养农场的四个入口，
// 由 sheep/store/app.js 的 adaptTemplate 覆盖模板里的 tabBar。接真后端后可把同样的数据配进装修模板。

const svg = (body, color) =>
  'data:image/svg+xml;charset=utf-8,' +
  encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${body}</svg>`,
  );

const ICONS = {
  home: '<path d="M3 11l9-7 9 7"/><path d="M5 10v10h14V10"/><path d="M10 20v-6h4v6"/>',
  adopt: '<path d="M12 21c0-6 0-9 0-12"/><path d="M12 12c-4 0-7-2.5-7-7 4 0 7 2.5 7 7z"/><path d="M12 9c0-3.5 2.5-6 6.5-6 0 3.5-2.5 6-6.5 6z"/><path d="M4 21h16"/>',
  supply: '<path d="M4 8h16l-1.5 12h-13z"/><path d="M8 8V6a4 4 0 0 1 8 0v2"/>',
  mine: '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4.4 3.6-7 8-7s8 2.6 8 7"/>',
};

const INACTIVE = '#8a8a8a';
const ACTIVE = '#22a06b';

const item = (text, url, icon) => ({
  text,
  url,
  iconUrl: svg(ICONS[icon], INACTIVE),
  activeIconUrl: svg(ICONS[icon], ACTIVE),
});

export const FARM_TABBAR = {
  theme: 'green',
  mode: 1,
  style: { bgType: 'color', bgColor: '#ffffff', color: INACTIVE, activeColor: ACTIVE },
  items: [
    item('首页', '/pages/farm/index', 'home'),
    item('认养', '/pages/farm/adoption/list', 'adopt'),
    item('农资', '/pages/farm/supply/list', 'supply'),
    item('我的', '/pages/farm/mine', 'mine'),
  ],
};
