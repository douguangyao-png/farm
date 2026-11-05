<template>
  <view class="farm-page">
    <wd-navbar title="任务详情" left-arrow placeholder safe-area-inset-top fixed @click-left="goBack" />

    <view v-if="!task" class="farm-empty">
      {{ loadError || '加载中…' }}
    </view>
    <template v-else>
      <view class="farm-card">
        <view class="farm-row">
          <text class="farm-tag" :class="SOURCE_META[task.source]?.cls">
            {{ SOURCE_META[task.source]?.text || task.source }}
          </text>
          <text class="farm-tag" :class="statusCls">
            {{ statusText }}
          </text>
        </view>
        <view class="farm-t15 title">
          {{ task.title }}
        </view>
        <view class="farm-t13 desc">
          {{ task.description }}
        </view>
        <view class="farm-kv">
          <text class="k">截止时间</text>
          <text class="v" :class="{ 'farm-red': overdue }">
            {{ fmtTime(task.deadline) }}{{ overdue ? `（${fmtDeadline(task.deadline)}）` : '' }}
          </text>
        </view>
        <view v-if="task.status === 10" class="farm-kv">
          <text class="k">完成时间</text>
          <text class="v farm-green">{{ fmtTime(task.finishTime) }}</text>
        </view>
        <view class="farm-kv">
          <text class="k">认养人</text>
          <text class="v">{{ task.memberName || '—' }}{{ task.careMode ? ` · ${CARE_NAME[task.careMode]}` : '' }}</text>
        </view>
        <view v-if="task.serviceOrderId" class="farm-kv">
          <text class="k">来源</text>
          <text class="v">认养人委托的代管工单</text>
        </view>
      </view>

      <view class="farm-hd">
        <text>标的</text>
        <text class="more" @click="gotoAsset">标的详情 ›</text>
      </view>
      <view class="farm-card" @click="gotoAsset">
        <view class="farm-kv">
          <text class="k">编号</text>
          <text class="v">{{ task.asset.code }} {{ task.asset.name }}</text>
        </view>
        <view class="farm-kv">
          <text class="k">作物 / 品种</text>
          <text class="v">{{ task.asset.crop || '—' }}</text>
        </view>
        <view class="farm-kv">
          <text class="k">面积</text>
          <text class="v">{{ task.asset.areaM2 != null ? `${task.asset.areaM2} ㎡` : '—' }}</text>
        </view>
        <view class="farm-kv">
          <text class="k">基地</text>
          <text class="v">{{ task.baseName }}</text>
        </view>
      </view>

      <view class="farm-hd">
        <text>当前环境</text>
        <text class="farm-t12">演示数据 · {{ fmtTime(task.env?.updateTime || Date.now()) }}</text>
      </view>
      <view class="farm-card">
        <EnvSnapshot :env="task.env" />
      </view>

      <view v-if="task.status === 0" class="farm-footbar">
        <wd-button type="primary" block custom-class="btn-flex" @click="gotoCheckin">
          去打卡
        </wd-button>
      </view>
    </template>
    <DemoBadge />
  </view>
</template>

<script lang="ts" setup>
import type { StewardTaskDetail } from '@/api/farm/steward'
import { computed, ref } from 'vue'
import { getStewardTask } from '@/api/farm/steward'
import { navigateBackPlus } from '@/utils'
import DemoBadge from '../components/demo-badge.vue'
import EnvSnapshot from '../components/env-snapshot.vue'
import { fmtDeadline, fmtTime, SOURCE_META } from '../components/farm-utils'

definePage({
  style: {
    navigationBarTitleText: '任务详情',
    navigationStyle: 'custom',
  },
})

const CARE_NAME: Record<string, string> = { full: '全托管', semi: '半托管', self: '不托管' }

const id = ref(0)
const task = ref<StewardTaskDetail>()
const loadError = ref('')

const overdue = computed(() => !!task.value && task.value.status === 0 && task.value.deadline < Date.now())
const statusText = computed(() => {
  const s = task.value?.status
  if (s === 10) {
    return '已完成'
  }
  if (s === 20) {
    return '已委托代办'
  }
  return overdue.value ? '已逾期' : '待办'
})
const statusCls = computed(() => {
  const s = task.value?.status
  if (s === 10) {
    return 'st-green'
  }
  return overdue.value ? 'st-red' : 'st-orange'
})

async function load() {
  if (!id.value) {
    loadError.value = '缺少任务编号'
    return
  }
  try {
    task.value = await getStewardTask(id.value)
  } catch (e: any) {
    loadError.value = e?.msg || '加载失败'
  }
}

onLoad((options) => {
  id.value = Number(options?.id || 0)
})
onShow(() => {
  load()
})

function goBack() {
  navigateBackPlus('/pages/index/index')
}

function gotoCheckin() {
  uni.navigateTo({ url: `/pages-farm/task/checkin?id=${id.value}` })
}

function gotoAsset() {
  if (task.value) {
    uni.navigateTo({ url: `/pages-farm/asset/detail?id=${task.value.asset.id}` })
  }
}
</script>

<style lang="scss" scoped>
@import '../components/farm.scss';

.title {
  margin-top: 16rpx;
}
.desc {
  margin: 10rpx 0 12rpx;
  line-height: 1.6;
}
.more {
  font-size: 24rpx;
  font-weight: 400;
  color: $gd;
}
:deep(.btn-flex) {
  flex: 1;
}
</style>
