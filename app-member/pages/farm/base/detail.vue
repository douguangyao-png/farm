<!-- 基地详情（原型 07 farmDetail）：评分、资质、认养项目、动态、评价 -->
<template>
  <s-layout title="基地详情">
    <demo-badge />
    <view class="f-page" v-if="b">
      <image class="cover" :src="b.cover" mode="aspectFill" />
      <view class="f-card">
        <view class="f-between">
          <text class="name">{{ b.name }}</text>
          <text class="fav" :class="{ on: faved }" @tap="toggleFav">{{ faved ? '★ 已收藏' : '☆ 收藏' }}</text>
        </view>
        <view class="f-sub">{{ b.address }}</view>
        <view class="f-row" style="margin-top: 12rpx">
          <text class="f-tag orange" v-if="b.rating">{{ b.rating }} 分 · {{ b.reviewCount }} 条评价</text>
          <text class="f-tag gray" v-else>暂无评价</text>
          <text class="f-tag gray">{{ b.areaMu }} 亩</text>
          <text class="f-tag gray">{{ b.cropSummary }}</text>
        </view>
        <view class="f-row" style="margin-top: 16rpx">
          <button class="f-btn ghost mini" @tap="go('/pages/farm/visit/index', { baseId: b.id })">预约到访</button>
          <button class="f-btn ghost mini" style="margin-left: 16rpx" @tap="go('/pages/farm/service/chat')">问管家</button>
        </view>
      </view>

      <view class="f-card">
        <view class="f-title">基地资质</view>
        <view class="f-kv" v-for="c in b.certs" :key="c.name"><text class="k">{{ c.name }}</text><text class="v">{{ c.no }}</text></view>
      </view>

      <view class="f-title">认养项目</view>
      <product-card v-for="p in b.products" :key="p.id" :p="p" />
      <view class="f-empty" v-if="!b.products.length">该基地暂无在售项目</view>

      <template v-if="b.news.length">
        <view class="f-title">基地动态</view>
        <view class="f-card">
          <view class="news" v-for="n in b.news" :key="n.id" @tap="go('/pages/farm/article/detail', { id: n.id })">
            <view>{{ n.title }}</view>
            <view class="f-sub">{{ fmtTime(n.createTime, 'YYYY-MM-DD') }}</view>
          </view>
        </view>
      </template>

      <template v-if="b.reviews.length">
        <view class="f-title">认养人评价</view>
        <view class="f-card">
          <view class="news" v-for="r in b.reviews" :key="r.id">
            <view class="f-between"><text>{{ r.memberName }}</text><text class="f-tag orange">{{ r.score }} 分</text></view>
            <view class="f-sub">{{ r.content || '（未填写文字）' }}</view>
          </view>
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
  import { call, fmtTime } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';
  import ProductCard from '@/pages/farm/components/product-card.vue';

  const b = ref(null);
  const faved = ref(false);
  let id = 0;

  function go(url, params) {
    sheep.$router.go(url, params);
  }
  onLoad(async (opt) => {
    id = Number(opt.id);
    b.value = await call(FarmApi.getBase(id));
    if (sheep.$store('user').isLogin) faved.value = !!(await call(FarmApi.checkFavorite('base', id)));
  });
  async function toggleFav() {
    if (!sheep.$store('user').isLogin) return sheep.$helper.toast('请先登录');
    const r = await call(FarmApi.toggleFavorite('base', id));
    if (r !== null) {
      faved.value = r;
      sheep.$helper.toast(r ? '已收藏' : '已取消收藏');
    }
  }
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .cover {
    width: 100%;
    height: 340rpx;
    border-radius: 20rpx;
    margin-bottom: 20rpx;
  }
  .name {
    font-size: 34rpx;
    font-weight: 600;
  }
  .fav {
    font-size: 26rpx;
    color: $fsub;
  }
  .fav.on {
    color: $fo;
  }
  .news {
    padding: 16rpx 0;
    border-bottom: 2rpx solid $fline;
  }
  .news:last-child {
    border-bottom: none;
  }
  .mini {
    height: 60rpx;
    line-height: 60rpx;
    font-size: 26rpx;
  }
</style>
