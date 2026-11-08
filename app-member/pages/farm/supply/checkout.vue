<!-- 农资结算页（第一期补齐 §9）：展示商品、数量、金额、收货地址，提交走 POST /app-api/farm/supply-order/create -->
<template>
  <s-layout title="农资结算">
    <demo-badge />
    <view class="f-page" v-if="s">
      <view class="f-card goods">
        <image class="goods-img" :src="s.cover" mode="aspectFill" />
        <view class="goods-body">
          <view class="goods-title">{{ s.name }}</view>
          <view class="f-sub">{{ s.spec }}</view>
          <view class="f-between">
            <text class="f-price">{{ yuan(s.price) }}<text class="f-sub">/{{ s.unit }}</text></text>
            <text class="f-sub">x {{ qty }}</text>
          </view>
        </view>
      </view>

      <view class="f-card">
        <view class="f-kv" v-if="adoption"><text class="k">用于地块</text><text class="v">{{ adoption.assetCode }} · {{ adoption.productTitle }}</text></view>
        <view class="f-kv"><text class="k">交付方式</text><text class="v">{{ DELIVER_WAY_NAME[deliverWay] || deliverWay }}</text></view>
        <view class="f-kv"><text class="k">商品金额</text><text class="v">{{ yuan(s.price * qty) }}</text></view>
        <view class="f-between pay-line">
          <text>应付金额</text>
          <text class="f-price total">{{ yuan(s.price * qty) }}</text>
        </view>
      </view>

      <view class="f-card" v-if="deliverWay === 'express'">
        <view class="f-title">收货地址<text class="f-more" @tap="pickAddress">{{ addr ? '更换 ›' : '选择地址 ›' }}</text></view>
        <view v-if="addr">
          <view class="nm">{{ addr.name }} {{ addr.mobile }}</view>
          <view class="f-sub">{{ addr.detail }}</view>
        </view>
        <view v-else class="f-sub">还没有收货地址，点右上「选择地址」</view>
        <view class="f-sub note">演示口径：农资订单暂不保存收货地址，寄送由管家按账户地址联系确认。</view>
      </view>

      <view class="f-warn" v-if="s.needWorkorder">下单后自动生成代管工单，由管家到地块完成。</view>
      <view class="f-bottom-space" />
    </view>
    <view class="f-page" v-else-if="loaded"><view class="f-empty"><view class="f-empty-title">商品不存在或已下架</view></view></view>

    <view class="f-bottom-bar" v-if="s">
      <view>
        <text class="f-sub">合计 </text>
        <text class="f-price total">{{ yuan(s.price * qty) }}</text>
      </view>
      <button class="f-btn" :class="{ disabled: busy }" @tap="submit">提交订单</button>
    </view>
  </s-layout>
</template>

<script setup>
  import { ref } from 'vue';
  import { onLoad, onShow } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { DELIVER_WAY_NAME, yuan, call, needLogin } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const s = ref(null);
  const loaded = ref(false);
  const qty = ref(1);
  const deliverWay = ref('store');
  const adoptionId = ref(0);
  const adoption = ref(null);
  const addr = ref(null);
  const busy = ref(false);

  function pickAddress() {
    sheep.$router.go('/pages/farm/address/list', { select: 1 });
  }
  async function loadDefaultAddress() {
    const list = (await call(FarmApi.getAddressList())) || [];
    addr.value = list.find((a) => a.isDefault) || list[0] || null;
  }
  // 地址页选择模式返回后取走所选地址
  onShow(() => {
    const a = uni.getStorageSync('farm_picked_address');
    if (!a) return;
    uni.removeStorageSync('farm_picked_address');
    addr.value = a;
  });

  async function submit() {
    if (busy.value || needLogin()) return;
    if (deliverWay.value === 'express' && !addr.value) return sheep.$helper.toast('请先选择收货地址');
    busy.value = true;
    const data = await call(
      FarmApi.createSupplyOrder({ spuId: s.value.id, qty: qty.value, adoptionId: adoptionId.value, deliverWay: deliverWay.value }),
    );
    busy.value = false;
    if (!data) return;
    uni.showModal({
      title: '下单成功',
      content: `订单号 ${data.no}，金额 ${yuan(data.amount)}（演示支付，不扣款）。`,
      showCancel: false,
      confirmText: '查看我的农资',
      success: () => sheep.$router.redirect('/pages/farm/supply/order-list'),
    });
  }

  onLoad(async (opt) => {
    qty.value = Math.max(1, Number(opt.qty || 1));
    deliverWay.value = opt.deliverWay || 'store';
    adoptionId.value = Number(opt.adoptionId || 0);
    s.value = await call(FarmApi.getSupply(Number(opt.id)));
    loaded.value = true;
    if (!s.value) return;
    if (s.value.stock !== undefined && qty.value > s.value.stock) qty.value = Math.max(1, s.value.stock);
    if (!sheep.$store('user').isLogin) return;
    const ads = (await call(FarmApi.getAdoptionList())) || [];
    adoption.value = ads.find((a) => a.id === adoptionId.value) || null;
    loadDefaultAddress();
  });
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .goods {
    display: flex;
  }
  .goods-img {
    width: 160rpx;
    height: 160rpx;
    border-radius: 14rpx;
    margin-right: 20rpx;
    flex-shrink: 0;
  }
  .goods-body {
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
  }
  .goods-title {
    font-size: 30rpx;
    font-weight: 600;
  }
  .nm {
    font-size: 28rpx;
    font-weight: 600;
    margin-bottom: 6rpx;
  }
  .note {
    margin-top: 12rpx;
  }
  .total {
    font-size: 40rpx;
  }
  .pay-line {
    margin-top: 12rpx;
    padding-top: 16rpx;
    border-top: 2rpx solid $fline;
    font-size: 28rpx;
  }
</style>
