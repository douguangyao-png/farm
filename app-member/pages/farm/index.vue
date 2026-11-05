<!-- 认养农场首页（farmer-saas 新增）：轮播、基地、认养商品、公告 -->
<template>
  <s-layout :title="home.tenantName || '认养农场'" tabbar="/pages/farm/index" navbarBackgroundColor="#22a06b" color="#ffffff">
    <demo-badge />
    <view class="f-page">
      <!-- 轮播 -->
      <swiper
        v-if="home.banners.length"
        class="banner"
        circular
        autoplay
        indicator-dots
        indicator-active-color="#ffffff"
        :interval="4000"
      >
        <swiper-item v-for="(b, i) in home.banners" :key="i" @tap="go(b.url)">
          <image class="banner-img" :src="b.image" mode="aspectFill" />
          <view class="banner-title">{{ b.title }}</view>
        </swiper-item>
      </swiper>

      <!-- 基地 -->
      <view class="f-card" v-if="home.bases.length">
        <view class="f-title">我们的基地</view>
        <scroll-view scroll-x class="base-scroll">
          <view class="base-item" v-for="b in home.bases" :key="b.id">
            <image class="base-img" :src="b.cover" mode="aspectFill" />
            <view v-if="b.live" class="live-tag"><text class="dot" />直播中</view>
            <view class="base-name">{{ b.name }}</view>
            <view class="f-sub base-addr">{{ b.address }}</view>
          </view>
        </scroll-view>
      </view>

      <!-- 认养商品 -->
      <view class="f-title section-title">认养一块地 · 一只鸡</view>
      <view class="f-card product" v-for="p in home.products" :key="p.id" @tap="go('/pages/farm/product/detail?id=' + p.id)">
        <image class="product-img" :src="p.cover" mode="aspectFill" />
        <view class="product-body">
          <view class="product-title">{{ p.title }}</view>
          <view class="f-sub">{{ p.baseName }} · 周期 {{ p.cycleDays }} 天<text v-if="p.areaM2"> · {{ p.areaM2 }}㎡</text></view>
          <view class="tags">
            <text class="f-tag" v-for="m in p.enabledModes" :key="m">{{ CARE_MODE_NAME[m] }}</text>
          </view>
          <view class="f-between">
            <view><text class="f-price price">{{ yuan(p.minPrice) }}</text><text class="f-sub"> 起</text></view>
            <text class="f-sub">已认养 {{ p.sold }} · 剩 {{ p.stock }}</text>
          </view>
        </view>
      </view>
      <view v-if="loaded && !home.products.length" class="f-empty">暂时没有可认养的商品</view>

      <!-- 公告 -->
      <view class="f-card" v-if="home.notices.length">
        <view class="f-title">农场公告</view>
        <view class="notice" v-for="n in home.notices" :key="n.id">
          <text class="f-tag orange">公告</text>
          <text class="notice-text">{{ n.title }}</text>
        </view>
      </view>
    </view>
  </s-layout>
</template>

<script setup>
  import { reactive, ref } from 'vue';
  import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { CARE_MODE_NAME, yuan, call } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  uni.hideTabBar({ fail: () => {} });

  const home = reactive({ tenantName: '', banners: [], bases: [], products: [], notices: [] });
  const loaded = ref(false);

  async function load() {
    const data = await call(FarmApi.getHome());
    loaded.value = true;
    if (!data) return;
    Object.assign(home, {
      tenantName: data.tenantName || '',
      banners: data.banners || [],
      bases: data.bases || [],
      products: data.products || [],
      notices: data.notices || [],
    });
  }

  function go(url) {
    if (url) sheep.$router.go(url);
  }

  onLoad(load);
  onPullDownRefresh(async () => {
    await load();
    uni.stopPullDownRefresh();
  });
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .banner {
    height: 300rpx;
    border-radius: 20rpx;
    overflow: hidden;
    margin-bottom: 20rpx;
  }
  .banner-img {
    width: 100%;
    height: 300rpx;
  }
  .banner-title {
    position: absolute;
    left: 24rpx;
    bottom: 40rpx;
    color: #fff;
    font-size: 28rpx;
    text-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.3);
  }
  .base-scroll {
    white-space: nowrap;
  }
  .base-item {
    display: inline-block;
    width: 300rpx;
    margin-right: 20rpx;
    position: relative;
    vertical-align: top;
    white-space: normal;
  }
  .base-img {
    width: 300rpx;
    height: 180rpx;
    border-radius: 14rpx;
  }
  .live-tag {
    position: absolute;
    left: 12rpx;
    top: 12rpx;
    background: rgba(229, 72, 77, 0.92);
    color: #fff;
    font-size: 20rpx;
    padding: 4rpx 12rpx;
    border-radius: 20rpx;
    display: flex;
    align-items: center;
    .dot {
      width: 10rpx;
      height: 10rpx;
      border-radius: 50%;
      background: #fff;
      margin-right: 8rpx;
    }
  }
  .base-name {
    font-size: 26rpx;
    font-weight: 600;
    margin-top: 10rpx;
  }
  .base-addr {
    font-size: 22rpx;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .section-title {
    margin: 8rpx 4rpx 16rpx;
  }
  .product {
    display: flex;
    padding: 20rpx;
  }
  .product-img {
    width: 220rpx;
    height: 220rpx;
    border-radius: 14rpx;
    flex-shrink: 0;
    margin-right: 20rpx;
  }
  .product-body {
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
  }
  .product-title {
    font-size: 30rpx;
    font-weight: 600;
  }
  .tags {
    margin: 8rpx 0;
  }
  .price {
    font-size: 36rpx;
  }
  .notice {
    display: flex;
    align-items: center;
    padding: 10rpx 0;
    font-size: 26rpx;
  }
  .notice-text {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
</style>
