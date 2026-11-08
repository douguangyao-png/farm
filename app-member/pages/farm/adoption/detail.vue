<!-- 认养详情（farmer-saas 新增）：概览、生长档案、溯源、环境数据、监控画面、收获配送（含确认收货） -->
<template>
  <s-layout title="认养详情">
    <demo-badge />
    <view class="f-page" v-if="a">
      <!-- 头部 -->
      <view class="f-card head">
        <view class="f-between">
          <text class="title">{{ a.productTitle }}</text>
          <text class="f-tag" :class="a.careMode === 'full' ? 'solid' : a.careMode === 'self' ? 'orange' : ''">
            {{ a.careModeName || CARE_MODE_NAME[a.careMode] }}
          </text>
        </view>
        <view class="plot">
          地块 <text class="code">{{ a.assetCode }}</text>
          <text class="f-sub" v-if="a.asset"> · {{ a.asset.name }} · {{ a.asset.areaM2 }}㎡</text>
        </view>
        <view class="f-sub" v-if="a.asset">种植：{{ a.asset.crop }}</view>
        <view class="f-sub">{{ a.baseName }} · {{ fmtTime(a.startAt, 'YYYY-MM-DD') }} 至 {{ fmtTime(a.endAt, 'YYYY-MM-DD') }}</view>
        <view class="f-sub" v-if="a.steward">管家：{{ a.steward.name }} {{ a.steward.mobile }}</view>
      </view>

      <!-- 分区 -->
      <view class="f-tabs">
        <view v-for="t in TABS" :key="t.key" class="f-tab" :class="{ active: tab === t.key }" @tap="tab = t.key">{{ t.label }}</view>
      </view>

      <!-- 概览 -->
      <view v-show="tab === 'overview'">
        <view class="f-card" v-if="a.promisedJin !== null && a.promisedJin !== undefined">
          <view class="f-title">约定交付</view>
          <view class="f-between f-sub bar-label">
            <text>已收 {{ a.harvestedJin || 0 }} 斤</text>
            <text>约定 {{ a.promisedJin }} 斤</text>
          </view>
          <view class="f-progress"><view class="bar" :style="{ width: pct + '%' }" /></view>
          <view class="f-sub promise-note">约定交付 {{ a.promisedJin }} 斤，不足部分按同等品质补发</view>
        </view>
        <view class="f-card" v-else>
          <view class="f-title">产出</view>
          <view class="f-kv"><text class="k">已收</text><text class="v">{{ a.harvestedJin || 0 }} 斤</text></view>
          <view class="f-sub">{{ a.careModeInfo?.outputPromise }}</view>
        </view>

        <view class="f-card" v-if="a.careModeInfo">
          <view class="f-title">托管方式 · {{ CARE_MODE_NAME[a.careMode] }}</view>
          <view class="slogan">「{{ a.careModeInfo.slogan }}」</view>
          <view class="f-kv"><text class="k">合同</text><text class="v">{{ contractTitle(a.careMode, a.careModeInfo.contractVersion) }}</text></view>
          <view class="f-kv" @tap="toContract"><text class="k">合同全文</text><text class="v">查看合同 ›</text></view>
          <view class="f-kv"><text class="k">保险</text><text class="v">{{ a.careModeInfo.insurance }}</text></view>
          <view class="f-kv"><text class="k">您负责</text><text class="v">{{ memberDuty.length ? memberDuty.join('、') : '无，全部由农场完成' }}</text></view>
        </view>

        <view class="f-card">
          <view class="entries">
            <view class="entry" @tap="go('/pages/farm/self-task/list', { adoptionId: a.id })">
              <view class="e-icon">✓</view>
              <view>自管任务</view>
              <view class="badge" v-if="a.selfTodoCount > 0">{{ a.selfTodoCount }}</view>
            </view>
            <view class="entry" @tap="go('/pages/farm/supply/list')">
              <view class="e-icon">肥</view>
              <view>农资商城</view>
            </view>
            <view class="entry" @tap="tab = 'harvest'">
              <view class="e-icon">车</view>
              <view>收获配送</view>
            </view>
            <view class="entry" @tap="go('/pages/farm/supply/order-list')">
              <view class="e-icon">单</view>
              <view>我的农资</view>
            </view>
            <view class="entry" @tap="go('/pages/farm/report/index', { adoptionId: a.id })">
              <view class="e-icon">检</view>
              <view>质检报告</view>
            </view>
            <view class="entry" @tap="go('/pages/farm/visit/index')">
              <view class="e-icon">访</view>
              <view>预约到访</view>
            </view>
            <view class="entry" @tap="go('/pages/farm/service/chat')">
              <view class="e-icon">管</view>
              <view>专属管家</view>
            </view>
            <view class="entry" @tap="go('/pages/farm/transfer/index', { adoptionId: a.id })">
              <view class="e-icon">赠</view>
              <view>认养转赠</view>
            </view>
            <view class="entry" @tap="go('/pages/farm/review/edit', { adoptionId: a.id })">
              <view class="e-icon">评</view>
              <view>发表评价</view>
            </view>
          </view>
        </view>
      </view>

      <!-- 收获配送（原型 07 adoption 的「收获配送」页签） -->
      <view v-show="tab === 'harvest'">
        <view class="f-card">
          <view class="f-between bar-label">
            <text class="f-title no-mb">产出统计</text>
            <text class="f-sub" v-if="a.promisedJin !== null && a.promisedJin !== undefined">约定交付 {{ a.promisedJin }} 斤</text>
          </view>
          <template v-if="a.promisedJin !== null && a.promisedJin !== undefined">
            <view class="f-progress"><view class="bar" :style="{ width: pct + '%' }" /></view>
            <view class="f-between bar-label top">
              <text class="f-sub">已收 {{ a.harvestedJin || 0 }} 斤</text>
              <text class="f-sub">完成 {{ pct }}%</text>
            </view>
            <view class="f-sub promise-note">约定交付 {{ a.promisedJin }} 斤，不足部分按同等品质补发</view>
          </template>
          <template v-else>
            <view class="f-kv"><text class="k">已收</text><text class="v">{{ a.harvestedJin || 0 }} 斤</text></view>
            <view class="f-sub">{{ a.careModeInfo?.outputPromise }}</view>
          </template>
        </view>

        <view class="f-title sec-title">配送批次<text class="f-more">共 {{ shipments.length }} 次</text></view>
        <view class="f-card" v-for="(s, i) in shipmentRows" :key="s.id" @tap="toggleTrack(s.id)">
          <view class="f-between">
            <view class="f-row">
              <text class="f-tag" :class="s.status === 20 ? 'gray' : 'orange'">第 {{ s.seq }} 次</text>
              <text class="f-sub">{{ fmtTime(s.createTime, 'YYYY-MM-DD') }}</text>
            </view>
            <text class="s-status" :class="{ done: s.status === 20 }">{{ SHIPMENT_STATUS[s.status] }}</text>
          </view>
          <view class="s-line">{{ s.weightJin }} 斤<text class="f-tag" v-if="s.coldChain">冷链</text></view>
          <view class="f-between foot">
            <text class="f-sub">{{ s.carrier || '—' }} {{ s.logisticsNo }}</text>
            <text class="f-more" @tap.stop="go('/pages/farm/logistics/detail', { id: s.id })">物流详情 ›</text>
          </view>
          <view class="f-timeline tracks" v-if="openId === s.id && s.tracks?.length">
            <view class="f-tl-item" :class="{ gray: ti > 0 }" v-for="(t, ti) in [...s.tracks].reverse()" :key="ti">
              <view class="t-content">{{ t.content }}</view>
              <view class="f-sub">{{ fmtTime(t.time) }}</view>
            </view>
          </view>
          <view class="f-sub addr" v-if="openId === s.id">收货人 {{ s.receiverName }} {{ s.receiverMobile }} · {{ s.address }}</view>
          <view class="confirm-row" v-if="s.status === 10">
            <button class="f-btn mini" @tap.stop="confirm(s)">确认收货</button>
          </view>
        </view>
        <view v-if="!shipments.length" class="f-empty">
          <view class="f-empty-title">还没有配送记录</view>
          <view>采收后农场会安排配送，物流信息会显示在这里</view>
        </view>

        <view class="f-warn">采收后农场会安排冷链配送。收到农产品后请点「确认收货」，系统会记录签收时间；配送方式需要调整时请联系专属管家。</view>
        <view class="btn-row">
          <button class="f-btn ghost" @tap="adjustDelivery">调整配送</button>
          <button class="f-btn" @tap="confirmLatest">确认收货</button>
        </view>
      </view>

      <!-- 生长档案 -->
      <view v-show="tab === 'records'" class="f-card">
        <view class="f-title">生长档案<text class="f-more">管家每次农事都拍照留档</text></view>
        <view class="f-timeline" v-if="records.length">
          <view class="f-tl-item" v-for="r in records" :key="r.id">
            <view class="f-between">
              <text class="r-title">{{ r.title }}</text>
              <text class="f-sub">{{ fmtTime(r.createTime, 'MM-DD HH:mm') }}</text>
            </view>
            <view class="r-content">{{ r.content }}</view>
            <view class="f-more" style="text-align: right" @tap="go('/pages/farm/record/detail', { id: r.id })">记录详情 ›</view>
            <view class="photos" v-if="r.photos?.length">
              <image v-for="(ph, i) in r.photos" :key="i" class="photo" :src="ph" mode="aspectFill" @tap="preview(r.photos, i)" />
            </view>
            <view class="r-meta">
              <text class="f-tag gray" v-if="r.stewardName">{{ r.stewardName }}</text>
              <text class="f-tag" v-if="r.checks?.gpsFence">定位在地块内</text>
              <text class="f-tag" v-if="r.checks?.exifTime">拍摄时间已核验</text>
              <text class="f-tag gray" v-if="r.env">气温 {{ r.env.airTemp }}℃ · 土壤湿度 {{ r.env.soilMoisture }}%</text>
            </view>
          </view>
        </view>
        <view v-else class="f-empty">还没有农事记录，管家打卡并审核通过后会出现在这里</view>
      </view>

      <!-- 溯源 -->
      <view v-show="tab === 'trace'">
        <view class="f-warn" v-if="trace?.pendingTrace">有自购农资还没登记使用情况，档案标「待补充」；管家登记用量后自动写入。</view>
        <view class="f-card">
          <view class="f-title">溯源档案</view>
          <view class="f-timeline">
            <view class="f-tl-item" :class="{ gray: !s.time }" v-for="s in trace?.stages || []" :key="s.key">
              <view class="f-between">
                <text class="r-title">{{ s.title }}</text>
                <text class="f-sub">{{ s.time ? fmtTime(s.time, 'YYYY-MM-DD') : '—' }}</text>
              </view>
              <view class="r-content">{{ s.content }}</view>
            </view>
          </view>
        </view>
        <view class="f-card">
          <view class="f-title">投入品记录</view>
          <view v-if="trace?.inputs?.length">
            <view class="input-row" v-for="i in trace.inputs" :key="i.id">
              <view class="f-between">
                <text class="r-title">{{ i.name }}</text>
                <text class="f-tag" :class="i.source === 'member' ? 'orange' : ''">{{ i.source === 'member' ? '认养人自购' : '农场投入' }}</text>
              </view>
              <view class="f-sub">批次 {{ i.batchNo }} · 用量 {{ i.dosage }} · {{ fmtTime(i.usedAt, 'YYYY-MM-DD') }}<text v-if="i.operator"> · {{ i.operator }}</text></view>
            </view>
          </view>
          <view v-else class="f-sub">
            暂无投入品记录<text v-if="trace?.pendingTrace">（待补充）</text>
          </view>
        </view>
      </view>

      <!-- 环境数据 -->
      <view v-show="tab === 'env'" class="f-card">
        <view class="f-title">
          环境数据
          <text class="f-tag orange">演示数据</text>
        </view>
        <template v-if="env">
          <view class="f-sub">{{ env.sensorName }} · 更新于 {{ fmtTime(env.current?.updateTime, 'MM-DD HH:mm') }}</view>
          <view class="env-grid">
            <view class="env-cell" v-for="c in envCells" :key="c.label">
              <view class="env-val">{{ c.value }}<text class="env-unit">{{ c.unit }}</text></view>
              <view class="f-sub">{{ c.label }}</view>
            </view>
          </view>
          <view class="f-title chart-title">近 24 小时</view>
          <image class="chart" :src="chartSrc" mode="widthFix" />
          <view class="legend">
            <text class="lg"><text class="dot" style="background: #ff8f1f" />气温 ℃</text>
            <text class="lg"><text class="dot" style="background: #3b82f6" />空气湿度 %</text>
            <text class="lg"><text class="dot" style="background: #22a06b" />土壤湿度 %</text>
          </view>
        </template>
        <view v-else class="f-sub">暂无环境数据</view>
      </view>

      <!-- 监控画面 -->
      <view v-show="tab === 'camera'" class="f-card">
        <view class="f-title">
          监控画面
          <text class="f-tag" :class="camera?.online ? '' : 'gray'">{{ camera?.online ? '在线' : '离线' }}</text>
        </view>
        <view class="video" v-if="camera" @tap="play">
          <image class="poster" :src="camera.poster" mode="aspectFill" />
          <view class="play"><view class="tri" /></view>
          <view class="cam-name">{{ camera.name }}</view>
        </view>
        <view class="f-sub cam-tip">{{ camera?.tip }}</view>
      </view>
    </view>
  </s-layout>
