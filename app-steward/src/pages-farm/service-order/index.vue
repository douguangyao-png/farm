<template>
  <view class="farm-page">
    <wd-navbar title="代管工单" left-arrow placeholder safe-area-inset-top fixed @click-left="goBack" />

    <view class="tabs">
      <view class="tab" :class="{ on: scope === 'pool' }" @click="switchScope('pool')">
        工单池
      </view>
      <view class="tab" :class="{ on: scope === 'mine' }" @click="switchScope('mine')">
        我的
      </view>
    </view>

    <view class="farm-note">
      {{ scope === 'pool' ? '本基地认养人委托的代办，接单后会生成一条「代管工单」任务，出现在今日任务里。' : '你接过的工单。打卡完成后工单自动完成。' }}
    </view>

    <view v-if="loading && list.length === 0" class="farm-empty">
      加载中…
    </view>
    <view v-else-if="list.length === 0" class="farm-empty">
      {{ scope === 'pool' ? '工单池暂无待接单' : '还没有接过工单' }}
    </view>
    <view v-for="o in list" :key="o.id" class="farm-card order">
      <view class="farm-row">
        <view class="left">
          <text class="farm-tag" :class="SERVICE_ORDER_STATUS[o.status]?.cls">
            {{ SERVICE_ORDER_STATUS[o.status]?.text || o.status }}
          </text>
          <text class="farm-t15">
            {{ o.serviceType }}
          </text>
        </view>
        <text class="price">
          {{ fmtMoney(o.price) }}
        </text>
      </view>
      <view class="farm-t13 mt12">
        {{ o.assetCode }} · {{ o.memberName }} · {{ o.taskTitle }}
      </view>
      <view v-if="o.remark" class="farm-t12 mt8">
        备注：{{ o.remark }}
      </view>
      <view class="farm-t12 mt8">
        工单号 {{ o.no }} · {{ fmtTime(o.createTime) }}
      </view>
      <view class="split">
        分账（演示，未执行）：管家 {{ o.split?.steward ?? 70 }}% {{ fmtMoney(Math.round(o.price * (o.split?.steward ?? 70) / 100)) }}
        / 农场 {{ o.split?.farm ?? 20 }}% / 平台 {{ o.split?.platform ?? 10 }}%
      </view>
      <view class="farm-row ops">
        <text class="farm-t12">
          {{ o.status === 10 ? `接单于 ${fmtTime(o.acceptTime)}` : o.status === 20 ? `完成于 ${fmtTime(o.finishTime)}` : '' }}
        </text>
        <wd-button v-if="o.status === 0" type="primary" size="small" :loading="acceptingId === o.id" @click="accept(o)">
          接单
        </wd-button>
        <wd-button v-else-if="o.status === 10" type="primary" variant="plain" size="small" @click="gotoHome">
          去今日任务打卡
        </wd-button>
      </view>
    </view>
    <DemoBadge />
  </view>
</template>

<script lang="ts" setup>
import type { ServiceOrder } from '@/api/farm/steward'
import { useToast } from '@wot-ui/ui/components/wd-toast'
import { ref } from 'vue'
import { acceptServiceOrder, getServiceOrderList } from '@/api/farm/steward'
import { navigateBackPlus } from '@/utils'
import DemoBadge from '../components/demo-badge.vue'
import { fmtMoney, fmtTime, SERVICE_ORDER_STATUS } from '../components/farm-utils'

definePage({
  style: {
    navigationBarTitleText: '代管工单',
    navigationStyle: 'custom',
  },
})

const toast = useToast()
const scope = ref<'pool' | 'mine'>('pool')
const list = ref<ServiceOrder[]>([])
const loading = ref(false)
const acceptingId = ref(0)

async function load() {
  loading.value = true
  try {
    list.value = await getServiceOrderList(scope.value)
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function switchScope(s: 'pool' | 'mine') {
  if (scope.value !== s) {
    scope.value = s
    list.value = []
    load()
  }
}

onLoad((o) => {
  if (o?.scope === 'mine') {
    scope.value = 'mine'
  }
})
onShow(() => {
  load()
})

async function accept(o: ServiceOrder) {
  acceptingId.value = o.id
  try {
    await acceptServiceOrder(o.id)
    toast.success('接单成功，已加入今日任务')
    await load()
  } catch {
    // 已被接走等错误由请求层弹出接口 msg
    await load()
  } finally {
    acceptingId.value = 0
  }
}

function goBack() {
  navigateBackPlus('/pages/index/index')
}

function gotoHome() {
  uni.switchTab({ url: '/pages/index/index' })
}
</script>

<style lang="scss" scoped>
@import '../components/farm.scss';

.tabs {
  display: flex;
  background: #fff;
  .tab {
    flex: 1;
    padding: 22rpx 0;
    font-size: 28rpx;
    color: $tx2;
    text-align: center;
    border-bottom: 4rpx solid transparent;
    &.on {
      font-weight: 600;
      color: $gd;
      border-bottom-color: $g;
    }
  }
}
.order {
  .left {
    display: flex;
    align-items: center;
    gap: 14rpx;
  }
  .price {
    font-size: 32rpx;
    font-weight: 700;
    color: $o;
  }
  .split {
    margin-top: 12rpx;
    padding: 10rpx 16rpx;
    font-size: 22rpx;
    color: $tx2;
    background: #f6f7f9;
    border-radius: 10rpx;
  }
  .ops {
    margin-top: 16rpx;
    padding-top: 14rpx;
    border-top: 1rpx solid $line;
  }
}
.mt8 {
  margin-top: 8rpx;
}
.mt12 {
  margin-top: 12rpx;
}
</style>
