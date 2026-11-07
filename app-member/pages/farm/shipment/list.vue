<!-- 收获配送（farmer-saas 新增）：配送批次 + 物流轨迹 -->
<template>
  <s-layout title="收获配送">
    <demo-badge />
    <view class="f-page">
      <view class="f-card" v-for="s in list" :key="s.id">
        <view class="f-between">
          <text class="no">配送单 {{ s.no }}</text>
          <text class="f-tag" :class="s.status === 20 ? '' : s.status === 10 ? 'orange' : 'gray'">{{ SHIPMENT_STATUS[s.status] }}</text>
        </view>
        <view class="weight">
          <text class="w">{{ s.weightJin }}</text> 斤
          <text class="f-tag" v-if="s.coldChain">冷链</text>
          <text class="f-sub">地块 {{ s.assetCode }}</text>
        </view>
        <view class="f-kv"><text class="k">物流</text><text class="v">{{ s.carrier || '—' }} {{ s.logisticsNo }}</text></view>
        <view class="f-kv"><text class="k">收货人</text><text class="v">{{ s.receiverName }} {{ s.receiverMobile }}</text></view>
        <view class="f-kv"><text class="k">地址</text><text class="v">{{ s.address }}</text></view>
        <view class="f-more link" @tap="sheep.$router.go('/pages/farm/logistics/detail', { id: s.id })">物流详情（本批清单、复制单号）›</view>
        <view class="f-timeline tracks" v-if="s.tracks?.length">
          <view class="f-tl-item" :class="{ gray: i > 0 }" v-for="(t, i) in [...s.tracks].reverse()" :key="i">
            <view class="t-content">{{ t.content }}</view>
            <view class="f-sub">{{ fmtTime(t.time) }}</view>
          </view>
        </view>
      </view>
      <view v-if="loaded && !list.length" class="f-empty">
        <view class="f-empty-title">还没有配送记录</view>
        <view>采收后农场会安排配送，物流信息会显示在这里</view>
      </view>
    </view>
  </s-layout>
</template>

<script setup>
  import { ref, watch } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { SHIPMENT_STATUS, fmtTime, call, needLogin } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const adoptionId = ref(0);
  const list = ref([]);
  const loaded = ref(false);

  async function load() {
    if (needLogin()) return;
    list.value = (await call(FarmApi.getShipmentList(adoptionId.value || undefined))) || [];
    loaded.value = true;
  }

  onLoad((options) => {
    adoptionId.value = Number(options.adoptionId || 0);
    load();
  });
  watch(
    () => sheep.$store('user').isLogin,
    (v) => v && load(),
  );
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .no {
    font-size: 26rpx;
    color: $fsub;
  }
  .weight {
    margin: 12rpx 0;
    display: flex;
    align-items: baseline;
    gap: 12rpx;
    .w {
      font-size: 48rpx;
      font-weight: 700;
      color: $fgd;
    }
  }
  .link {
    text-align: right;
    padding: 8rpx 0;
  }
  .tracks {
    margin-top: 16rpx;
    border-top: 2rpx solid $fline;
    padding-top: 20rpx;
  }
  .t-content {
    font-size: 26rpx;
  }
</style>
