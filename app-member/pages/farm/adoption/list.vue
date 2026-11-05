<!-- 我的认养（farmer-saas 新增，底部导航「认养」） -->
<template>
  <s-layout title="我的认养" tabbar="/pages/farm/adoption/list">
    <demo-badge />
    <view class="f-page">
      <view v-if="!isLogin" class="f-empty">
        <view class="f-empty-title">登录后查看您认养的地块</view>
        <button class="f-btn" @tap="login">登录</button>
      </view>
      <template v-else>
        <view class="f-card item" v-for="a in list" :key="a.id" @tap="toDetail(a.id)">
          <view class="f-row">
            <image class="cover" :src="a.cover" mode="aspectFill" />
            <view class="body">
              <view class="f-between">
                <text class="title">{{ a.productTitle }}</text>
                <text class="f-tag" :class="modeClass(a.careMode)">{{ a.careModeName || CARE_MODE_NAME[a.careMode] }}</text>
              </view>
              <view class="f-sub">{{ a.baseName }} · 地块 <text class="code">{{ a.assetCode }}</text></view>
              <view class="f-sub">{{ fmtTime(a.startAt, 'YYYY-MM-DD') }} 至 {{ fmtTime(a.endAt, 'YYYY-MM-DD') }}</view>
            </view>
          </view>
          <view class="promise" v-if="a.promisedJin !== null && a.promisedJin !== undefined">
            <view class="f-between f-sub">
              <text>约定交付 {{ a.promisedJin }} 斤</text>
              <text>已收 {{ a.harvestedJin || 0 }} 斤</text>
            </view>
            <view class="f-progress"><view class="bar" :style="{ width: pct(a) + '%' }" /></view>
          </view>
          <view class="f-between foot">
            <text v-if="a.selfTodoCount > 0" class="f-tag orange">{{ a.selfTodoCount }} 项自管待办</text>
            <text v-else class="f-sub">{{ a.careMode === 'full' ? '全托管，无需您动手' : '暂无自管待办' }}</text>
            <text class="f-more">查看详情 ›</text>
          </view>
        </view>
        <view v-if="loaded && !list.length" class="f-empty">
          <view class="f-empty-title">还没有认养</view>
          <button class="f-btn" @tap="toHome">去认养一块地</button>
        </view>
      </template>
    </view>
  </s-layout>
</template>

<script setup>
  import { computed, ref, watch } from 'vue';
  import { onShow, onPullDownRefresh } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { CARE_MODE_NAME, fmtTime, call } from '@/pages/farm/common/farm';
  import { showAuthModal } from '@/sheep/hooks/useModal';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  uni.hideTabBar({ fail: () => {} });

  const list = ref([]);
  const loaded = ref(false);
  const isLogin = computed(() => sheep.$store('user').isLogin);

  const modeClass = (m) => (m === 'full' ? 'solid' : m === 'semi' ? '' : 'orange');
  const pct = (a) => Math.min(100, Math.round(((a.harvestedJin || 0) / a.promisedJin) * 100));

  async function load() {
    if (!isLogin.value) return;
    list.value = (await call(FarmApi.getAdoptionList())) || [];
    loaded.value = true;
  }
  function login() {
    showAuthModal();
  }
  function toDetail(id) {
    sheep.$router.go('/pages/farm/adoption/detail', { id });
  }
  function toHome() {
    sheep.$router.go('/pages/farm/index');
  }

  watch(isLogin, (v) => v && load());
  onShow(load);
  onPullDownRefresh(async () => {
    await load();
    uni.stopPullDownRefresh();
  });
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .cover {
    width: 140rpx;
    height: 140rpx;
    border-radius: 14rpx;
    margin-right: 20rpx;
    flex-shrink: 0;
  }
  .body {
    flex: 1;
    min-width: 0;
  }
  .title {
    font-size: 30rpx;
    font-weight: 600;
  }
  .code {
    color: $fg;
    font-weight: 600;
  }
  .promise {
    margin-top: 20rpx;
    .f-between {
      margin-bottom: 8rpx;
    }
  }
  .foot {
    margin-top: 20rpx;
    border-top: 2rpx solid $fline;
    padding-top: 16rpx;
  }
</style>
