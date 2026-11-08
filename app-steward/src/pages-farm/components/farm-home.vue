<template>
  <view class="farm-page farm-home">
    <!-- 问候 -->
    <view class="hero">
      <view class="hello">
        {{ hello }}，{{ home?.stewardName || userName }}
      </view>
      <view class="sub">
        {{ home?.baseName || '—' }} · {{ today }}
      </view>
    </view>

    <!-- 计数 -->
    <view class="farm-card counters">
      <view class="ct" @click="tab = 0">
        <view class="num">
          {{ home?.todo.taskCount ?? '-' }}
        </view>
        <view class="lbl">
          待办任务
        </view>
      </view>
      <view class="ct" @click="tab = 0">
        <view class="num" :class="{ 'farm-red': (home?.todo.overdueCount ?? 0) > 0 }">
          {{ home?.todo.overdueCount ?? 0 }}
        </view>
        <view class="lbl">
          已逾期
        </view>
      </view>
      <view class="ct" @click="gotoOrders('pool')">
        <view class="num" :class="{ orange: (home?.todo.serviceOrderPending ?? 0) > 0 }">
          {{ home?.todo.serviceOrderPending ?? '-' }}
        </view>
        <view class="lbl">
          工单池待接
        </view>
      </view>
      <view class="ct" @click="gotoOrders('mine')">
        <view class="num">
          {{ home?.todo.serviceOrderMine ?? '-' }}
        </view>
        <view class="lbl">
          我的工单
        </view>
      </view>
      <view class="ct" @click="tab = 1">
        <view class="num farm-green">
          {{ home?.todayDone ?? '-' }}
        </view>
        <view class="lbl">
          今日完成
        </view>
      </view>
      <view class="ct">
        <view class="num">
          {{ home?.pendingAuditCount ?? 0 }}<text class="slash">
            /
          </text><text :class="{ 'farm-red': (home?.rejectedCount ?? 0) > 0 }">
            {{ home?.rejectedCount ?? 0 }}
          </text>
        </view>
        <view class="lbl">
          待审核 / 被驳回
        </view>
      </view>
    </view>

    <!-- 代管工单入口 -->
    <view class="farm-card entry" @click="gotoOrders('pool')">
      <view>
        <view class="farm-t15">
          代管工单
        </view>
        <view class="farm-t12">
          {{ (home?.todo.serviceOrderPending ?? 0) > 0 ? `${home?.todo.serviceOrderPending} 单待接单` : '工单池暂无待接单' }}
          · 认养人委托的农事代办
        </view>
      </view>
      <wd-icon name="arrow-right" size="32rpx" color="#98a0aa" />
    </view>

    <!-- 第二期入口：原型「我的」里的工具与首页入口卡 -->
    <view class="farm-card quick-grid">
      <view v-for="e in QUICK" :key="e.text" class="qg" @click="goto(e.url)">
        <view class="ic">
          {{ e.ic }}
        </view>
        <view class="farm-t12 lb">
          {{ e.text }}
        </view>
      </view>
    </view>

    <!-- 任务列表 -->
    <view class="farm-hd">
      <text>今日任务</text>
      <view class="seg">
        <view class="seg-item" :class="{ on: tab === 0 }" @click="tab = 0">
          待办
        </view>
        <view class="seg-item" :class="{ on: tab === 1 }" @click="tab = 1">
          已完成
        </view>
      </view>
    </view>

    <view v-if="loading && list.length === 0" class="farm-empty">
      加载中…
    </view>
    <view v-else-if="list.length === 0" class="farm-empty">
      {{ tab === 0 ? '没有待办任务' : '还没有完成的任务' }}
    </view>
    <view
      v-for="t in list"
      :key="t.id"
      class="farm-card task"
      :class="{ overdue: t.status === 0 && isOverdue(t) }"
      @click="gotoTask(t.id)"
    >
      <view class="farm-row">
        <view class="left">
          <text class="farm-tag" :class="SOURCE_META[t.source]?.cls">
            {{ SOURCE_META[t.source]?.text || t.source }}
          </text>
          <text class="farm-t15 title">
            {{ t.title }}
          </text>
        </view>
      </view>
      <view class="farm-t13 desc">
        {{ t.description }}
      </view>
      <view class="farm-row meta">
        <text class="farm-t12">
          {{ t.memberName || '—' }}{{ t.careMode ? ` · ${CARE_NAME[t.careMode]}` : '' }}
        </text>
        <text v-if="t.status === 0" class="farm-t12 dl" :class="{ 'farm-red': isOverdue(t) }">
          {{ fmtDeadline(t.deadline) }}
        </text>
        <text v-else class="farm-t12 farm-green">
          完成于 {{ fmtTime(t.finishTime) }}
        </text>
      </view>
    </view>
    <DemoBadge />
  </view>
</template>

