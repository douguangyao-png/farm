<!-- 发表评价（原型 07 review）：总体评分 + 3 个维度（农产品品质 / 管家服务 / 配送物流）+ 文字 -->
<template>
  <s-layout title="发表评价">
    <demo-badge />
    <view class="f-page">
      <view class="f-card">
        <view class="f-title">总体评分</view>
        <view class="stars"><text v-for="n in 5" :key="n" class="star" :class="{ on: n <= form.score }" @tap="form.score = n">★</text></view>
        <view class="dim" v-for="d in DIMS" :key="d.key">
          <text>{{ d.label }}</text>
          <view class="stars sm"><text v-for="n in 5" :key="n" class="star" :class="{ on: n <= form.dims[d.key] }" @tap="form.dims[d.key] = n">★</text></view>
        </view>
      </view>
      <view class="f-card">
        <textarea class="ta" v-model="form.content" maxlength="500" placeholder="说说这次认养的体验，帮助其他认养人参考（选填）" />
      </view>
      <button class="f-btn block" :class="{ disabled: busy }" @tap="submit">提交评价</button>
    </view>
  </s-layout>
</template>

<script setup>
  import { reactive, ref } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { callRaw, needLogin } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const DIMS = [
    { key: 'freshness', label: '农产品品质' },
    { key: 'service', label: '管家服务' },
    { key: 'logistics', label: '配送物流' },
  ];
  const form = reactive({ adoptionId: 0, score: 5, dims: { freshness: 5, service: 5, logistics: 5 }, content: '' });
  const busy = ref(false);

  onLoad((opt) => {
    form.adoptionId = Number(opt.adoptionId);
  });
  async function submit() {
    if (needLogin()) return;
    busy.value = true;
    const r = await callRaw(FarmApi.createReview({ ...form, dims: { ...form.dims } }));
    busy.value = false;
    if (r.code !== 0) return;
    sheep.$helper.toast('感谢评价');
    setTimeout(() => uni.navigateBack(), 600);
  }
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .stars {
    display: flex;
    margin-bottom: 20rpx;
  }
  .star {
    font-size: 60rpx;
    color: #d6dae0;
    margin-right: 12rpx;
  }
  .star.on {
    color: $fo;
  }
  .sm .star {
    font-size: 40rpx;
  }
  .sm {
    margin-bottom: 0;
  }
  .dim {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12rpx 0;
  }
  .ta {
    width: 100%;
    height: 240rpx;
    font-size: 28rpx;
  }
</style>
