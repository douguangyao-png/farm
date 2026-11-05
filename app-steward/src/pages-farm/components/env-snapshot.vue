<template>
  <view class="farm-env">
    <view class="cell">
      <view class="num">
        {{ env?.airTemp ?? '-' }}<text class="unit">°C</text>
      </view>
      <view class="lbl">
        气温
      </view>
    </view>
    <view class="cell">
      <view class="num">
        {{ env?.airHumidity ?? '-' }}<text class="unit">%</text>
      </view>
      <view class="lbl">
        空气湿度
      </view>
    </view>
    <view class="cell">
      <view class="num" :class="{ 'farm-red': dry }">
        {{ env?.soilMoisture ?? '-' }}<text class="unit">%</text>
      </view>
      <view class="lbl" :class="{ 'farm-red': dry }">
        土壤湿度{{ dry ? '（低于 22%）' : '' }}
      </view>
    </view>
  </view>
</template>

<script lang="ts" setup>
import type { FarmEnv } from '@/api/farm/steward'
import { computed } from 'vue'

const props = defineProps<{ env?: FarmEnv | null }>()

/** 土壤湿度低于 22% 触发浇水（演示数据说明） */
const dry = computed(() => props.env != null && props.env.soilMoisture < 22)
</script>

<style lang="scss" scoped>
@import './farm.scss';

.unit {
  margin-left: 2rpx;
  font-size: 22rpx;
  font-weight: 400;
}
</style>
