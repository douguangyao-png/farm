<template>
  <view class="farm-page">
    <wd-navbar title="配送确认" left-arrow placeholder safe-area-inset-top fixed @click-left="goBack" />
    <view class="farm-note">
      收成录入后进入待发货；发货需填写承运商和物流单号。这里只显示你负责的认养。
    </view>

    <view class="farm-card stat">
      <view class="ct">
        <view class="num farm-red">
          {{ data?.stats.pending ?? '-' }}
        </view>
        <view class="lbl">
          待发货
        </view>
      </view>
      <view class="ct">
        <view class="num farm-orange">
          {{ data?.stats.shipped ?? '-' }}
        </view>
        <view class="lbl">
          已发货
        </view>
      </view>
      <view class="ct">
        <view class="num farm-green">
          {{ data?.stats.done ?? '-' }}
        </view>
        <view class="lbl">
          已签收
        </view>
      </view>
    </view>

    <view class="tabs">
      <view v-for="t in TABS" :key="t.k" class="tab" :class="{ on: tab === t.k }" @click="tab = t.k">
        {{ t.text }}
      </view>
    </view>

    <view v-if="loading && !data" class="farm-empty">
      加载中…
    </view>

    <template v-if="tab === 'pending'">
      <view v-if="data && data.pending.length === 0" class="farm-empty">
        暂无待发货的收成
      </view>
      <view v-for="h in data?.pending" :key="h.harvestId" class="farm-card">
        <view class="farm-row">
          <view class="left">
            <text class="farm-tag st-red">
              待发货
            </text>
            <text class="farm-t15">
              {{ h.assetCode }} · {{ h.memberName }}
            </text>
          </view>
          <text class="farm-t12">
            {{ fmtDate(h.harvestDate) }}
          </text>
        </view>
        <view class="farm-t13 mt12">
          {{ h.crop }} · {{ h.weightJin }} 斤
        </view>
        <view v-if="h.remark" class="farm-t12 mt8">
          {{ h.remark }}
        </view>
        <view class="farm-row ops">
          <text class="farm-t12">
            发货前请确认打包清单与冷链
          </text>
          <wd-button type="primary" size="small" @click="openShip(h)">
            确认出库
          </wd-button>
        </view>
      </view>
    </template>

    <template v-else>
      <view v-if="data && data.list.length === 0" class="farm-empty">
        暂无{{ tab === 'shipped' ? '已发货' : '已签收' }}的配送单
      </view>
      <view v-for="s in data?.list" :key="s.id" class="farm-card">
        <view class="farm-row">
          <view class="left">
            <text class="farm-tag" :class="s.status === 10 ? 'st-orange' : 'st-green'">
              {{ s.status === 10 ? '已发货' : '已签收' }}
            </text>
            <text class="farm-t15">
              {{ s.no }}
            </text>
          </view>
          <text class="farm-t12">
            {{ fmtTime(s.createTime) }}
          </text>
        </view>
        <view class="farm-t13 mt12">
          {{ s.assetCode }} · {{ s.memberName }} · {{ s.weightJin }} 斤{{ s.coldChain ? ' · 冷链' : '' }}
        </view>
        <view class="farm-t12 mt8">
          {{ s.carrier }} {{ s.logisticsNo }}
        </view>
        <view class="farm-t12 mt8">
          收件：{{ s.receiverName }} {{ s.receiverMobile }} {{ s.address }}
        </view>
        <view v-if="s.tracks.length" class="farm-t12 mt8">
          最新：{{ s.tracks[s.tracks.length - 1].content }}
        </view>
        <view v-if="s.status === 10" class="farm-row ops">
          <text class="farm-t12">
            自提或当面交付时在此核销
          </text>
          <wd-button type="primary" variant="plain" size="small" :loading="busyId === s.id" @click="sign({ id: s.id })">
            确认签收
          </wd-button>
        </view>
      </view>
    </template>

    <view class="farm-card">
      <view class="farm-t15">
        自提核销
      </view>
      <view class="farm-t12 mt8">
        扫认养人出示的配送单二维码，或输入配送单号。
      </view>
      <view class="scan-row">
        <input v-model="scanNo" class="inp" placeholder="配送单号">
        <wd-button size="small" type="primary" :disabled="!scanNo.trim()" @click="sign({ no: scanNo.trim() })">
          核销
        </wd-button>
        <wd-button size="small" variant="plain" @click="scan">
          扫码
        </wd-button>
      </view>
    </view>

    <wd-popup v-model="shipShow" position="bottom" custom-style="border-radius:24rpx 24rpx 0 0;padding:32rpx">
      <view class="farm-t15">
        确认出库 · {{ cur?.assetCode }} {{ cur?.crop }} {{ cur?.weightJin }} 斤
      </view>
      <view class="form">
        <input v-model="carrier" class="inp" placeholder="承运商（必填）">
        <input v-model="logisticsNo" class="inp" placeholder="物流单号（必填）">
        <view class="farm-row">
          <text class="farm-t13">
            冷链配送
          </text>
          <wd-switch v-model="coldChain" size="44rpx" />
        </view>
      </view>
      <wd-button block type="primary" :loading="shipping" @click="doShip">
        确认并发货
      </wd-button>
    </wd-popup>
    <DemoBadge />
  </view>
