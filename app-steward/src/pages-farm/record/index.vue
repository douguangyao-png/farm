<template>
  <view class="farm-page">
    <wd-navbar title="我的记录" left-arrow placeholder safe-area-inset-top fixed @click-left="goBack" />
    <view class="farm-note">
      你提交的记录审核通过后会同步到认养人的「生长档案」，是他们判断地块有人照顾的依据。
    </view>
    <view class="tabs">
      <view v-for="t in TABS" :key="t.k" class="tab" :class="{ on: tab === t.k }" @click="tab = t.k">
        {{ t.text }}{{ data ? `（${data.stats[t.cnt]}）` : '' }}
      </view>
    </view>
    <view v-if="data && data.list.length === 0" class="farm-empty">
      暂无记录
    </view>
    <view v-for="r in data?.list" :key="r.id" class="farm-card">
      <view class="farm-row">
        <view class="left">
          <text class="farm-tag">
            {{ r.assetCode }}
          </text>
          <text class="farm-tag" :class="RECORD_STATUS[r.status]?.cls">
            {{ r.status === 10 ? '已发布' : RECORD_STATUS[r.status]?.text }}
          </text>
        </view>
        <text class="farm-t12">
          {{ fmtTime(r.createTime) }}
        </text>
      </view>
      <view class="farm-t15 mt12">
        {{ r.title }}
      </view>
      <view class="farm-t13 mt8">
        {{ r.content }}
      </view>
      <view v-if="r.photos?.length" class="photos mt12">
        <view v-for="(p, i) in r.photos.slice(0, 3)" :key="i" class="ph" @click="preview(r.photos, i)">
          <image :src="p" mode="aspectFill" />
        </view>
      </view>
      <view v-if="r.status === 20 && r.auditRemark" class="hint farm-red">
        驳回原因：{{ r.auditRemark }}
      </view>
      <view class="farm-row ops">
        <text class="farm-t12">
          {{ r.checks && r.checks.exifTime && r.checks.gpsFence && r.checks.duplicate ? '机器校验通过' : '机器校验有疑点' }}
        </text>
        <text class="farm-t13 farm-green">
          {{ r.status === 10 ? '认养人已可见' : r.status === 0 ? '等待审核' : '请重新打卡' }}
        </text>
      </view>
    </view>
    <DemoBadge />
  </view>
</template>

<script lang="ts" setup>
import type { StewardRecordList } from '@/api/farm/steward'
import { ref, watch } from 'vue'
import { getStewardRecords } from '@/api/farm/steward'
import { navigateBackPlus } from '@/utils'
import DemoBadge from '../components/demo-badge.vue'
import { fmtTime, RECORD_STATUS } from '../components/farm-utils'

definePage({ style: { navigationBarTitleText: '我的记录', navigationStyle: 'custom' } })

const TABS = [
  { k: 'all', text: '全部', cnt: 'all' },
  { k: 'pending', text: '待审核', cnt: 'pending' },
  { k: 'published', text: '已发布', cnt: 'published' },
  { k: 'rejected', text: '被驳回', cnt: 'rejected' },
] as const
const STATUS_OF = { all: undefined, pending: 0, published: 10, rejected: 20 } as const
const tab = ref<keyof typeof STATUS_OF>('all')
const data = ref<StewardRecordList>()

async function load() {
  try {
    data.value = await getStewardRecords(STATUS_OF[tab.value])
  } catch {
    data.value = undefined
  }
}
watch(tab, () => load())
onShow(() => load())

function preview(urls: string[], i: number) {
  uni.previewImage({ urls, current: i })
}
function goBack() {
  navigateBackPlus('/pages/index/index')
}
</script>

<style lang="scss" scoped>
@import '../components/farm-extra.scss';
</style>
