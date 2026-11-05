<template>
  <!-- edit by 认养农场：管家端首页改为「工作台 / 今日任务」（src/pages-farm/components/farm-home.vue）。
       芋道原工作台的组件（./components/*）保留未删，需要时恢复本文件即可 -->
  <view class="yd-page-container">
    <wd-navbar title="工作台" placeholder safe-area-inset-top fixed />
    <FarmHome ref="farmHomeRef" />
  </view>
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import FarmHome from '@/pages-farm/components/farm-home.vue'

defineOptions({
  name: 'Home',
})

definePage({
  type: 'home',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '工作台',
  },
})

const farmHomeRef = ref<InstanceType<typeof FarmHome>>()

/** 每次回到工作台都刷新（打卡、接单后数字要变） */
onShow(() => {
  farmHomeRef.value?.refresh()
})
</script>