</template>

<script setup>
  import { computed, ref, watch } from 'vue';
  import { onLoad, onShow } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { CARE_MODE_NAME, SHIPMENT_STATUS, fmtTime, call, callRaw, contractTitle, needLogin } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const TABS = [
    { key: 'overview', label: '概览' },
    { key: 'records', label: '生长档案' },
    { key: 'trace', label: '溯源' },
    { key: 'env', label: '环境' },
    { key: 'camera', label: '监控' },
    { key: 'harvest', label: '收获配送' },
  ];

  const id = ref(0);
  const tab = ref('overview');
  const a = ref(null);
  const records = ref([]);
  const trace = ref(null);
  const env = ref(null);
  const camera = ref(null);
  const shipments = ref([]);
  const openId = ref(0);

  const pct = computed(() =>
    a.value?.promisedJin ? Math.min(100, Math.round(((a.value.harvestedJin || 0) / a.value.promisedJin) * 100)) : 0,
  );
  // 配送单按时间从早到晚编「第 N 次」，列表新的在前
  const shipmentRows = computed(() => {
    const asc = [...shipments.value].sort((x, y) => x.createTime - y.createTime || x.id - y.id);
    const seq = Object.fromEntries(asc.map((s, i) => [s.id, i + 1]));
    return shipments.value.map((s) => ({ ...s, seq: seq[s.id] }));
  });
  const memberDuty = computed(() =>
    (a.value?.careModeInfo?.duty || []).filter((d) => d.owner === 'member').map((d) => d.item),
  );

  const envCells = computed(() => {
    const c = env.value?.current || {};
    return [
      { label: '气温', value: c.airTemp, unit: '℃' },
      { label: '空气湿度', value: c.airHumidity, unit: '%' },
      { label: '土壤湿度', value: c.soilMoisture, unit: '%' },
      { label: '土壤温度', value: c.soilTemp, unit: '℃' },
      { label: '土壤 pH', value: c.ph, unit: '' },
      { label: '光照', value: c.light, unit: 'lux' },
    ];
  });

  // 24 小时曲线：自己拼 SVG，用 <image> 显示（H5、小程序都能用，不依赖图表库）
  const chartSrc = computed(() => {
    const s = env.value?.series || [];
    if (!s.length) return '';
    const W = 640, H = 320, L = 44, R = 14, T = 22, B = 40;
    const keys = [
      { k: 'airTemp', c: '#ff8f1f' },
      { k: 'airHumidity', c: '#3b82f6' },
      { k: 'soilMoisture', c: '#22a06b' },
    ];
    const all = s.flatMap((p) => keys.map((x) => Number(p[x.k]))).filter((v) => !Number.isNaN(v));
    const lo = Math.floor(Math.min(...all) / 10) * 10;
    const hi = Math.ceil(Math.max(...all) / 10) * 10 || 10;
    const x = (i) => L + ((W - L - R) * i) / Math.max(1, s.length - 1);
    const y = (v) => T + (H - T - B) * (1 - (v - lo) / (hi - lo || 1));
    let g = '';
    const steps = 4;
    for (let i = 0; i <= steps; i++) {
      const v = lo + ((hi - lo) * i) / steps;
      const yy = y(v).toFixed(1);
      g += `<line x1="${L}" y1="${yy}" x2="${W - R}" y2="${yy}" stroke="#eef0f3"/>`;
      g += `<text x="${L - 6}" y="${Number(yy) + 6}" font-size="18" fill="#9aa1ab" text-anchor="end">${Math.round(v)}</text>`;
    }
    s.forEach((p, i) => {
      if (i % 6 === 0 || i === s.length - 1) {
        g += `<text x="${x(i).toFixed(1)}" y="${H - 12}" font-size="18" fill="#9aa1ab" text-anchor="middle">${fmtTime(p.ts, 'HH:mm')}</text>`;
      }
    });
    const lines = keys
      .map(({ k, c }) => {
        const pts = s.map((p, i) => `${x(i).toFixed(1)},${y(Number(p[k])).toFixed(1)}`).join(' ');
        return `<polyline points="${pts}" fill="none" stroke="${c}" stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round"/>`;
      })
      .join('');
    const mark = `<text x="${W - R}" y="${T - 4}" font-size="18" fill="#ff8f1f" text-anchor="end">演示数据</text>`;
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">${g}${lines}${mark}</svg>`;
    return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
  });

  function go(url, params) {
    sheep.$router.go(url, params);
  }
  function preview(urls, i) {
    uni.previewImage({ urls, current: i });
  }
  function play() {
    sheep.$helper.toast(camera.value?.tip || '演示环境为视频占位');
  }

  async function load() {
    if (!id.value || needLogin()) return;
    const [ad, rec, tr, ev, cam, ship] = await Promise.all([
      call(FarmApi.getAdoption(id.value)),
      call(FarmApi.getAdoptionRecords(id.value)),
      call(FarmApi.getAdoptionTrace(id.value)),
      call(FarmApi.getAdoptionEnv(id.value)),
      call(FarmApi.getAdoptionCamera(id.value)),
      call(FarmApi.getShipmentList(id.value)),
    ]);
    a.value = ad;
    records.value = rec || [];
    trace.value = tr;
    env.value = ev;
    camera.value = cam;
    shipments.value = ship || [];
  }

  function toggleTrack(sid) {
    openId.value = openId.value === sid ? 0 : sid;
  }
  // 确认收货：只有运输中的配送单能确认，成功后刷新状态
  function confirm(s) {
    uni.showModal({
      title: '确认收货',
      content: `配送单 ${s.no}（${s.weightJin} 斤）已收到了吗？确认后状态变为已签收。`,
      confirmText: '已收到',
      success: async (r) => {
        if (!r.confirm) return;
        const res = await callRaw(FarmApi.confirmShipment(s.id));
        if (res.code === 0) {
          sheep.$helper.toast('已确认收货');
        }
        await load();
      },
    });
  }
  function confirmLatest() {
    const list = shipmentRows.value.filter((s) => s.status === 10);
    if (!list.length) return sheep.$helper.toast('暂无待收货的配送单');
    if (list.length > 1) return sheep.$helper.toast('有多张运输中的配送单，请在对应批次里确认收货');
    confirm(list[0]);
  }
  function adjustDelivery() {
    uni.showModal({
      title: '调整配送',
      content: '配送方式调整、延后（免费保管 7 天）请联系专属管家，演示版暂不支持在线修改。',
      showCancel: false,
    });
  }

  let firstShow = true;
  onLoad((options) => {
    id.value = Number(options.id || 0);
    if (options.tab) tab.value = options.tab;
    load();
  });
  // 从自管任务等页面返回时刷新待办数
  onShow(() => {
    if (firstShow) {
      firstShow = false;
      return;
    }
    load();
  });
  watch(
    () => sheep.$store('user').isLogin,
    (v) => v && load(),
  );

  // 合同独立页按订单号取：认养详情没有 orderId，用订单列表按 adoptionId 反查
  async function toContract() {
    const list = (await call(FarmApi.getOrderList())) || [];
    const o = list.find((x) => x.adoptionId === a.value.id);
    if (!o) return sheep.$helper.toast('没找到这份认养对应的订单');
    sheep.$router.go('/pages/farm/contract/detail', { orderId: o.id });
  }
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .title {
    font-size: 32rpx;
    font-weight: 700;
  }
  .plot {
    margin: 12rpx 0 6rpx;
    font-size: 28rpx;
    .code {
      color: $fg;
      font-weight: 700;
      font-size: 34rpx;
    }
  }
  .head .f-sub {
    margin-top: 6rpx;
  }
  .bar-label {
    margin-bottom: 10rpx;
  }
  .no-mb {
    margin-bottom: 0;
  }
  .top {
    margin: 10rpx 0 0;
  }
  .sec-title {
    margin: 10rpx 0 14rpx;
  }
  .s-status {
    font-size: 28rpx;
    color: $fo;
    &.done {
      color: $fgd;
    }
  }
  .s-line {
    margin: 14rpx 0;
    font-size: 30rpx;
    font-weight: 600;
  }
  .foot {
    margin-top: 12rpx;
    padding-top: 14rpx;
    border-top: 2rpx solid $fline;
  }
  .tracks {
    margin-top: 16rpx;
  }
  .t-content {
    font-size: 26rpx;
  }
  .addr {
    margin-top: 10rpx;
  }
  .confirm-row {
    margin-top: 16rpx;
    text-align: right;
  }
  .f-btn.mini {
    height: 56rpx;
    line-height: 56rpx;
    padding: 0 32rpx;
    font-size: 24rpx;
  }
  .btn-row {
    display: flex;
    gap: 20rpx;
    margin-top: 20rpx;
    .f-btn {
      flex: 1;
    }
  }
  .promise-note {
    margin-top: 12rpx;
  }
  .slogan {
    color: $fgd;
    font-weight: 600;
    margin-bottom: 8rpx;
  }
  .entries {
    display: flex;
  }
  .entry {
    flex: 1;
    text-align: center;
    font-size: 24rpx;
    position: relative;
    .e-icon {
      width: 76rpx;
      height: 76rpx;
      line-height: 76rpx;
      border-radius: 50%;
      background: $fgl;
      color: $fgd;
      margin: 0 auto 10rpx;
      font-size: 30rpx;
      font-weight: 600;
    }
    .badge {
      position: absolute;
      top: -6rpx;
      right: 24rpx;
      background: $fr;
      color: #fff;
      font-size: 20rpx;
      min-width: 32rpx;
      height: 32rpx;
      line-height: 32rpx;
      border-radius: 16rpx;
    }
  }
  .r-title {
    font-size: 28rpx;
    font-weight: 600;
  }
  .r-content {
    font-size: 26rpx;
    color: #374151;
    margin: 8rpx 0;
    line-height: 1.6;
  }
  .photos {
    display: flex;
    flex-wrap: wrap;
    gap: 10rpx;
    margin-bottom: 8rpx;
  }
  .photo {
    width: 180rpx;
    height: 135rpx;
    border-radius: 10rpx;
  }
  .r-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 8rpx 0;
  }
  .input-row {
    padding: 16rpx 0;
    border-bottom: 2rpx solid $fline;
  }
  .input-row:last-child {
    border-bottom: none;
  }
  .env-grid {
    display: flex;
    flex-wrap: wrap;
    margin: 16rpx -8rpx 0;
  }
  .env-cell {
    width: calc(33.33% - 16rpx);
    margin: 8rpx;
    background: #fafbfc;
    border-radius: 14rpx;
    padding: 16rpx;
    box-sizing: border-box;
  }
  .env-val {
    font-size: 36rpx;
    font-weight: 700;
    color: $ftx;
  }
  .env-unit {
    font-size: 22rpx;
    color: $fsub;
    margin-left: 4rpx;
  }
  .chart-title {
    margin-top: 24rpx;
    font-size: 28rpx;
  }
  .chart {
    width: 100%;
  }
  .legend {
    display: flex;
    justify-content: center;
    gap: 24rpx;
    font-size: 22rpx;
    color: $fsub;
    .dot {
      display: inline-block;
      width: 16rpx;
      height: 16rpx;
      border-radius: 50%;
      margin-right: 6rpx;
    }
  }
  .video {
    position: relative;
    border-radius: 16rpx;
    overflow: hidden;
    height: 400rpx;
  }
  .poster {
    width: 100%;
    height: 400rpx;
  }
  .play {
    position: absolute;
    left: 50%;
    top: 50%;
    width: 110rpx;
    height: 110rpx;
    margin: -55rpx 0 0 -55rpx;
    border-radius: 50%;
    background: rgba(0, 0, 0, 0.45);
    .tri {
      position: absolute;
      left: 44rpx;
      top: 32rpx;
      border-left: 36rpx solid #fff;
      border-top: 23rpx solid transparent;
      border-bottom: 23rpx solid transparent;
    }
  }
  .cam-name {
    position: absolute;
    left: 16rpx;
    bottom: 14rpx;
    color: #fff;
    font-size: 24rpx;
    background: rgba(0, 0, 0, 0.4);
    padding: 4rpx 12rpx;
    border-radius: 8rpx;
  }
  .cam-tip {
    margin-top: 16rpx;
  }
</style>