</template>

<script lang="ts" setup>
import type { PendingHarvest, StewardShipmentList } from '@/api/farm/steward'
import { useToast } from '@wot-ui/ui/components/wd-toast'
import { ref, watch } from 'vue'
import { createStewardShipment, getStewardShipments, signStewardShipment } from '@/api/farm/steward'
import { navigateBackPlus } from '@/utils'
import DemoBadge from '../components/demo-badge.vue'
import { fmtDate, fmtTime } from '../components/farm-utils'

definePage({ style: { navigationBarTitleText: '配送确认', navigationStyle: 'custom' } })

const TABS = [{ k: 'pending', text: '待发货' }, { k: 'shipped', text: '已发货' }, { k: 'done', text: '已签收' }] as const
const toast = useToast()
const tab = ref<'pending' | 'shipped' | 'done'>('pending')
const data = ref<StewardShipmentList>()
const loading = ref(false)
const busyId = ref(0)
const scanNo = ref('')

const shipShow = ref(false)
const cur = ref<PendingHarvest>()
const carrier = ref('')
const logisticsNo = ref('')
const coldChain = ref(true)
const shipping = ref(false)

async function load() {
  loading.value = true
  try {
    data.value = await getStewardShipments(tab.value === 'pending' ? 0 : tab.value === 'shipped' ? 10 : 20)
  } catch {
    data.value = undefined
  } finally {
    loading.value = false
  }
}
watch(tab, () => load())
onShow(() => load())

function openShip(h: PendingHarvest) {
  cur.value = h
  carrier.value = ''
  logisticsNo.value = ''
  coldChain.value = true
  shipShow.value = true
}
async function doShip() {
  if (!cur.value) {
    return
  }
  if (!carrier.value.trim() || !logisticsNo.value.trim()) {
    toast.warning('请填写承运商和物流单号')
    return
  }
  shipping.value = true
  try {
    await createStewardShipment({ harvestId: cur.value.harvestId, carrier: carrier.value.trim(), logisticsNo: logisticsNo.value.trim(), coldChain: coldChain.value })
    shipShow.value = false
    toast.success('已出库发货')
    await load()
  } catch {
    // 错误提示由请求层弹出
  } finally {
    shipping.value = false
  }
}
async function sign(p: { id?: number, no?: string }) {
  busyId.value = p.id ?? -1
  try {
    await signStewardShipment(p)
    toast.success('已核销签收')
    scanNo.value = ''
    await load()
  } catch {
    // 错误提示由请求层弹出
  } finally {
    busyId.value = 0
  }
}
function scan() {
  uni.scanCode({
    success: (r) => {
      scanNo.value = String(r.result || '').trim()
    },
    fail: () => toast.warning('当前环境不能扫码，请手动输入单号'),
  })
}
function goBack() {
  navigateBackPlus('/pages/index/index')
}
</script>

<style lang="scss" scoped>
@import '../components/farm-extra.scss';

.scan-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 16rpx;
}
</style>
