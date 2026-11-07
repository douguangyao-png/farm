<template>
  <view class="farm-page">
    <wd-navbar title="农资代办" left-arrow placeholder safe-area-inset-top fixed @click-left="goBack" />
    <view class="tabs">
      <view v-for="t in TABS" :key="t.k" class="tab" :class="{ on: tab === t.k }" @click="tab = t.k">
        {{ t.text }}{{ data ? `（${data.counts[t.k]}）` : '' }}
      </view>
    </view>
    <view class="farm-note">
      认养人自购农资到基地后，由你代收 / 寄存 / 代施；批次号必须登记，否则该地块溯源档案无法出具报告。
    </view>
    <view v-if="data && data.list.length === 0" class="farm-empty">
      暂无{{ tab === 'todo' ? '待处理' : '已完成' }}的农资代办
    </view>
    <view v-for="x in data?.list" :key="x.id" class="farm-card">
      <view class="farm-row">
        <view class="left">
          <text class="farm-tag" :class="x.done ? '' : 'st-orange'">
            {{ x.kind }}
          </text>
          <text class="farm-t15">
            {{ x.assetCode }} · {{ x.memberName }}
          </text>
        </view>
        <text class="farm-tag" :class="x.careModeName === '不托管' ? 'st-red' : ''">
          {{ x.careModeName }}
        </text>
      </view>
      <view class="farm-t13 mt12">
        {{ x.supplyName }}
      </view>
      <view class="farm-t12 mt8">
        批次 {{ x.batchNo }} · 单号 {{ x.no }}
      </view>
      <view v-if="!x.done && x.hint" class="hint">
        {{ x.hint }}
      </view>
      <view v-if="x.done" class="farm-t12 farm-green mt8">
        已写入投入品台账 · {{ fmtDate(x.usedAt) }} · 用量 {{ x.dosage }}
      </view>
      <view v-else class="farm-row ops start">
        <wd-button type="primary" size="small" @click="open(x)">
          登记施用 / 安装
        </wd-button>
        <wd-button variant="plain" size="small" @click="scan">
          扫码核收
        </wd-button>
      </view>
    </view>

    <wd-popup v-model="show" position="bottom" custom-style="border-radius:24rpx 24rpx 0 0;padding:32rpx">
      <view class="farm-t15">
        登记 · {{ cur?.supplyName }}
      </view>
      <view class="farm-t12 mt8">
        批次 {{ cur?.batchNo }}，登记后写入投入品台账
      </view>
      <view class="form">
        <input v-model="dosage" class="inp" placeholder="实际用量，如 2 袋 / 20 kg（必填）">
      </view>
      <wd-button block type="primary" :loading="saving" @click="save">
        确认登记
      </wd-button>
    </wd-popup>
    <DemoBadge />
  </view>
</template>

<script lang="ts" setup>
import type { SupplyTask } from '@/api/farm/steward'
import { useToast } from '@wot-ui/ui/components/wd-toast'
import { ref, watch } from 'vue'
import { applySupplyTask, getSupplyTasks } from '@/api/farm/steward'
import { navigateBackPlus } from '@/utils'
import DemoBadge from '../components/demo-badge.vue'
import { fmtDate } from '../components/farm-utils'

definePage({ style: { navigationBarTitleText: '农资代办', navigationStyle: 'custom' } })

const TABS = [{ k: 'todo', text: '待处理' }, { k: 'done', text: '已完成' }] as const
const toast = useToast()
const tab = ref<'todo' | 'done'>('todo')
const data = ref<{ counts: { todo: number, done: number }, list: SupplyTask[] }>()
const show = ref(false)
const cur = ref<SupplyTask>()
const dosage = ref('')
const saving = ref(false)

async function load() {
  try {
    data.value = await getSupplyTasks(tab.value)
  } catch {
    data.value = undefined
  }
}
watch(tab, () => load())
onShow(() => load())

function open(x: SupplyTask) {
  cur.value = x
  dosage.value = ''
  show.value = true
}
async function save() {
  if (!cur.value || !dosage.value.trim()) {
    toast.warning('请填写用量')
    return
  }
  saving.value = true
  try {
    await applySupplyTask({ id: cur.value.id, dosage: dosage.value.trim() })
    show.value = false
    toast.success('已登记')
  } catch {
    // 错误提示由请求层弹出
  } finally {
    saving.value = false
    await load()
  }
}
function scan() {
  uni.scanCode({
    success: () => toast.success('核收成功（演示：未校验批次二维码）'),
    fail: () => toast.warning('当前环境不能扫码'),
  })
}
function goBack() {
  navigateBackPlus('/pages/index/index')
}
</script>

<style lang="scss" scoped>
@import '../components/farm-extra.scss';
</style>
