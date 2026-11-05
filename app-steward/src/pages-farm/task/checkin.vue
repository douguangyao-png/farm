<template>
  <view class="farm-page">
    <wd-navbar title="农事打卡" left-arrow placeholder safe-area-inset-top fixed @click-left="goBack" />

    <view v-if="!task" class="farm-empty">
      {{ loadError || '加载中…' }}
    </view>
    <template v-else>
      <view class="farm-card">
        <view class="farm-t15">
          {{ task.title }}
        </view>
        <view class="farm-t12 mt8">
          {{ task.asset.code }} {{ task.asset.name }} · {{ task.memberName || '—' }}
        </view>
      </view>

      <view class="farm-hd">
        <text>作业内容</text>
      </view>
      <view class="farm-card">
        <wd-textarea
          v-model="content"
          :placeholder="`说说做了什么，例如：${task.description}`"
          :maxlength="300"
          show-word-limit
          :rows="4"
        />
      </view>

      <view class="farm-hd">
        <text>现场照片 <text class="req">*</text></text>
        <text class="farm-t12">{{ photos.length }} / 9</text>
      </view>
      <view class="farm-card">
        <view class="photos">
          <view v-for="(p, i) in photos" :key="i" class="ph">
            <image :src="p" mode="aspectFill" class="img" @click="preview(i)" />
            <view class="del" @click.stop="removePhoto(i)">
              ×
            </view>
          </view>
          <view v-if="photos.length < 9" class="ph add" @click="choosePhoto">
            <wd-icon name="camera" size="48rpx" color="#98a0aa" />
            <text class="farm-t12">
              {{ compressing ? '处理中…' : '拍照 / 相册' }}
            </text>
          </view>
        </view>
        <view class="farm-t12 mt8">
          至少 1 张，最多 9 张；照片会压缩到长边 1280 像素以内
        </view>
        <view v-if="showDemoBadge && photos.length < 9" class="demo-photo" @click="addDemoPhoto">
          使用演示照片
        </view>
      </view>

      <view class="farm-hd">
        <text>自动带入</text>
      </view>
      <view class="farm-card">
        <view class="farm-kv">
          <text class="k">定位</text>
          <text class="v" :class="{ 'farm-t12': locState === 'locating' }">{{ locText }}</text>
        </view>
        <view class="farm-kv">
          <text class="k">环境快照</text>
          <text class="v farm-t12">提交时以服务器读数为准（演示数据）</text>
        </view>
        <EnvSnapshot :env="task.env" />
      </view>

      <view class="farm-note">
        提交后系统自动做三项校验：照片拍摄时间、GPS 是否在基地围栏内、是否重复画面；然后进入经营者审核，审核通过后写进认养人的生长档案。
      </view>

      <view class="farm-footbar">
        <wd-button type="info" custom-class="btn-cancel" @click="goBack">
          取消
        </wd-button>
        <wd-button type="primary" custom-class="btn-flex" :loading="submitting" @click="submit">
          提交打卡
        </wd-button>
      </view>
    </template>
    <DemoBadge />
  </view>
</template>

<script lang="ts" setup>
import type { StewardTaskDetail } from '@/api/farm/steward'
import { useToast } from '@wot-ui/ui/components/wd-toast'
import { computed, ref } from 'vue'
import { checkinStewardTask, getStewardTask } from '@/api/farm/steward'
import { navigateBackPlus } from '@/utils'
import DemoBadge from '../components/demo-badge.vue'
import EnvSnapshot from '../components/env-snapshot.vue'
import { compressImage, makeDemoPhoto, showDemoBadge, tryGetLocation } from '../components/farm-utils'

definePage({
  style: {
    navigationBarTitleText: '农事打卡',
    navigationStyle: 'custom',
  },
})

const toast = useToast()
const id = ref(0)
const task = ref<StewardTaskDetail>()
const loadError = ref('')
const content = ref('')
const photos = ref<string[]>([])
const compressing = ref(false)
const submitting = ref(false)
const locState = ref<'locating' | 'gps' | 'base'>('locating')
const loc = ref<{ lng: number, lat: number } | null>(null)

