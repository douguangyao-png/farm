<!-- 农资商城（farmer-saas 新增，底部导航「农资」） -->
<template>
  <s-layout title="农资商城" tabbar="/pages/farm/supply/list">
    <demo-badge />
    <view class="f-page">
      <view class="f-between top">
        <text class="f-sub">农资必须绑定一块认养地，用量会写进溯源档案</text>
        <text class="f-more link" @tap="toOrders">我的农资 ›</text>
      </view>
      <view class="f-tabs">
        <view v-for="c in SUPPLY_CATS" :key="c.value" class="f-tab" :class="{ active: cat === c.value }" @tap="switchCat(c.value)">
          {{ c.label }}
        </view>
      </view>
      <view class="grid">
        <view class="cell" v-for="s in list" :key="s.id" @tap="toDetail(s.id)">
          <image class="img" :src="s.cover" mode="aspectFill" />
          <view class="cell-body">
            <view class="name">{{ s.name }}</view>
            <view class="f-sub spec">{{ s.spec }}</view>
            <view class="tags">
              <text class="f-tag orange" v-if="s.needWorkorder">管家上门</text>
              <text class="f-tag gray" v-if="s.fitModes?.length">适合{{ s.fitModes.map((m) => CARE_MODE_NAME[m]).join('/') }}</text>
            </view>
            <view class="f-between">
              <text class="f-price">{{ yuan(s.price) }}<text class="unit">/{{ s.unit }}</text></text>
              <text class="f-sub">已售 {{ s.sold }}</text>
            </view>
          </view>
        </view>
      </view>
      <view v-if="loaded && !list.length" class="f-empty">这个分类暂时没有商品</view>
    </view>
  </s-layout>
</template>

<script setup>
  import { ref } from 'vue';
  import { onShow } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { CARE_MODE_NAME, SUPPLY_CATS, yuan, call } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  uni.hideTabBar({ fail: () => {} });

  const cat = ref('');
  const list = ref([]);
  const loaded = ref(false);

  async function load() {
    list.value = (await call(FarmApi.getSupplyList(cat.value))) || [];
    loaded.value = true;
  }
  function switchCat(v) {
    cat.value = v;
    load();
  }
  function toDetail(id) {
    sheep.$router.go('/pages/farm/supply/detail', { id });
  }
  function toOrders() {
    sheep.$router.go('/pages/farm/supply/order-list');
  }

  onShow(() => {
    // 从别的页面带分类进来（tabBar 不能带参数，走 app store 的 paramsForTabbar）
    const app = sheep.$store('app');
    if (app.paramsForTabbar?.cat !== undefined) {
      cat.value = app.paramsForTabbar.cat;
      app.clearParamsForTabbar();
    }
    load();
  });
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .top {
    margin-bottom: 16rpx;
    .link {
      color: $fg;
      flex-shrink: 0;
      margin-left: 16rpx;
    }
  }
  .grid {
    display: flex;
    flex-wrap: wrap;
    justify-content: space-between;
  }
  .cell {
    width: calc(50% - 10rpx);
    background: #fff;
    border-radius: 20rpx;
    overflow: hidden;
    margin-bottom: 20rpx;
  }
  .img {
    width: 100%;
    height: 250rpx;
  }
  .cell-body {
    padding: 16rpx;
  }
  .name {
    font-size: 28rpx;
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .spec {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .tags {
    margin: 8rpx 0;
    min-height: 34rpx;
  }
  .unit {
    font-size: 22rpx;
    font-weight: 400;
  }
</style>
