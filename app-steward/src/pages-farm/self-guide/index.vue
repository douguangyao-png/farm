<template>
  <view class="farm-page">
    <wd-navbar title="自管认养人指导" left-arrow placeholder safe-area-inset-top fixed @click-left="goBack" />
    <view class="farm-card stat">
      <view v-for="s in statCells" :key="s.lbl" class="ct">
        <view class="num" :class="s.cls">
          {{ s.num }}
        </view>
        <view class="lbl">
          {{ s.lbl }}
        </view>
      </view>
    </view>
    <view class="farm-note">
      半托管 / 不托管地块的日常农事由认养人自己完成；你的职责是提醒、指导与兜底，不代替执行（除非认养人委托代办）。
    </view>
    <view class="tabs">
      <view v-for="t in TABS" :key="t.k" class="tab" :class="{ on: tab === t.k }" @click="tab = t.k">
        {{ t.text }}{{ data ? `（${data.counts[t.k]}）` : '' }}
      </view>
    </view>
    <view v-if="data && data.list.length === 0" class="farm-empty">
      暂无
    </view>
    <view v-for="x in data?.list" :key="x.taskId" class="farm-card">
      <view class="farm-row">
        <view class="left">
          <text class="farm-tag" :class="x.state === 'over' ? 'st-red' : ''">
            {{ x.careModeName }}
          </text>
          <text class="farm-t15">
            {{ x.assetCode }} · {{ x.memberName }}
          </text>
        </view>
        <text class="farm-t12" :class="{ 'farm-red': x.state === 'over' }">
          {{ fmtDeadline(x.deadline) }}
        </text>
      </view>
      <view class="farm-t15 mt12">
        {{ x.title }}
      </view>
      <view class="hint">
        {{ x.description }}
      </view>
      <view v-if="x.remindedAt" class="farm-t12 mt8">
        已于 {{ fmtTime(x.remindedAt) }} 提醒
      </view>
      <view class="farm-row ops start">
        <wd-button v-if="x.state === 'over'" type="primary" size="small" :loading="busy === x.taskId" @click="remind(x, true)">
          推送委托代办 {{ fmtMoney(x.entrustPrice ?? 6000) }}
        </wd-button>
        <wd-button v-else type="primary" size="small" :loading="busy === x.taskId" @click="remind(x, false)">
          {{ x.state === 'reminded' ? '再提醒一次' : '发送提醒' }}
        </wd-button>
        <wd-button variant="plain" size="small" @click="chat(x)">
          {{ x.state === 'over' ? '催一下' : '去指导' }}
        </wd-button>
      </view>
    </view>
    <DemoBadge />
  </view>
</template>

<script lang="ts" setup>
import type { SelfGuide, SelfGuideItem } from '@/api/farm/steward'
import { useToast } from '@wot-ui/ui/components/wd-toast'
import { computed, ref, watch } from 'vue'
import { getSelfGuide, remindSelfTask } from '@/api/farm/steward'
import { navigateBackPlus } from '@/utils'
import DemoBadge from '../components/demo-badge.vue'
import { fmtDeadline, fmtMoney, fmtTime } from '../components/farm-utils'

definePage({ style: { navigationBarTitleText: '自管指导', navigationStyle: 'custom' } })

const TABS = [{ k: 'todo', text: '需提醒' }, { k: 'reminded', text: '已提醒' }, { k: 'over', text: '已逾期' }] as const
const toast = useToast()
const tab = ref<'todo' | 'reminded' | 'over'>('todo')
const data = ref<SelfGuide>()
const busy = ref(0)

const statCells = computed(() => {
  const s = data.value?.stats
  return [
    { lbl: '自管地块', num: s ? `${s.plots} 块` : '-', cls: '' },
    { lbl: '本周待办', num: s ? `${s.weekTodo} 项` : '-', cls: '' },
    { lbl: '已提醒', num: s ? `${s.reminded} 项` : '-', cls: 'farm-green' },
    { lbl: '逾期', num: s ? `${s.overdue} 项` : '-', cls: s && s.overdue > 0 ? 'farm-red' : '' },
  ]
})

async function load() {
  try {
    data.value = await getSelfGuide(tab.value)
  } catch {
    data.value = undefined
  }
}
watch(tab, () => load())
onShow(() => load())

async function remind(x: SelfGuideItem, entrust: boolean) {
  busy.value = x.taskId
  try {
    await remindSelfTask(x.taskId, entrust)
    toast.success(entrust ? '已推送委托代办' : '已发送提醒')
  } catch {
    // 错误提示由请求层弹出
  } finally {
    busy.value = 0
    await load()
  }
}
function chat(x: SelfGuideItem) {
  uni.navigateTo({ url: `/pages-farm/message/chat?memberId=${x.memberId}` })
}
function goBack() {
  navigateBackPlus('/pages/index/index')
}
</script>

<style lang="scss" scoped>
@import '../components/farm-extra.scss';
</style>
