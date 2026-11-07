<!-- 设置（原型 07 settings）：账号信息、推送与隐私开关（本机保存）、关于、退出登录 -->
<template>
  <s-layout title="设置">
    <demo-badge />
    <view class="f-page">
      <view class="f-card">
        <view class="f-title">账号与安全</view>
        <view class="f-kv"><text class="k">昵称</text><text class="v">{{ user.nickname || '—' }}</text></view>
        <view class="f-kv"><text class="k">手机号</text><text class="v">{{ user.mobile || '—' }}</text></view>
        <view class="f-kv"><text class="k">实名认证</text><text class="v f-sub">演示版未接入实名核验</text></view>
        <view class="f-kv" @tap="go('/pages/farm/address/list')"><text class="k">收货地址</text><text class="v">管理 ›</text></view>
      </view>

      <view class="f-card">
        <view class="f-title">消息推送</view>
        <view class="sw" v-for="s in SWITCHES" :key="s.key">
          <view>
            <view>{{ s.label }}</view>
            <view class="f-sub">{{ s.sub }}</view>
          </view>
          <switch :checked="pref[s.key]" color="#22a06b" @change="setPref(s.key, $event.detail.value)" />
        </view>
        <view class="f-sub tip">开关只保存在本机；正式版接入微信订阅消息后生效。</view>
      </view>

      <view class="f-card">
        <view class="f-title">关于</view>
        <view class="f-kv" @tap="go('/pages/farm/notice/index')"><text class="k">平台合规说明</text><text class="v">查看 ›</text></view>
        <view class="f-kv"><text class="k">版本</text><text class="v">演示版 v0.2</text></view>
      </view>

      <button v-if="isLogin" class="f-btn ghost block" @tap="logout">退出登录</button>
    </view>
  </s-layout>
</template>

<script setup>
  import { computed, reactive } from 'vue';
  import sheep from '@/sheep';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const KEY = 'farm_settings';
  const SWITCHES = [
    { key: 'task', label: '农事与任务提醒', sub: '自管任务到期、管家打卡' },
    { key: 'logistics', label: '物流通知', sub: '发货、签收' },
    { key: 'promo', label: '活动推荐', sub: '新品认养、基地活动' },
  ];
  const pref = reactive({ task: true, logistics: true, promo: false, ...(uni.getStorageSync(KEY) || {}) });
  const userStore = sheep.$store('user');
  const isLogin = computed(() => userStore.isLogin);
  const user = computed(() => userStore.userInfo || {});

  function setPref(k, v) {
    pref[k] = v;
    uni.setStorageSync(KEY, { ...pref });
  }
  function go(url) {
    sheep.$router.go(url);
  }
  function logout() {
    uni.showModal({
      title: '退出登录',
      content: '确定退出当前账号吗？',
      success: async (r) => {
        if (!r.confirm) return;
        await userStore.logout();
        sheep.$router.go('/pages/farm/mine');
      },
    });
  }
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .sw {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16rpx 0;
    border-bottom: 2rpx solid $fline;
  }
  .tip {
    margin-top: 14rpx;
  }
</style>
