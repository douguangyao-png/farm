<template>
  <view class="farm-page">
    <wd-navbar title="打卡结果" placeholder safe-area-inset-top fixed />

    <view class="farm-card done">
      <view class="ok">
        ✓
      </view>
      <view class="farm-t15">
        已提交，等待经营者审核
      </view>
      <view class="farm-t12 mt8">
        {{ title }} · 记录编号 {{ recordId }}
      </view>
    </view>

    <view class="farm-hd">
      <text>机器校验</text>
    </view>
    <view class="farm-card">
      <view v-for="c in checks" :key="c.name" class="farm-kv">
        <text class="k">{{ c.name }}</text>
        <text class="v" :class="c.pass ? 'farm-green' : 'farm-red'">
          {{ c.pass ? '✓ 通过' : '✗ 未通过' }}
        </text>
      </view>
      <view v-if="locMode === 'base'" class="farm-t12 mt8">
        定位：使用基地坐标（演示）
      </view>
      <view v-if="checks.some(c => !c.pass)" class="farm-note warn">
        有校验未通过，经营者审核时会看到提示。
      </view>
    </view>

    <view v-if="env" class="farm-hd">
      <text>已附带的环境快照</text>
      <text class="farm-t12">演示数据</text>
    </view>
    <view v-if="env" class="farm-card">
      <EnvSnapshot :env="env" />
    </view>

    <view class="farm-note">
      审核通过后，这条记录会出现在认养人的「生长档案」里。
    </view>

    <view class="farm-footbar">
      <wd-button type="primary" block custom-class="btn-flex" @click="backHome">
        回到今日任务
      </wd-button>
    </view>
    <DemoBadge />
  </view>
</template>

<script lang="ts" setup>
import type { FarmEnv } from '@/api/farm/steward'
import { ref } from 'vue'
import DemoBadge from '../components/demo-badge.vue'
import EnvSnapshot from '../components/env-snapshot.vue'

definePage({
  style: {
    navigationBarTitleText: '打卡结果',
    navigationStyle: 'custom',
  },
})

const title = ref('')
const recordId = ref('')
const locMode = ref('')
const env = ref<FarmEnv | null>(null)
const checks = ref<{ name: string, pass: boolean }[]>([])

onLoad((o) => {
  const q = o || {}
  title.value = decodeURIComponent(q.title || '')
  recordId.value = q.recordId || ''
  locMode.value = q.loc || ''
  checks.value = [
    { name: 'EXIF 时间', pass: q.exif === '1' },
    { name: 'GPS 围栏', pass: q.gps === '1' },
    { name: '重复画面', pass: q.dup === '1' },
  ]
  if (q.soil) {
    env.value = { airTemp: Number(q.temp), airHumidity: Number(q.hum), soilMoisture: Number(q.soil) }
  }
})

function backHome() {
  uni.switchTab({ url: '/pages/index/index' })
}
</script>

<style lang="scss" scoped>
@import '../components/farm.scss';

.done {
  padding: 48rpx 28rpx;
  text-align: center;
  .ok {
    width: 96rpx;
    height: 96rpx;
    margin: 0 auto 20rpx;
    font-size: 56rpx;
    line-height: 96rpx;
    color: #fff;
    background: $g;
    border-radius: 50%;
  }
}
.mt8 {
  margin-top: 8rpx;
}
.farm-card .farm-note {
  margin: 16rpx 0 0;
}
:deep(.btn-flex) {
  flex: 1;
}
</style>
