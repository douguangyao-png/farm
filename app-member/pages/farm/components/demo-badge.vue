<!-- 「演示数据」角标（R-7）：只在 SHOPRO_MOCK_BADGE=true 的构建里显示 -->
<template>
  <view v-if="show" class="demo-badge">演示数据</view>
</template>

<script setup>
  import { SHOW_DEMO } from '@/pages/farm/common/farm';
  const show = SHOW_DEMO;
</script>

<style lang="scss" scoped>
  .demo-badge {
    position: fixed;
    right: 0;
    top: calc(var(--status-bar-height, 0px) + 100px);
    z-index: 998;
    pointer-events: none;
    background: rgba(255, 143, 31, 0.92);
    color: #fff;
    font-size: 11px;
    line-height: 1;
    padding: 5px 8px 5px 10px;
    border-radius: 12px 0 0 12px;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
  }
</style>
