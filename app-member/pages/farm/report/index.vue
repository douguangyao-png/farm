<!-- 质检报告（原型 07 report）：检测项目 / 限量值 / 结果。第三方检测数据为演示 -->
<template>
  <s-layout title="质检报告">
    <demo-badge />
    <view class="f-page" v-if="d">
      <view class="f-card head">
        <view class="f-between">
          <text class="title">{{ d.crop }} 农残检测</text>
          <text class="f-tag">{{ d.conclusion }}</text>
        </view>
        <view class="f-kv"><text class="k">报告编号</text><text class="v">{{ d.reportNo }}</text></view>
        <view class="f-kv"><text class="k">检测机构</text><text class="v">{{ d.org }}</text></view>
        <view class="f-kv"><text class="k">地块</text><text class="v">{{ d.assetCode }}</text></view>
        <view class="f-kv"><text class="k">采样日期</text><text class="v">{{ fmtTime(d.sampleDate, 'YYYY-MM-DD') }}</text></view>
      </view>

      <view class="f-card">
        <view class="f-title">检测项目</view>
        <view class="tr th"><text class="c1">项目</text><text class="c2">限量值</text><text class="c3">结果</text></view>
        <view class="tr" v-for="x in d.items" :key="x.name">
          <text class="c1">{{ x.name }}</text>
          <text class="c2 f-sub">{{ x.limit }}</text>
          <text class="c3" :class="x.pass ? 'ok' : 'bad'">{{ x.result }}</text>
        </view>
      </view>

      <view class="f-warn">这是演示数据：正式版由基地上传第三方检测机构出具的报告，支持下载 PDF 和扫码验真。</view>
      <button class="f-btn ghost block" @tap="tip">下载 PDF / 验真</button>
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

  const d = ref(null);
  onLoad(async (opt) => {
    d.value = await call(FarmApi.getReport(opt.adoptionId));
  });
  function tip() {
    sheep.$helper.toast('正式版接入检测机构后开放');
  }
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .title {
    font-size: 32rpx;
    font-weight: 600;
  }
  .tr {
    display: flex;
    padding: 18rpx 0;
    border-bottom: 2rpx solid $fline;
    font-size: 26rpx;
  }
  .th {
    color: $fsub;
  }
  .c1 {
    flex: 1.2;
  }
  .c2 {
    flex: 1.4;
  }
  .c3 {
    flex: 1;
    text-align: right;
  }
  .ok {
    color: $fgd;
  }
  .bad {
    color: $fr;
  }
</style>
