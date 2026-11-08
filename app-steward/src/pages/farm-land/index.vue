<template>
  <view class="farm-page">
    <wd-navbar title="标的管理" placeholder safe-area-inset-top fixed />
    <view class="tabs">
      <view v-for="t in TABS" :key="t.k" class="tab" :class="{ on: tab === t.k }" @click="tab = t.k">
        {{ t.text }}{{ data ? `（${data.counts[t.k]}）` : '' }}
      </view>
    </view>
    <view v-if="data && data.list.length === 0" class="farm-empty">
      暂无标的
    </view>
    <view v-for="a in data?.list" :key="a.id" class="farm-card" @click="goDetail(a.id)">
      <view class="farm-row">
        <text class="farm-t15">
          {{ a.code }} {{ a.name }}
        </text>
        <text class="farm-tag" :class="a.raising ? 'st-green' : 'st-gray'">
          {{ a.statusName }}
        </text>
      </view>
      <view class="farm-t12 mt8">
        {{ a.raising ? `${a.memberName} · ${a.crop}${a.mine ? '' : ' · 非我负责'}` : '待分配认养' }}{{ a.areaM2 ? ` · ${a.areaM2}㎡` : '' }}
      </view>
      <template v-if="a.raising">
        <view class="bar">
          <view class="fill" :style="{ width: `${a.progress}%` }" />
        </view>
        <view class="farm-t12 mt8">
          产出 {{ a.harvestedJin }}{{ a.promisedJin ? ` / ${a.promisedJin}` : '' }} 斤 · 剩 {{ a.leftDays }} 天
        </view>
      </template>
    </view>
    <view class="farm-note">
      畜禽养殖批次的数据结构暂未建，这里只列地块、果树、单只标的（见分工登记）。
    </view>
    <DemoBadge />
  </view>
</template>

<script lang="ts" setup>
import type { StewardAssetItem } from '@/api/farm/steward'
import { ref, watch } from 'vue'
import { getStewardAssets } from '@/api/farm/steward'
import DemoBadge from '@/pages-farm/components/demo-badge.vue'

definePage({ style: { navigationBarTitleText: '标的管理', navigationStyle: 'custom' } })

const TABS = [{ k: 'all', text: '全部' }, { k: 'raising', text: '在养' }, { k: 'free', text: '空闲/休整' }] as const
const tab = ref<'all' | 'raising' | 'free'>('all')
const data = ref<{ counts: { all: number, raising: number, free: number }, list: StewardAssetItem[] }>()

async function load() {
  try {
    data.value = await getStewardAssets(tab.value)
  } catch {
    data.value = undefined
  }
}
watch(tab, () => load())
onShow(() => load())

function goDetail(id: number) {
  uni.navigateTo({ url: `/pages-farm/asset/detail?id=${id}` })
}
</script>

<style lang="scss" scoped>
@import '@/pages-farm/components/farm-extra.scss';

.bar {
  height: 10rpx;
  margin-top: 14rpx;
  overflow: hidden;
  background: #eef0f3;
  border-radius: 6rpx;
  .fill {
    height: 100%;
    background: $g;
  }
}
</style>
