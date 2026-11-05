<!-- 托管方式对比（farmer-saas 新增）：compareRows 13 项 + 价格、产出、保险、合同 -->
<template>
  <s-layout title="托管方式对比">
    <demo-badge />
    <view class="f-page" v-if="p">
      <view class="f-card">
        <view class="f-title">{{ p.title }}</view>
        <view class="table">
          <view class="tr th">
            <view class="td item">服务项</view>
            <view class="td" v-for="m in modes" :key="m.mode">{{ CARE_MODE_NAME[m.mode] }}</view>
          </view>
          <view class="tr">
            <view class="td item">价格</view>
            <view class="td price" v-for="m in modes" :key="m.mode">{{ m.enabled ? yuan(m.price) : '未开放' }}</view>
          </view>
          <view class="tr">
            <view class="td item">每周待办</view>
            <view class="td" v-for="m in modes" :key="m.mode">{{ m.weeklyTodo }} 项</view>
          </view>
          <view class="tr" v-for="r in p.compareRows" :key="r.item">
            <view class="td item">{{ r.item }}</view>
            <view class="td" :class="{ me: r.full === '您来做' }">{{ r.full }}</view>
            <view class="td" :class="{ me: r.semi === '您来做' }">{{ r.semi }}</view>
            <view class="td" :class="{ me: r.self === '您来做' }">{{ r.self }}</view>
          </view>
          <view class="tr">
            <view class="td item">产出说明</view>
            <view class="td small" v-for="m in modes" :key="m.mode">{{ m.outputPromise }}</view>
          </view>
          <view class="tr">
            <view class="td item">保险</view>
            <view class="td small" v-for="m in modes" :key="m.mode">{{ m.insurance }}</view>
          </view>
          <view class="tr">
            <view class="td item">合同版本</view>
            <view class="td small" v-for="m in modes" :key="m.mode">{{ m.contractVersion }}</view>
          </view>
        </view>
        <view class="f-sub note">橙色是需要您自己动手的农活；不想做的可以在自管任务里委托管家代办（按次收费）。</view>
      </view>
      <button class="f-btn block" @tap="back">返回选择托管方式</button>
    </view>
  </s-layout>
</template>

<script setup>
  import { computed, ref } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { CARE_MODE_NAME, yuan, call } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const p = ref(null);
  const ORDER = ['full', 'semi', 'self'];
  const modes = computed(() =>
    ORDER.map((k) => p.value?.careModes.find((m) => m.mode === k)).filter(Boolean),
  );

  function back() {
    if (sheep.$router.hasHistory()) sheep.$router.back();
    else sheep.$router.go('/pages/farm/product/detail', { id: p.value.id });
  }

  onLoad(async (options) => {
    p.value = await call(FarmApi.getProduct(Number(options.id || 1)));
  });
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .table {
    border: 2rpx solid $fline;
    border-radius: 14rpx;
    overflow: hidden;
  }
  .tr {
    display: flex;
    border-bottom: 2rpx solid $fline;
  }
  .tr:last-child {
    border-bottom: none;
  }
  .th {
    background: $fgl;
    font-weight: 600;
    color: $fgd;
  }
  .td {
    flex: 0.62;
    text-align: center;
    padding: 16rpx 6rpx;
    font-size: 24rpx;
  }
  .td.item {
    flex: 1;
    text-align: left;
    padding-left: 16rpx;
    color: $ftx;
  }
  .td.me {
    color: $fo;
    font-weight: 600;
  }
  .td.price {
    color: $fo;
    font-weight: 700;
  }
  .td.small {
    font-size: 20rpx;
    color: $fsub;
    text-align: left;
  }
  .note {
    margin-top: 16rpx;
  }
</style>
