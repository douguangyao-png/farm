<!-- 认养商品卡片（搜索、商城、收藏、基地详情共用） -->
<template>
  <view class="pc f-card" @tap="open">
    <image class="cover" :src="p.cover" mode="aspectFill" />
    <view class="body">
      <view class="title">{{ p.title }}</view>
      <view class="f-sub">{{ p.baseName }} · {{ p.categoryName }}</view>
      <view class="f-between">
        <view><text class="f-price">{{ yuan(p.minPrice) }}</text><text class="f-sub"> 起</text></view>
        <text class="f-sub">已认养 {{ p.sold }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
  import sheep from '@/sheep';
  import { yuan } from '@/pages/farm/common/farm';
  const props = defineProps({ p: { type: Object, required: true } });
  function open() {
    sheep.$router.go('/pages/farm/product/detail', { id: props.p.id });
  }
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .pc {
    display: flex;
    padding: 16rpx;
  }
  .cover {
    width: 200rpx;
    height: 200rpx;
    border-radius: 14rpx;
    flex-shrink: 0;
    margin-right: 20rpx;
  }
  .body {
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
  }
  .title {
    font-size: 30rpx;
    font-weight: 600;
  }
</style>
