<template>
  <div class="row">
    <div class="flex items-center justify-between">
      <span class="text-13px text-[var(--el-text-color-secondary)]">
        {{ m.label }}
        <el-tag v-if="m.demo" type="warning" size="small" effect="plain" class="ml-4px">演示</el-tag>
      </span>
      <span class="font-700">{{ m.value }}{{ m.unit }}</span>
    </div>
    <div v-if="m.hint" class="text-12px text-[var(--el-text-color-placeholder)]">{{ m.hint }}</div>
  </div>
</template>
<script lang="ts" setup>
/** 经营分析的一行指标：名称、数值、口径说明，演示指标带「演示」标 */
defineProps<{ m: { label: string; value: number | string; unit?: string; demo?: boolean; hint?: string } }>()
</script>
<style scoped>
.row {
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.row:last-child {
  border-bottom: none;
}
</style>
