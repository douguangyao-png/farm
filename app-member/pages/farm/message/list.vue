<!-- 消息中心（原型 07 message）：全部 / 系统 / 物流 / 农事 / 互动；消息由订单、配送、农事记录、管家回复派生 -->
<template>
  <s-layout title="消息中心">
    <demo-badge />
    <view class="f-page">
      <view class="f-tabs">
        <view v-for="t in TABS" :key="t.key" class="f-tab" :class="{ active: tab === t.key }" @tap="pick(t.key)">{{ t.label }}</view>
      </view>
      <view class="f-between bar" v-if="unread">
        <text class="f-sub">{{ unread }} 条未读</text>
        <text class="f-more" @tap="readAll">全部已读</text>
      </view>
      <view class="f-card msg" v-for="m in list" :key="m.key" @tap="open(m)">
        <view class="f-between">
          <view class="f-row"><view class="dot" v-if="!m.read" /><text class="t">{{ m.title }}</text><text class="f-tag gray">{{ TYPE_NAME[m.type] }}</text></view>
          <text class="f-sub">{{ fmtTime(m.createTime, 'MM-DD HH:mm') }}</text>
        </view>
        <view class="c">{{ m.content }}</view>
      </view>
      <view v-if="loaded && !list.length" class="f-empty"><view class="f-empty-title">暂无消息</view></view>
    </view>
  </s-layout>
</template>

<script setup>
  import { ref, watch } from 'vue';
  import { onShow } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { call, fmtTime, needLogin } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const TABS = [
    { key: '', label: '全部' },
    { key: 'system', label: '系统' },
    { key: 'logistics', label: '物流' },
    { key: 'farming', label: '农事' },
    { key: 'interact', label: '互动' },
  ];
  const TYPE_NAME = { system: '系统', logistics: '物流', farming: '农事', interact: '互动' };
  const tab = ref('');
  const list = ref([]);
  const unread = ref(0);
  const loaded = ref(false);

  async function load() {
    if (needLogin()) return;
    const d = await call(FarmApi.getMessageList(tab.value));
    list.value = (d && d.list) || [];
    unread.value = (d && d.unread) || 0;
    loaded.value = true;
  }
  function pick(k) {
    tab.value = k;
    load();
  }
  async function readAll() {
    await call(FarmApi.readMessage());
    load();
  }
  async function open(m) {
    if (!m.read) await call(FarmApi.readMessage(m.key));
    if (m.link) sheep.$router.go(m.link);
  }
  onShow(load);
  watch(
    () => sheep.$store('user').isLogin,
    (v) => v && load(),
  );
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .bar {
    margin-bottom: 14rpx;
  }
  .msg .t {
    font-size: 30rpx;
    font-weight: 600;
    margin-right: 12rpx;
  }
  .msg .c {
    margin-top: 10rpx;
    color: $fsub;
    font-size: 26rpx;
  }
  .dot {
    width: 14rpx;
    height: 14rpx;
    border-radius: 50%;
    background: $fr;
    margin-right: 10rpx;
  }
</style>
