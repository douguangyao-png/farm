<!-- 农场资讯详情（farmer-saas 新增）：只显示已发布的文章 -->
<template>
  <s-layout title="资讯详情">
    <demo-badge />
    <view class="f-page" v-if="article">
      <view class="f-card">
        <image v-if="article.picUrl" class="cover" :src="article.picUrl" mode="widthFix" />
        <view class="title">{{ article.title }}</view>
        <view class="f-sub">
          {{ article.author }}<text v-if="article.categoryName"> · {{ article.categoryName }}</text> · {{ fmtTime(article.createTime, 'YYYY-MM-DD') }} · {{ article.browseCount }} 次阅读
        </view>
        <view class="intro" v-if="article.introduction">{{ article.introduction }}</view>
        <!-- 正文是后台富文本（HTML），用 mp-html 渲染（芋道商城自带的富文本组件） -->
        <mp-html class="content" :content="article.content" />
      </view>
    </view>
    <view v-else-if="loaded" class="f-empty">文章不存在或已撤回</view>
  </s-layout>
</template>

<script setup>
  import { ref } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import FarmApi from '@/sheep/api/farm';
  import { fmtTime, call } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const article = ref(null);
  const loaded = ref(false);
  onLoad(async (options) => {
    article.value = await call(FarmApi.getArticle(options.id));
    loaded.value = true;
  });
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .cover { width: 100%; border-radius: 12rpx; margin-bottom: 20rpx; }
  .title { font-size: 36rpx; font-weight: 600; line-height: 1.4; margin-bottom: 12rpx; }
  .intro { margin: 20rpx 0; padding: 16rpx 20rpx; background: #f4f8f4; border-radius: 10rpx; font-size: 26rpx; color: #555; }
  .content { font-size: 30rpx; line-height: 1.8; }
</style>
