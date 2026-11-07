<!-- 物流详情（原型 07 logistics）：承运 + 单号、第几次配送、签收状态、轨迹、本批清单、复制单号、联系管家 -->
<template>
  <s-layout title="物流详情">
    <demo-badge />
    <view class="f-page" v-if="s">
      <view class="f-card">
        <view class="f-between">
          <text class="big">第 {{ s.nth }} 次配送</text>
          <text class="f-tag" :class="s.status === 20 ? '' : s.status === 10 ? 'orange' : 'gray'">{{ SHIPMENT_STATUS[s.status] }}</text>
        </view>
        <view class="f-kv"><text class="k">承运</text><text class="v">{{ s.carrier || '—' }}<text v-if="s.coldChain"> · 冷链</text></text></view>
        <view class="f-kv">
          <text class="k">运单号</text>
          <text class="v">{{ s.logisticsNo || '—' }}<text class="copy" v-if="s.logisticsNo" @tap="copy">复制</text></text>
        </view>
        <view class="f-kv"><text class="k">收货人</text><text class="v">{{ s.receiverName }} {{ s.receiverMobile }}</text></view>
        <view class="f-kv"><text class="k">地址</text><text class="v">{{ s.address }}</text></view>
      </view>

      <view class="f-card">
        <view class="f-title">物流轨迹</view>
        <view class="f-timeline" v-if="s.tracks && s.tracks.length">
          <view class="f-tl-item" :class="{ gray: i > 0 }" v-for="(t, i) in [...s.tracks].reverse()" :key="i">
            <view>{{ t.content }}</view>
            <view class="f-sub">{{ fmtTime(t.time) }}</view>
          </view>
        </view>
        <view class="f-sub" v-else>还没有物流信息，发货后更新</view>
      </view>

      <view class="f-card">
        <view class="f-title">本批清单</view>
        <view class="f-kv" v-for="x in s.items" :key="x.name"><text class="k">{{ x.name }}</text><text class="v">{{ x.weightJin }} 斤</text></view>
      </view>

      <view class="f-row">
        <button class="f-btn ghost" style="flex: 1; margin-right: 16rpx" @tap="call_">联系管家</button>
        <button class="f-btn" style="flex: 1" @tap="back">返回</button>
      </view>
    </view>
  </s-layout>
</template>

<script setup>
  import { ref } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { SHIPMENT_STATUS, call, fmtTime } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const s = ref(null);
  onLoad(async (opt) => {
    s.value = await call(FarmApi.getLogistics(opt.id));
  });
  function copy() {
    uni.setClipboardData({ data: s.value.logisticsNo, success: () => sheep.$helper.toast('已复制') });
  }
  function call_() {
    sheep.$router.go('/pages/farm/service/chat');
  }
  function back() {
    uni.navigateBack();
  }
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .big {
    font-size: 32rpx;
    font-weight: 600;
  }
  .copy {
    margin-left: 16rpx;
    color: $fg;
  }
</style>
