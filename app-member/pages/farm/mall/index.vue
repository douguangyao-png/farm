<!-- 分类商城（原型 07 mall）：按品类（一分地 / 畜牧 / 果树）+ 排序浏览认养商品 -->
<template>
  <s-layout title="分类商城">
    <demo-badge />
    <view class="f-page">
      <view class="f-tabs">
        <view v-for="c in CATS" :key="c.key" class="f-tab" :class="{ active: type === c.key }" @tap="pick(c.key)">{{ c.label }}</view>
      </view>
      <view class="sorts f-row">
        <text v-for="s in SORTS" :key="s.key" class="sort" :class="{ on: sort === s.key }" @tap="pickSort(s.key)">{{ s.label }}</text>
        <text class="sort go" @tap="toSearch">搜索 ›</text>
      </view>
      <product-card v-for="p in list" :key="p.id" :p="p" />
      <view v-if="loaded && !list.length" class="f-empty">
        <view class="f-empty-title">这个分类暂时没有商品</view>
      </view>
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

  const CATS = [
    { key: '', label: '全部' },
    { key: 'plot', label: '一分地' },
    { key: 'animal', label: '畜牧家禽' },
    { key: 'tree', label: '果树' },
  ];
  const SORTS = [
    { key: 'default', label: '综合' },
    { key: 'sold', label: '认养最多' },
    { key: 'price', label: '价格' },
  ];
  const type = ref('');
  const sort = ref('default');
  const list = ref([]);
  const loaded = ref(false);

  onLoad((opt) => {
    type.value = (opt && opt.type) || '';
    load();
  });
  function pick(k) {
    type.value = k;
    load();
  }
  function pickSort(k) {
    sort.value = k;
    load();
  }
  function toSearch() {
    sheep.$router.go('/pages/farm/search/index');
  }
  async function load() {
    const d = await call(FarmApi.search({ type: type.value || undefined, sort: sort.value }));
    list.value = (d && d.products) || [];
    loaded.value = true;
  }
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .sorts {
    margin-bottom: 20rpx;
  }
  .sort {
    margin-right: 36rpx;
    font-size: 26rpx;
    color: $fsub;
  }
  .sort.on {
    color: $fg;
    font-weight: 600;
  }
  .sort.go {
    margin-left: auto;
    margin-right: 0;
  }
</style>
