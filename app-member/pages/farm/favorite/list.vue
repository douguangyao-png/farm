<!-- 我的收藏（原型 07 favorites）：认养商品 / 基地 -->
<template>
  <s-layout title="我的收藏">
    <demo-badge />
    <view class="f-page">
      <view class="f-tabs">
        <view class="f-tab" :class="{ active: tab === 'product' }" @tap="pick('product')">认养商品</view>
        <view class="f-tab" :class="{ active: tab === 'base' }" @tap="pick('base')">基地</view>
      </view>
      <template v-if="tab === 'product'">
        <view v-for="f in list" :key="f.id">
          <product-card :p="f.product" />
          <view class="un f-sub" @tap="remove(f)">取消收藏</view>
        </view>
      </template>
      <template v-else>
        <view class="f-card base" v-for="f in list" :key="f.id" @tap="openBase(f)">
          <image class="b-cover" :src="f.base.cover" mode="aspectFill" />
          <view class="b-body">
            <view class="b-name">{{ f.base.name }}</view>
            <view class="f-sub">{{ f.base.address }}</view>
            <text class="f-sub un2" @tap.stop="remove(f)">取消收藏</text>
          </view>
        </view>
      </template>
      <view v-if="loaded && !list.length" class="f-empty">
        <view class="f-empty-title">还没有收藏</view>
        <view>在商品页或基地页点「收藏」，会出现在这里</view>
      </view>
    </view>
  </s-layout>
</template>

<script setup>
  import { ref, watch } from 'vue';
  import { onShow } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { call, needLogin } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';
  import ProductCard from '@/pages/farm/components/product-card.vue';

  const tab = ref('product');
  const list = ref([]);
  const loaded = ref(false);

  async function load() {
    if (needLogin()) return;
    list.value = (await call(FarmApi.getFavoriteList(tab.value))) || [];
    loaded.value = true;
  }
  function pick(k) {
    tab.value = k;
    load();
  }
  function openBase(f) {
    sheep.$router.go('/pages/farm/base/detail', { id: f.base.id });
  }
  async function remove(f) {
    await call(FarmApi.toggleFavorite(f.type, f.targetId));
    load();
  }
  onShow(load);
  watch(
    () => sheep.$store('user').isLogin,
    (v) => v && load(),
  );
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .un {
    text-align: right;
    margin: -10rpx 8rpx 20rpx;
  }
  .base {
    display: flex;
    padding: 16rpx;
  }
  .b-cover {
    width: 160rpx;
    height: 160rpx;
    border-radius: 14rpx;
    margin-right: 20rpx;
  }
  .b-body {
    flex: 1;
  }
  .b-name {
    font-size: 30rpx;
    font-weight: 600;
  }
  .un2 {
    display: block;
    margin-top: 20rpx;
  }
</style>
