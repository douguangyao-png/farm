<template>
  <view class="farm-page">
    <wd-navbar title="异常上报" left-arrow placeholder safe-area-inset-top fixed @click-left="goBack" />
    <view class="farm-note warn">
      损耗与灾害必须在 24 小时内登记并留影像，否则保险可能拒赔。
    </view>

    <view class="farm-hd">
      <text>快速上报</text>
    </view>
    <view class="quick">
      <view v-for="t in TYPES" :key="t" class="q" @click="open(t)">
        {{ t }}
      </view>
    </view>

    <view class="farm-hd">
      <text>异常列表</text>
    </view>
    <view v-if="list.length === 0" class="farm-empty">
      暂无上报记录
    </view>
    <view v-for="a in list" :key="a.id" class="farm-card">
      <view class="farm-row">
        <text class="farm-t15">
          {{ a.title }}
        </text>
        <text class="farm-tag" :class="a.level === '高' ? 'st-red' : ''">
          {{ a.level }}
        </text>
      </view>
      <view class="farm-t13 mt8">
        {{ a.content }}{{ a.qty ? `（数量 ${a.qty}）` : '' }}
      </view>
      <view v-if="a.photos.length" class="photos mt12">
        <view v-for="(p, i) in a.photos.slice(0, 3)" :key="i" class="ph">
          <image :src="p" mode="aspectFill" />
        </view>
      </view>
      <view class="farm-row ops">
        <text class="farm-t12">
          {{ fmtTime(a.createTime) }}
        </text>
        <text v-if="a.insuranceReported" class="farm-tag st-green">
          已自动报备保险（演示）
        </text>
      </view>
    </view>

    <wd-popup v-model="show" position="bottom" custom-style="border-radius:24rpx 24rpx 0 0;padding:32rpx;max-height:80vh;overflow:auto">
      <view class="farm-t15">
        {{ type }}
      </view>
      <view class="form">
        <picker :range="assetNames" @change="onPick">
          <view class="inp">
            {{ assetIdx >= 0 ? assetNames[assetIdx] : '选择标的（可不选）' }}
          </view>
        </picker>
        <input v-if="type === '损耗登记'" v-model="qty" class="inp" type="number" placeholder="损耗数量（必填）">
        <textarea v-model="content" class="inp area" placeholder="情况说明与处置（必填）" />
        <view class="farm-t12">
          现场影像{{ type === '设备异常' ? '（选填）' : '（必填）' }} {{ photos.length }} / 9
        </view>
        <view class="photos">
          <view v-for="(p, i) in photos" :key="i" class="ph" @click="photos.splice(i, 1)">
            <image :src="p" mode="aspectFill" />
          </view>
          <view v-if="photos.length < 9" class="ph add" @click="choose">
            +
          </view>
        </view>
        <view v-if="showDemoBadge && photos.length < 9" class="farm-t12 farm-green" @click="photos.push(makeDemoPhoto(type))">
          使用演示照片
        </view>
      </view>
      <wd-button block type="primary" :loading="saving" @click="submit">
        提交上报
      </wd-button>
    </wd-popup>
    <DemoBadge />
  </view>
</template>

<script lang="ts" setup>
import type { StewardAlarm, StewardAssetItem } from '@/api/farm/steward'
import { useToast } from '@wot-ui/ui/components/wd-toast'
import { computed, ref } from 'vue'
import { createStewardAlarm, getStewardAlarms, getStewardAssets } from '@/api/farm/steward'
import { navigateBackPlus } from '@/utils'
import DemoBadge from '../components/demo-badge.vue'
import { compressImage, fmtTime, makeDemoPhoto, showDemoBadge } from '../components/farm-utils'

definePage({ style: { navigationBarTitleText: '异常上报', navigationStyle: 'custom' } })

const TYPES = ['设备异常', '损耗登记', '气象灾害', '病虫害爆发']
const toast = useToast()
const list = ref<StewardAlarm[]>([])
const assets = ref<StewardAssetItem[]>([])
const show = ref(false)
const type = ref(TYPES[0])
const assetIdx = ref(-1)
const qty = ref('')
const content = ref('')
const photos = ref<string[]>([])
const saving = ref(false)
const assetNames = computed(() => assets.value.map(a => `${a.code} ${a.name}`))

async function load() {
  list.value = await getStewardAlarms().catch(() => [])
}
onShow(() => {
  load()
  getStewardAssets('all').then((d) => {
    assets.value = d.list
  }).catch(() => {})
})

function open(t: string) {
  type.value = t
  assetIdx.value = -1
  qty.value = ''
  content.value = ''
  photos.value = []
  show.value = true
}
function onPick(e: any) {
  assetIdx.value = Number(e.detail.value)
}
function choose() {
  uni.chooseImage({
    count: 9 - photos.value.length,
    success: async (r) => {
      for (const p of r.tempFilePaths as string[]) {
        if (photos.value.length < 9) {
          photos.value.push(await compressImage(p))
        }
      }
    },
  })
}
async function submit() {
  if (!content.value.trim()) {
    toast.warning('请填写情况说明')
    return
  }
  saving.value = true
  try {
    await createStewardAlarm({
      type: type.value,
      assetId: assetIdx.value >= 0 ? assets.value[assetIdx.value].id : undefined,
      content: content.value.trim(),
      qty: type.value === '损耗登记' ? Number(qty.value) : undefined,
      photos: photos.value,
    })
    show.value = false
    toast.success('已上报')
    await load()
  } catch {
    // 错误提示由请求层弹出
  } finally {
    saving.value = false
  }
}
function goBack() {
  navigateBackPlus('/pages/index/index')
}
</script>

<style lang="scss" scoped>
@import '../components/farm-extra.scss';

.quick {
  display: flex;
  gap: 16rpx;
  padding: 16rpx 24rpx 0;
  .q {
    flex: 1;
    padding: 28rpx 0;
    font-size: 26rpx;
    color: $gd;
    text-align: center;
    background: #fff;
    border-radius: 16rpx;
  }
}
.area {
  height: 160rpx;
}
</style>
