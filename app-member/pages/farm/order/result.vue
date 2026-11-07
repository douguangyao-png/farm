<!-- 支付结果（farmer-saas 新增）：认养编号、地块、服务周期、管家、合同 / 保险 / 溯源码状态、快捷入口（原型 07 payResult） -->
<template>
  <s-layout title="支付结果">
    <demo-badge />
    <view class="f-page">
      <view class="f-card result">
        <view class="icon">✓</view>
        <view class="r-title">认养成功</view>
        <view class="amount" v-if="o">{{ yuan(o.amount) }}</view>
        <view class="f-sub">{{ o?.payMethod || '微信支付' }} · 演示支付，未实际扣款</view>
        <view class="plot" v-if="assetCode">
          已为您分配地块 <text class="code">{{ assetCode }}</text>
        </view>
      </view>

      <view class="f-card" v-if="o">
        <view class="f-kv" v-if="o.adoptionNo"><text class="k">认养编号</text><text class="v strong">{{ o.adoptionNo }}</text></view>
        <view class="f-kv"><text class="k">认养标的</text><text class="v">{{ o.productTitle }}</text></view>
        <view class="f-kv" v-if="o.assetCode">
          <text class="k">地块编号</text>
          <text class="v">{{ o.assetCode }}<text v-if="o.areaM2">（{{ o.areaM2 }}㎡）</text></text>
        </view>
        <view class="f-kv" v-if="o.startAt"><text class="k">服务周期</text><text class="v">{{ fmtTime(o.startAt, 'YYYY-MM-DD') }} 至 {{ fmtTime(o.endAt, 'YYYY-MM-DD') }}</text></view>
        <view class="f-kv" v-if="o.stewardName"><text class="k">专属管家</text><text class="v">{{ o.stewardName }} · {{ o.stewardMobile }}</text></view>
        <view class="f-kv"><text class="k">托管方式</text><text class="v">{{ o.careModeName }}</text></view>
        <view class="f-kv"><text class="k">订单号</text><text class="v">{{ o.no }}</text></view>
        <view class="f-kv"><text class="k">支付时间</text><text class="v">{{ fmtTime(o.payTime) }}</text></view>
        <view class="f-kv" v-if="o.contract"><text class="k">合同</text><text class="v">{{ o.contract.title }}</text></view>
        <view class="f-kv" v-if="o.contract"><text class="k">合同编号</text><text class="v">{{ o.contract.no }}</text></view>

        <view class="status" @tap="go('/pages/farm/contract/list')">
          <view class="s-left"><text class="s-icon">合</text>电子合同已归档</view>
          <text class="f-tag">CA 存证</text>
        </view>
        <view class="status">
          <view class="s-left"><text class="s-icon o">险</text>农业保险已投保</view>
          <text class="f-tag orange">{{ o.insuranceNo }}</text>
        </view>
        <view class="status" @tap="toTrace">
          <view class="s-left"><text class="s-icon">码</text>溯源码已生成</view>
          <text class="f-tag">{{ o.traceCode }} ›</text>
        </view>
        <view class="f-ok note">款项由经营者收取并按合同约定结算，平台不设资金池；后续每一笔农事、每一次配送都会推送给您。</view>
      </view>

      <view class="btn-row">
        <button class="f-btn ghost" @tap="toHome">返回首页</button>
        <button class="f-btn" @tap="toAdoption">查看我的认养</button>
      </view>

      <view class="f-title can">认养后你可以</view>
      <view class="f-card quick">
        <view class="q-item" v-for="x in QUICK" :key="x.label" @tap="quick(x)">
          <view class="q-icon">{{ x.icon }}</view>
          <view class="q-label">{{ x.label }}</view>
        </view>
      </view>
    </view>
  </s-layout>
</template>

<script setup>
  import { ref } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { yuan, fmtTime, call } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const o = ref(null);
  const assetCode = ref('');
  const adoptionId = ref(0);

  // 认养后你可以（原型 07 payResult）：看监控、生长档案、预约配送、预约到访
  const QUICK = [
    { icon: '监', label: '看监控', tab: 'camera' },
    { icon: '档', label: '生长档案', tab: 'records' },
    { icon: '车', label: '预约配送', tab: 'harvest' },
    { icon: '访', label: '预约到访', tab: '' },
  ];

  function go(url) {
    sheep.$router.go(url);
  }
  function toTab(tab) {
    if (adoptionId.value) sheep.$router.redirect('/pages/farm/adoption/detail', { id: adoptionId.value, tab });
    else sheep.$router.go('/pages/farm/adoption/list');
  }
  function toTrace() {
    toTab('trace');
  }
  function quick(x) {
    if (!x.tab) return sheep.$helper.toast('预约到访功能暂未开放');
    toTab(x.tab);
  }

  function toAdoption() {
    if (adoptionId.value) sheep.$router.redirect('/pages/farm/adoption/detail', { id: adoptionId.value });
    else sheep.$router.go('/pages/farm/adoption/list');
  }
  function toHome() {
    sheep.$router.go('/pages/farm/index');
  }

  onLoad(async (options) => {
    assetCode.value = options.assetCode || '';
    adoptionId.value = Number(options.adoptionId || 0);
    if (options.orderId) {
      o.value = await call(FarmApi.getOrder(Number(options.orderId)));
      if (o.value?.assetCode) assetCode.value = o.value.assetCode;
      if (o.value?.adoptionId) adoptionId.value = o.value.adoptionId;
    }
  });
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .result {
    text-align: center;
    padding: 50rpx 24rpx;
  }
  .icon {
    width: 110rpx;
    height: 110rpx;
    line-height: 110rpx;
    border-radius: 50%;
    background: $fg;
    color: #fff;
    font-size: 60rpx;
    margin: 0 auto 20rpx;
  }
  .r-title {
    font-size: 38rpx;
    font-weight: 700;
    margin-bottom: 8rpx;
  }
  .plot {
    margin-top: 24rpx;
    font-size: 30rpx;
    .code {
      color: $fg;
      font-size: 44rpx;
      font-weight: 700;
      margin-left: 8rpx;
    }
  }
  .amount {
    font-size: 52rpx;
    font-weight: 700;
    color: $ftx;
    margin-bottom: 6rpx;
  }
  .strong {
    font-weight: 700;
  }
  .status {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 20rpx 0;
    border-top: 2rpx solid $fline;
    font-size: 28rpx;
    .s-left {
      display: flex;
      align-items: center;
    }
    .s-icon {
      width: 44rpx;
      height: 44rpx;
      line-height: 44rpx;
      text-align: center;
      border-radius: 10rpx;
      background: $fgl;
      color: $fgd;
      font-size: 24rpx;
      margin-right: 14rpx;
      &.o {
        background: #fff3e3;
        color: $fo;
      }
    }
  }
  .note {
    margin: 10rpx 0 0;
  }
  .btn-row {
    display: flex;
    gap: 20rpx;
    .f-btn {
      flex: 1;
    }
  }
  .can {
    margin: 30rpx 0 14rpx;
    display: block;
  }
  .quick {
    display: flex;
    padding: 28rpx 6rpx;
  }
  .q-item {
    flex: 1;
    text-align: center;
  }
  .q-icon {
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
  .q-label {
    font-size: 24rpx;
  }
</style>
