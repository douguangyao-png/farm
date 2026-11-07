<!-- 我的订单（farmer-saas 新增，原型 07 orderList）：页签 全部 / 待付款 / 认养中 / 已完成 / 退款 -->
<template>
  <s-layout title="我的订单">
    <demo-badge />
    <view class="f-page">
      <view class="f-tabs">
        <view v-for="t in TABS" :key="t.key" class="f-tab" :class="{ active: tab === t.key }" @tap="tab = t.key">{{ t.label }}</view>
      </view>
      <view class="f-card" v-for="o in shown" :key="o.id" @tap="toDetail(o)">
        <view class="f-between">
          <text class="no">订单号 {{ o.no }}</text>
          <text class="f-tag" :class="statusClass(o.status)">{{ ORDER_STATUS[o.status] }}</text>
        </view>
        <view class="f-row goods">
          <image class="cover" :src="o.productCover" mode="aspectFill" />
          <view class="body">
            <view class="g-title">{{ o.productTitle }}</view>
            <view class="f-sub">{{ o.careModeName }}<text v-if="o.assetCode"> · {{ o.assetCode }}</text></view>
            <view class="f-sub" v-if="o.startAt">{{ fmtTime(o.startAt, 'YYYY-MM-DD') }} ~ {{ fmtTime(o.endAt, 'YYYY-MM-DD') }}</view>
            <view class="f-sub" v-else>下单时间 {{ fmtTime(o.createTime) }}</view>
          </view>
          <view class="f-price">{{ yuan(o.amount) }}</view>
        </view>
        <view class="f-between foot">
          <text class="f-sub" v-if="o.adoptionId">已配送 {{ o.shipmentCount || 0 }} 次<text v-if="o.promisedJin"> · 已收 {{ o.harvestedJin || 0 }} / {{ o.promisedJin }} 斤</text></text>
          <text class="f-sub" v-else>未支付，支付后自动分配地块</text>
          <view class="btns">
            <template v-if="o.status === 0">
              <button class="f-btn mini" @tap.stop="pay(o)">去支付</button>
            </template>
            <template v-else-if="o.adoptionId">
              <button class="f-btn mini ghost" @tap.stop="toLogistics(o)">查看物流</button>
              <button v-if="o.status === 20" class="f-btn mini" @tap.stop="toReview">去评价</button>
              <button v-else class="f-btn mini" @tap.stop="toDetail(o)">看长势</button>
            </template>
          </view>
        </view>
      </view>
      <view v-if="loaded && !shown.length" class="f-empty">
        <view class="f-empty-title">{{ tab === 'refund' ? '暂无退款订单' : '暂无订单' }}</view>
        <button class="f-btn" v-if="tab !== 'refund'" @tap="toHome">去认养一块地</button>
      </view>
    </view>
  </s-layout>
</template>

<script setup>
  import { computed, ref, watch } from 'vue';
  import { onShow, onPullDownRefresh } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { ORDER_STATUS, yuan, fmtTime, call, needLogin } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  // 退款闭环不在第一期范围，「退款」页签保留（和原型一致），目前没有退款状态的订单
  const TABS = [
    { key: 'all', label: '全部' },
    { key: 'pay', label: '待付款' },
    { key: 'doing', label: '认养中' },
    { key: 'done', label: '已完成' },
    { key: 'refund', label: '退款' },
  ];
  const STATUS_OF = { pay: 0, doing: 10, done: 20 };

  const tab = ref('all');
  const list = ref([]);
  const loaded = ref(false);

  const shown = computed(() => {
    if (tab.value === 'all') return list.value;
    if (tab.value === 'refund') return [];
    return list.value.filter((o) => o.status === STATUS_OF[tab.value]);
  });

  const statusClass = (s) => (s === 10 ? '' : s === 0 ? 'orange' : 'gray');

  async function load() {
    if (needLogin()) return;
    list.value = (await call(FarmApi.getOrderList())) || [];
    loaded.value = true;
  }

  function toDetail(o) {
    if (o.adoptionId) sheep.$router.go('/pages/farm/adoption/detail', { id: o.adoptionId });
  }
  function toLogistics(o) {
    sheep.$router.go('/pages/farm/shipment/list', { adoptionId: o.adoptionId });
  }
  function toReview() {
    sheep.$helper.toast('评价功能暂未开放');
  }
  function toHome() {
    sheep.$router.go('/pages/farm/index');
  }
  function pay(o) {
    uni.showModal({
      title: '支付认养费用',
      content: `订单 ${o.no}，应付 ${yuan(o.amount)}（演示支付，不扣款）`,
      confirmText: '确认支付',
      success: async (r) => {
        if (!r.confirm) return;
        const data = await call(FarmApi.payOrder(o.id));
        if (!data) return;
        sheep.$router.redirect('/pages/farm/order/result', {
          orderId: data.orderId,
          adoptionId: data.adoptionId,
          assetCode: data.assetCode || '',
        });
      },
    });
  }

  onShow(load);
  onPullDownRefresh(async () => {
    await load();
    uni.stopPullDownRefresh();
  });
  watch(
    () => sheep.$store('user').isLogin,
    (v) => v && load(),
  );
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .no {
    font-size: 24rpx;
    color: $fsub;
  }
  .goods {
    margin-top: 18rpx;
    align-items: flex-start;
  }
  .cover {
    width: 120rpx;
    height: 120rpx;
    border-radius: 12rpx;
    margin-right: 20rpx;
    flex-shrink: 0;
  }
  .body {
    flex: 1;
    .f-sub {
      margin-top: 6rpx;
    }
  }
  .g-title {
    font-size: 30rpx;
    font-weight: 600;
  }
  .foot {
    margin-top: 20rpx;
    padding-top: 18rpx;
    border-top: 2rpx solid $fline;
  }
  .btns {
    display: flex;
    gap: 14rpx;
  }
  .f-btn.mini {
    margin: 0;
    padding: 0 28rpx;
    height: 56rpx;
    line-height: 56rpx;
    font-size: 24rpx;
  }
</style>
