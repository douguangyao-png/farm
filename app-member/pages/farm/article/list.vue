<!-- 农场资讯列表（farmer-saas 新增）：经营者在后台「系统管理 → 文章管理」发布的文章 -->
<template>
  <s-layout title="农场资讯">
    <demo-badge />
    <view class="f-page">
      <view class="f-card art" v-for="a in list" :key="a.id" @tap="open(a.id)">
        <image v-if="a.picUrl" class="pic" :src="a.picUrl" mode="aspectFill" />
        <view class="body">
          <view class="t">{{ a.title }}</view>
          <view class="f-sub intro" v-if="a.introduction">{{ a.introduction }}</view>
          <view class="f-sub">{{ a.author }} · {{ fmtTime(a.createTime, 'YYYY-MM-DD') }} · {{ a.browseCount }} 次阅读</view>
        </view>
      </view>
      <view v-if="loaded && !list.length" class="f-empty">农场还没有发布资讯</view>
    </view>
  </s-layout>
</template>

<script setup>
  import { ref } from 'vue';
  import { onShow } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { fmtTime, call } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const list = ref([]);
  const loaded = ref(false);
  onShow(async () => {
    list.value = (await call(FarmApi.getArticleList())) || [];
    loaded.value = true;
  });
  const open = (id) => sheep.$router.go('/pages/farm/article/detail', { id });
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .art { display: flex; gap: 20rpx; }
  .pic { width: 180rpx; height: 135rpx; border-radius: 12rpx; flex-shrink: 0; }
  .body { flex: 1; min-width: 0; }
  .t { font-size: 30rpx; font-weight: 600; margin-bottom: 8rpx; }
  .intro { margin-bottom: 6rpx; }
</style>
