<!-- 支付结果（farmer-saas 新增）：展示分配的地块和合同 -->
<template>
  <s-layout title="支付结果">
    <demo-badge />
    <view class="f-page">
      <view class="f-card result">
        <view class="icon">✓</view>
        <view class="r-title">认养成功</view>
        <view class="f-sub">演示支付，未实际扣款</view>
        <view class="plot" v-if="assetCode">
          已为您分配地块 <text class="code">{{ assetCode }}</text>
        </view>
      </view>

      <view class="f-card" v-if="o">
        <view class="f-kv"><text class="k">认养商品</text><text class="v">{{ o.productTitle }}</text></view>
        <view class="f-kv"><text class="k">托管方式</text><text class="v">{{ o.careModeName }}</text></view>
        <view class="f-kv"><text class="k">支付金额</text><text class="v f-price">{{ yuan(o.amount) }}</text></view>
        <view class="f-kv"><text class="k">订单号</text><text class="v">{{ o.no }}</text></view>
        <view class="f-kv"><text class="k">支付时间</text><text class="v">{{ fmtTime(o.payTime) }}</text></view>
        <view class="f-kv" v-if="o.contract"><text class="k">合同</text><text class="v">{{ o.contract.title }}</text></view>
        <view class="f-kv" v-if="o.contract"><text class="k">合同编号</text><text class="v">{{ o.contract.no }}</text></view>
      </view>

      <button class="f-btn block" @tap="toAdoption">查看我的认养</button>
      <view class="gap" />
      <button class="f-btn ghost block" @tap="toHome">返回首页</button>
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
  .gap {
    height: 20rpx;
  }
</style>