const locText = computed(() => {
  if (locState.value === 'locating') {
    return '正在定位…'
  }
  if (locState.value === 'gps' && loc.value) {
    return `GPS ${loc.value.lng.toFixed(5)}, ${loc.value.lat.toFixed(5)}`
  }
  return '定位：使用基地坐标（演示）'
})

onLoad(async (options) => {
  id.value = Number(options?.id || 0)
  try {
    task.value = await getStewardTask(id.value)
    if (task.value.status !== 0) {
      loadError.value = '这条任务已经完成了'
      task.value = undefined
      return
    }
  } catch (e: any) {
    loadError.value = e?.msg || '加载失败'
    return
  }
  const r = await tryGetLocation()
  if (r) {
    loc.value = r
    locState.value = 'gps'
  } else {
    locState.value = 'base'
  }
})

function goBack() {
  navigateBackPlus(`/pages-farm/task/detail?id=${id.value}`)
}

function choosePhoto() {
  if (compressing.value) {
    return
  }
  uni.chooseImage({
    count: 9 - photos.value.length,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: async (res) => {
      const paths = (Array.isArray(res.tempFilePaths) ? res.tempFilePaths : [res.tempFilePaths]) as string[]
      compressing.value = true
      try {
        for (const p of paths) {
          if (photos.value.length >= 9) {
            break
          }
          photos.value.push(await compressImage(p))
        }
      } catch (e: any) {
        toast.error(e?.message || '图片处理失败')
      } finally {
        compressing.value = false
      }
    },
  })
}

function addDemoPhoto() {
  if (task.value && photos.value.length < 9) {
    photos.value.push(makeDemoPhoto(task.value.title))
  }
}

function removePhoto(i: number) {
  photos.value.splice(i, 1)
}

function preview(i: number) {
  uni.previewImage({ urls: photos.value, current: i })
}

async function submit() {
  if (!task.value || submitting.value) {
    return
  }
  if (photos.value.length === 0) {
    toast.warning('至少拍一张现场照片')
    return
  }
  submitting.value = true
  try {
    const res = await checkinStewardTask({
      taskId: task.value.id,
      content: content.value.trim(),
      photos: photos.value,
      // 拿不到定位时不传，服务端按基地中心坐标处理
      lng: loc.value?.lng,
      lat: loc.value?.lat,
    })
    const q = [
      `taskId=${task.value.id}`,
      `recordId=${res.recordId}`,
      `exif=${res.checks.exifTime ? 1 : 0}`,
      `gps=${res.checks.gpsFence ? 1 : 0}`,
      `dup=${res.checks.duplicate ? 1 : 0}`,
      `loc=${locState.value}`,
      `title=${encodeURIComponent(task.value.title)}`,
      `temp=${res.env?.airTemp ?? ''}`,
      `hum=${res.env?.airHumidity ?? ''}`,
      `soil=${res.env?.soilMoisture ?? ''}`,
    ].join('&')
    uni.redirectTo({ url: `/pages-farm/task/result?${q}` })
  } catch {
    // 错误提示由请求层按接口返回的 msg 弹出
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
@import '../components/farm.scss';

.mt8 {
  margin-top: 8rpx;
}
.req {
  color: $r;
}
.photos {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  .ph {
    position: relative;
    width: 196rpx;
    height: 196rpx;
    overflow: hidden;
    border-radius: 12rpx;
    .img {
      width: 100%;
      height: 100%;
    }
    .del {
      position: absolute;
      top: 0;
      right: 0;
      width: 40rpx;
      height: 40rpx;
      font-size: 32rpx;
      line-height: 36rpx;
      color: #fff;
      text-align: center;
      background: rgba(0, 0, 0, 0.5);
      border-radius: 0 0 0 12rpx;
    }
    &.add {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 8rpx;
      background: #f6f7f9;
      border: 2rpx dashed #d5d9de;
    }
  }
}
.demo-photo {
  display: inline-block;
  margin-top: 16rpx;
  padding: 8rpx 20rpx;
  font-size: 24rpx;
  color: $o;
  border: 1rpx solid $o;
  border-radius: 8rpx;
}
:deep(.btn-flex) {
  flex: 1;
}
:deep(.btn-cancel) {
  width: 200rpx;
}
</style>
