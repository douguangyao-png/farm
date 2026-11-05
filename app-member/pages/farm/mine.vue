<!-- 我的（farmer-saas 新增，底部导航「我的」） -->
<template>
  <s-layout title="我的" tabbar="/pages/farm/mine">
    <demo-badge />
    <view class="f-page">
      <view class="user" @tap="!isLogin && login()">
        <view class="avatar">{{ isLogin ? (user.nickname || '认').slice(0, 1) : '?' }}</view>
        <view v-if="isLogin">
          <view class="nick">{{ user.nickname || '认养人' }}</view>
          <view class="mobile">{{ user.mobile }}</view>
        </view>
        <view v-else>
          <view class="nick">点击登录</view>
          <view class="mobile">登录后查看认养、任务和配送</view>
        </view>
      </view>

      <view class="f-card menu">
        <view class="m-item" v-for="m in MENUS" :key="m.url" @tap="go(m)">
          <text>{{ m.label }}</text>
          <text class="f-sub">{{ m.sub }} ›</text>
        </view>
      </view>

      <button v-if="isLogin" class="f-btn ghost block" @tap="logout">退出登录</button>
      <button v-else class="f-btn block" @tap="login">登录</button>
    </view>
  </s-layout>
</template>

<script setup>
  import { computed } from 'vue';
  import { onShow } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import { showAuthModal } from '@/sheep/hooks/useModal';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  uni.hideTabBar({ fail: () => {} });

  const MENUS = [
    { label: '我的认养', sub: '地块、档案、监控', url: '/pages/farm/adoption/list', auth: true },
    { label: '自管任务', sub: '半托管 / 不托管的农活', url: '/pages/farm/self-task/list', auth: true },
    { label: '我的农资', sub: '订单和溯源登记', url: '/pages/farm/supply/order-list', auth: true },
    { label: '收获配送', sub: '物流轨迹', url: '/pages/farm/shipment/list', auth: true },
    { label: '农资商城', sub: '种子、肥料、代管服务', url: '/pages/farm/supply/list', auth: false },
  ];

  const userStore = sheep.$store('user');
  const isLogin = computed(() => userStore.isLogin);
  const user = computed(() => userStore.userInfo || {});

  function login() {
    showAuthModal();
  }
  function go(m) {
    if (m.auth && !isLogin.value) return login();
    sheep.$router.go(m.url);
  }
  function logout() {
    uni.showModal({
      title: '退出登录',
      content: '确认退出当前账号吗？',
      success: async (r) => {
        if (!r.confirm) return;
        await userStore.logout();
        sheep.$helper.toast('已退出登录');
      },
    });
  }

  onShow(() => {
    if (isLogin.value) userStore.updateUserData();
  });
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .user {
    display: flex;
    align-items: center;
    background: linear-gradient(135deg, $fg, $fgd);
    color: #fff;
    border-radius: 20rpx;
    padding: 40rpx 30rpx;
    margin-bottom: 20rpx;
  }
  .avatar {
    width: 110rpx;
    height: 110rpx;
    line-height: 110rpx;
    text-align: center;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.25);
    font-size: 44rpx;
    font-weight: 700;
    margin-right: 24rpx;
  }
  .nick {
    font-size: 34rpx;
    font-weight: 700;
  }
  .mobile {
    font-size: 24rpx;
    opacity: 0.85;
    margin-top: 6rpx;
  }
  .menu {
    padding: 0 24rpx;
  }
  .m-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 30rpx 0;
    border-bottom: 2rpx solid $fline;
    font-size: 28rpx;
  }
  .m-item:last-child {
    border-bottom: none;
  }
</style>
