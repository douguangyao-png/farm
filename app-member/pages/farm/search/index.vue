<!-- 搜索（原型 07 search）：认养商品 + 基地；热门搜索、搜索历史存本地 -->
<template>
  <s-layout title="搜索">
    <demo-badge />
    <view class="f-page">
      <view class="bar f-row">
        <input class="inp" v-model="kw" confirm-type="search" placeholder="搜一分地、跑山鸡、基地名" @confirm="doSearch(kw)" />
        <button class="f-btn mini" @tap="doSearch(kw)">搜索</button>
      </view>

      <template v-if="!searched">
        <view class="f-card" v-if="hot.length">
          <view class="f-title">热门搜索</view>
          <view class="chips"><text class="f-tag chip" v-for="w in hot" :key="w" @tap="doSearch(w)">{{ w }}</text></view>
        </view>
        <view class="f-card" v-if="history.length">
          <view class="f-title">搜索历史<text class="f-more" @tap="clearHistory">清空</text></view>
          <view class="chips"><text class="f-tag gray chip" v-for="w in history" :key="w" @tap="doSearch(w)">{{ w }}</text></view>
        </view>
      </template>

      <template v-else>
        <view class="f-tabs">
          <view v-for="s in SORTS" :key="s.key" class="f-tab" :class="{ active: sort === s.key }" @tap="changeSort(s.key)">{{ s.label }}</view>
        </view>
        <view class="f-title" v-if="res.bases.length">相关基地</view>
        <view class="f-card base" v-for="b in res.bases" :key="b.id" @tap="sheep.$router.go('/pages/farm/base/detail', { id: b.id })">
          <image class="b-cover" :src="b.cover" mode="aspectFill" />
          <view>
            <view class="b-name">{{ b.name }}</view>
            <view class="f-sub">{{ b.address }}</view>
          </view>
        </view>
        <view class="f-title" v-if="res.products.length">认养商品</view>
        <product-card v-for="p in res.products" :key="p.id" :p="p" />
        <view v-if="!res.products.length && !res.bases.length" class="f-empty">
          <view class="f-empty-title">没有找到「{{ lastKw }}」</view>
          <view>换个词试试，或去分类商城逛逛</view>
          <button class="f-btn" style="margin-top: 24rpx" @tap="sheep.$router.go('/pages/farm/mall/index')">去分类商城</button>
        </view>
      </template>
    </view>
  </s-layout>
</template>

<script setup>
  import { ref } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { call } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';
  import ProductCard from '@/pages/farm/components/product-card.vue';

  const SORTS = [
    { key: 'default', label: '综合' },
    { key: 'sold', label: '认养最多' },
    { key: 'price', label: '价格低到高' },
  ];
  const HKEY = 'farm_search_history';
  const kw = ref('');
  const lastKw = ref('');
  const sort = ref('default');
  const hot = ref([]);
  const history = ref([]);
  const searched = ref(false);
  const res = ref({ products: [], bases: [] });

  onLoad(async (opt) => {
    history.value = uni.getStorageSync(HKEY) || [];
    hot.value = (await call(FarmApi.searchHot())) || [];
    if (opt?.keyword) doSearch(decodeURIComponent(opt.keyword));
  });

  async function doSearch(w) {
    const word = (w || '').trim();
    if (!word) return sheep.$helper.toast('请输入关键词');
    kw.value = word;
    lastKw.value = word;
    history.value = [word, ...history.value.filter((x) => x !== word)].slice(0, 8);
    uni.setStorageSync(HKEY, history.value);
    const d = await call(FarmApi.search({ keyword: word, sort: sort.value }));
    res.value = d || { products: [], bases: [] };
    searched.value = true;
  }
  function changeSort(k) {
    sort.value = k;
    doSearch(kw.value);
  }
  function clearHistory() {
    history.value = [];
    uni.removeStorageSync(HKEY);
  }
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .bar {
    margin-bottom: 20rpx;
    .inp {
      flex: 1;
      height: 72rpx;
      background: #fff;
      border-radius: 36rpx;
      padding: 0 28rpx;
      margin-right: 16rpx;
      font-size: 28rpx;
    }
  }
  .chips {
    display: flex;
    flex-wrap: wrap;
  }
  .chip {
    padding: 12rpx 24rpx;
    font-size: 26rpx;
    margin: 0 16rpx 16rpx 0;
  }
  .base {
    display: flex;
    align-items: center;
    padding: 16rpx;
  }
  .b-cover {
    width: 120rpx;
    height: 120rpx;
    border-radius: 14rpx;
    margin-right: 20rpx;
  }
  .b-name {
    font-size: 30rpx;
    font-weight: 600;
  }
  .mini {
    height: 64rpx;
    line-height: 64rpx;
    padding: 0 28rpx;
  }
</style>