<script lang="ts" setup>
import type { StewardHome, StewardTask } from '@/api/farm/steward'
import { storeToRefs } from 'pinia'
import { computed, onMounted, ref, watch } from 'vue'
import { getStewardHome, getStewardTaskList } from '@/api/farm/steward'
import { useUserStore } from '@/store'
import DemoBadge from './demo-badge.vue'
import { fmtDate, fmtDeadline, fmtTime, greeting, SOURCE_META } from './farm-utils'

defineOptions({ name: 'FarmHome' })

const QUICK = [
  { text: '配送确认', ic: '送', url: '/pages-farm/delivery/index' },
  { text: '农资代办', ic: '资', url: '/pages-farm/supply-task/index' },
  { text: '自管指导', ic: '导', url: '/pages-farm/self-guide/index' },
  { text: '我的记录', ic: '记', url: '/pages-farm/record/index' },
  { text: '标的管理', ic: '地', url: '/pages/farm-land/index' },
  { text: '异常上报', ic: '警', url: '/pages-farm/alarm/index' },
  { text: '客户消息', ic: '信', url: '/pages/farm-msg/index' },
  { text: '绩效', ic: '绩', url: '/pages-farm/mine/index' },
]

const CARE_NAME: Record<string, string> = { full: '全托管', semi: '半托管', self: '不托管' }

const { userInfo } = storeToRefs(useUserStore())
const userName = computed(() => (userInfo.value as any)?.nickname || '')
const hello = greeting()
const today = fmtDate(Date.now())

const home = ref<StewardHome>()
const tasks = ref<StewardTask[]>([])
const tab = ref(0) // 0 待办 1 已完成
const loading = ref(false)

/** 待办按截止时间升序（逾期的在最前），已完成按完成时间倒序 */
const list = computed(() => tasks.value.slice().sort((a, b) =>
  tab.value === 0 ? a.deadline - b.deadline : (b.finishTime ?? 0) - (a.finishTime ?? 0)))

function isOverdue(t: StewardTask) {
  return t.overdue ?? t.deadline < Date.now()
}

async function loadTasks() {
  loading.value = true
  try {
    tasks.value = await getStewardTaskList(tab.value === 0 ? 0 : 10)
  } finally {
    loading.value = false
  }
}

async function refresh() {
  getStewardHome().then((d) => {
    home.value = d
  }).catch(() => {})
  await loadTasks().catch(() => {})
}

watch(tab, () => {
  tasks.value = []
  loadTasks().catch(() => {})
})

function gotoTask(id: number) {
  uni.navigateTo({ url: `/pages-farm/task/detail?id=${id}` })
}

function goto(url: string) {
  if (['/pages/index/index', '/pages/farm-land/index', '/pages/farm-msg/index', '/pages/user/index'].includes(url)) uni.switchTab({ url })
  else uni.navigateTo({ url })
}

function gotoOrders(scope: 'pool' | 'mine') {
  uni.navigateTo({ url: `/pages-farm/service-order/index?scope=${scope}` })
}

onMounted(() => {
  refresh()
})

defineExpose({ refresh })
</script>

<style lang="scss" scoped>
@import './farm.scss';

.farm-home {
  padding-bottom: 160rpx;
}
.hero {
  padding: 48rpx 32rpx 80rpx;
  color: #fff;
  background: linear-gradient(135deg, #22a06b, #17885a);
  .hello {
    font-size: 40rpx;
    font-weight: 600;
  }
  .sub {
    margin-top: 10rpx;
    font-size: 26rpx;
    opacity: 0.85;
  }
}
.counters {
  display: flex;
  flex-wrap: wrap;
  margin-top: -56rpx;
  padding: 16rpx 0;
  .ct {
    width: 33.33%;
    padding: 16rpx 0;
    text-align: center;
    .num {
      font-size: 40rpx;
      font-weight: 700;
      &.orange {
        color: $o;
      }
    }
    .slash {
      margin: 0 6rpx;
      font-size: 28rpx;
      color: $tx3;
    }
    .lbl {
      margin-top: 4rpx;
      font-size: 22rpx;
      color: $tx3;
    }
  }
}
.entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.seg {
  display: flex;
  padding: 4rpx;
  font-size: 24rpx;
  font-weight: 400;
  background: #e9ebee;
  border-radius: 12rpx;
  .seg-item {
    padding: 6rpx 22rpx;
    color: $tx2;
    border-radius: 10rpx;
    &.on {
      color: $gd;
      font-weight: 600;
      background: #fff;
    }
  }
}
.task {
  &.overdue {
    border-left: 8rpx solid $r;
  }
  .left {
    display: flex;
    align-items: center;
    gap: 14rpx;
  }
  .desc {
    margin-top: 10rpx;
    line-height: 1.5;
  }
  .meta {
    margin-top: 14rpx;
    padding-top: 14rpx;
    border-top: 1rpx solid $line;
  }
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 28rpx 0;
  .qg {
    text-align: center;
  }
  .ic {
    width: 76rpx;
    height: 76rpx;
    margin: 0 auto 8rpx;
    font-size: 30rpx;
    line-height: 76rpx;
    color: $gd;
    background: $gl;
    border-radius: 50%;
  }
  .lb {
    color: $tx2;
  }
}
</style>
