<template>
  <view v-if="showDemoBadge" class="farm-demo-badge">
    演示数据
  </view>
</template>

<script lang="ts" setup>
import { showDemoBadge } from './farm-utils'
</script>

<style lang="scss" scoped>
.farm-demo-badge {
  position: fixed;
  right: 0;
  bottom: 220rpx;
  z-index: 999;
  padding: 6rpx 16rpx;
  font-size: 20rpx;
  color: #fff;
  background: rgba(255, 143, 31, 0.92);
  border-radius: 999rpx 0 0 999rpx;
  pointer-events: none;
}
</style>
