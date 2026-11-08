<template>
  <view class="farm-page">
    <wd-navbar title="绩效与工具" left-arrow placeholder safe-area-inset-top fixed @click-left="goBack" />
    <view class="hero">
      <view class="farm-t15 name">
        {{ p?.stewardName || '—' }}
      </view>
      <view class="sub">
        {{ p?.baseName }} · 管家
      </view>
      <view class="cells">
        <view v-for="c in cells" :key="c.lbl" class="c">
          <view class="n">
            {{ c.num }}
          </view>
          <view class="l">
            {{ c.lbl }}
          </view>
        </view>
      </view>
    </view>

    <view class="farm-card">
      <view class="farm-row">
        <text class="farm-t15">
          我的绩效
        </text>
        <text class="farm-t12">
          {{ p?.month }}
        </text>
      </view>
      <view v-for="k in kv" :key="k.k" class="farm-kv">
        <text class="k">
          {{ k.k }}
        </text>
        <text class="v">
          {{ k.v }}
        </text>
      </view>
      <view class="farm-note">
        打卡完成率 = 本月到期的管家任务里按时完成的占比；产出达标 = 已收成 / 约定产量。响应时长、满意度、绩效分系统暂无数据，不显示数字。
      </view>
    </view>

    <view class="farm-card grid">
      <view v-for="e in ENTRIES" :key="e.text" class="g" @click="go(e.url)">
        <view class="ic">
          {{ e.ic }}
        </view>
        <view class="farm-t13">
          {{ e.text }}
        </view>
      </view>
    </view>
    <DemoBadge />
  </view>
</template>

<script lang="ts" setup>
import type { Performance } from '@/api/farm/steward'
import { computed, ref } from 'vue'
import { getPerformance } from '@/api/farm/steward'
import { navigateBackPlus } from '@/utils'
import DemoBadge from '../components/demo-badge.vue'

definePage({ style: { navigationBarTitleText: '绩效与工具', navigationStyle: 'custom' } })

const ENTRIES = [
  { text: '我的记录', ic: '记', url: '/pages-farm/record/index' },
  { text: '配送确认', ic: '送', url: '/pages-farm/delivery/index' },
  { text: '异常上报', ic: '警', url: '/pages-farm/alarm/index' },
  { text: '农资代办', ic: '资', url: '/pages-farm/supply-task/index' },
  { text: '自管指导', ic: '导', url: '/pages-farm/self-guide/index' },
  { text: '标的管理', ic: '地', url: '/pages/farm-land/index' },
  { text: '客户消息', ic: '信', url: '/pages/farm-msg/index' },
  { text: '代管工单', ic: '单', url: '/pages-farm/service-order/index' },
]
const p = ref<Performance>()
const pct = (v: number | null) => (v === null || v === undefined ? '暂无数据' : `${v}%`)
const cells = computed(() => [
  { lbl: '负责标的', num: p.value?.assetCount ?? '-' },
  { lbl: '服务认养人', num: p.value?.memberCount ?? '-' },
  { lbl: '本月打卡', num: p.value?.monthCheckins ?? '-' },
])
const kv = computed(() => [
  { k: '打卡完成率', v: pct(p.value?.completionRate ?? null) },
  { k: '记录审核通过率', v: pct(p.value?.passRate ?? null) },
  { k: '产出达标', v: pct(p.value?.outputRate ?? null) },
  { k: '平均响应时长', v: p.value?.responseMinutes == null ? '暂无数据' : `${p.value.responseMinutes} 分钟` },
  { k: '客户满意度', v: p.value?.satisfaction == null ? '暂无数据' : String(p.value.satisfaction) },
])

onShow(async () => {
  try {
    p.value = await getPerformance()
  } catch {
    p.value = undefined
  }
})

function go(url: string) {
  if (['/pages/index/index', '/pages/farm-land/index', '/pages/farm-msg/index', '/pages/user/index'].includes(url)) uni.switchTab({ url })
  else uni.navigateTo({ url })
}
function goBack() {
  navigateBackPlus('/pages/index/index')
}
</script>

<style lang="scss" scoped>
@import '../components/farm-extra.scss';

.hero {
  padding: 28rpx 32rpx 36rpx;
  color: #fff;
  background: linear-gradient(150deg, $g, $gd);
  .name {
    font-size: 36rpx;
  }
  .sub {
    margin-top: 6rpx;
    font-size: 24rpx;
    opacity: 0.9;
  }
  .cells {
    display: flex;
    margin-top: 28rpx;
    .c {
      flex: 1;
      text-align: center;
    }
    .n {
      font-size: 36rpx;
      font-weight: 700;
    }
    .l {
      margin-top: 4rpx;
      font-size: 22rpx;
      opacity: 0.85;
    }
  }
}
.grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 32rpx 0;
  .g {
    text-align: center;
  }
  .ic {
    width: 80rpx;
    height: 80rpx;
    margin: 0 auto 8rpx;
    font-size: 32rpx;
    line-height: 80rpx;
    color: $gd;
    background: $gl;
    border-radius: 50%;
  }
}
</style>
