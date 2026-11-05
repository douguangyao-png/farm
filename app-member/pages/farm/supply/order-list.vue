<!-- 我的农资（farmer-saas 新增）：农资订单 + 溯源登记状态 -->
<template>
  <s-layout title="我的农资">
    <demo-badge />
    <view class="f-page">
      <view class="f-card item" v-for="o in list" :key="o.id">
        <view class="f-between">
          <text class="f-sub">订单号 {{ o.no }}</text>
          <text class="f-tag" :class="o.traceState === 10 ? '' : 'orange'">{{ o.traceState === 10 ? '已写入溯源' : '待登记' }}</text>
        </view>
        <view class="f-row body">
          <image class="img" :src="o.cover" mode="aspectFill" />
          <view class="info">
            <view class="name">{{ o.spuName }}</view>
            <view class="f-sub">{{ o.qty }} {{ o.unit }} · 批次 {{ o.batchNo }}</view>
            <view class="f-sub">用于地块 <text class="code">{{ o.assetCode }}</text> · {{ DELIVER_WAY_NAME[o.deliverWay] || o.deliverWay }}</view>
          </view>
          <text class="f-price">{{ yuan(o.amount) }}</text>
        </view>
        <view class="f-between f-sub foot">
          <text>{{ fmtTime(o.createTime) }}</text>
          <text v-if="o.traceState === 10 && o.usedAt">{{ fmtTime(o.usedAt, 'MM-DD') }} 已用 {{ o.dosage }}</text>
          <text v-else-if="o.traceState !== 10">使用后由管家登记用量，写入溯源档案</text>
        </view>
      </view>
      <view v-if="loaded && !list.length" class="f-empty">
        <view class="f-empty-title">还没有买过农资</view>
        <button class="f-btn" @tap="toMall">去农资商城</button>
      </view>
    </view>
  </s-layout>
</template>

<script setup>
  import { ref, watch } from 'vue';
  import { onShow } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { DELIVER_WAY_NAME, yuan, fmtTime, call, needLogin } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const list = ref([]);
  const loaded = ref(false);

  async function load() {
    if (needLogin()) return;
    list.value = (await call(FarmApi.getSupplyOrderList())) || [];
    loaded.value = true;
  }
  function toMall() {
    sheep.$router.go('/pages/farm/supply/list');
  }

  onShow(load);
  watch(
    () => sheep.$store('user').isLogin,
    (v) => v && load(),
  );
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .body {
    margin: 16rpx 0;
  }
  .img {
    width: 120rpx;
    height: 120rpx;
    border-radius: 12rpx;
    margin-right: 16rpx;
    flex-shrink: 0;
  }
  .info {
    flex: 1;
    min-width: 0;
  }
  .name {
    font-size: 28rpx;
    font-weight: 600;
  }
  .code {
    color: $fg;
    font-weight: 600;
  }
  .foot {
    border-top: 2rpx solid $fline;
    padding-top: 14rpx;
  }
</style>
