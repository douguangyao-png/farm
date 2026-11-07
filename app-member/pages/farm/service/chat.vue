<!-- 在线管家（原型 07 service）：与专属管家的聊天。演示：自动回复，管家端接入后改为真人回复 -->
<template>
  <s-layout title="在线管家">
    <demo-badge />
    <view class="f-page chat">
      <view class="f-ok" v-if="steward">您的专属管家：{{ steward.name }}<text v-if="steward.mobile"> · {{ steward.mobile }}</text></view>
      <view class="f-warn" v-else>您还没有认养，认养后会为您分配专属管家。</view>
      <view class="f-sub ref" v-if="refText">正在咨询：{{ refText }}</view>

      <scroll-view scroll-y class="list" :scroll-top="top">
        <view class="f-sub empty" v-if="!list.length">有问题直接发消息，管家会尽快回复。</view>
        <view class="m" :class="m.from" v-for="m in list" :key="m.id">
          <view class="bubble">{{ m.content }}</view>
          <view class="f-sub time">{{ fmtTime(m.createTime, 'MM-DD HH:mm') }}</view>
        </view>
      </scroll-view>

      <view class="send">
        <input class="inp" v-model="text" confirm-type="send" placeholder="输入消息" @confirm="send" />
        <button class="f-btn mini" @tap="send">发送</button>
      </view>
    </view>
  </s-layout>
</template>

<script setup>
  import { ref } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { call, fmtTime, needLogin } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const list = ref([]);
  const steward = ref(null);
  const text = ref('');
  const top = ref(0);
  const refText = ref('');

  async function load() {
    const d = await call(FarmApi.getServiceMessages());
    if (!d) return;
    steward.value = d.steward;
    list.value = d.list;
    top.value = 99999 + list.value.length;
  }
  onLoad((opt) => {
    refText.value = (opt && opt.ref) || '';
    if (!needLogin()) load();
  });
  async function send() {
    const c = text.value.trim();
    if (!c) return;
    if (needLogin()) return;
    const content = refText.value ? `【${refText.value}】${c}` : c;
    text.value = '';
    refText.value = '';
    await call(FarmApi.sendServiceMessage(content));
    load();
  }
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .chat {
    display: flex;
    flex-direction: column;
    height: 100vh;
  }
  .list {
    flex: 1;
    height: 0;
  }
  .empty {
    text-align: center;
    padding: 60rpx 0;
  }
  .m {
    margin-bottom: 24rpx;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
  }
  .m.me {
    align-items: flex-end;
  }
  .bubble {
    max-width: 78%;
    padding: 18rpx 24rpx;
    border-radius: 18rpx;
    background: #fff;
    line-height: 1.5;
  }
  .m.me .bubble {
    background: $fg;
    color: #fff;
  }
  .time {
    margin-top: 6rpx;
  }
  .ref {
    margin-bottom: 12rpx;
  }
  .send {
    display: flex;
    align-items: center;
    padding-top: 16rpx;
    padding-bottom: env(safe-area-inset-bottom);
  }
  .inp {
    flex: 1;
    height: 72rpx;
    background: #fff;
    border-radius: 36rpx;
    padding: 0 28rpx;
    margin-right: 16rpx;
  }
</style>
