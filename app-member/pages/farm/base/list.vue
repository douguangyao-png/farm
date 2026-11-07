<!-- 基地列表（原型 07 farmList） -->
<template>
  <s-layout title="找基地">
    <demo-badge />
    <view class="f-page">
      <view class="f-card base" v-for="b in list" :key="b.id" @tap="open(b)">
        <image class="cover" :src="b.cover" mode="aspectFill" />
        <view class="body">
          <view class="f-between">
            <text class="name">{{ b.name }}</text>
            <text class="f-tag orange" v-if="b.rating">{{ b.rating }} 分</text>
            <text class="f-tag gray" v-else>暂无评分</text>
          </view>
          <view class="f-sub">{{ b.address }}</view>
          <view class="f-sub">{{ b.areaMu }} 亩 · {{ b.cropSummary }}</view>
          <view class="f-row" style="margin-top: 10rpx">
            <text class="f-tag" v-if="b.certNo">已备案 {{ b.certNo }}</text>
            <text class="f-tag gray">{{ b.productCount }} 个认养项目</text>
          </view>
        </view>
      </view>
      <view v-if="loaded && !list.length" class="f-empty"><view class="f-empty-title">暂无基地</view></view>
    </view>
  </s-layout>
</template>

<script setup>
  import { ref } from 'vue';
  import { onShow } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { call } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const list = ref([]);
  const loaded = ref(false);
  function open(b) {
    sheep.$router.go('/pages/farm/base/detail', { id: b.id });
  }
  onShow(async () => {
    list.value = (await call(FarmApi.getBaseList())) || [];
    loaded.value = true;
  });
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .base {
    padding: 0;
    overflow: hidden;
  }
  .cover {
    width: 100%;
    height: 280rpx;
  }
  .body {
    padding: 20rpx 24rpx 24rpx;
  }
  .name {
    font-size: 32rpx;
    font-weight: 600;
  }
</style>
