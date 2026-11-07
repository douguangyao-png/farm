<template>
  <div
    class="grid gap-12px mb-12px"
    :style="{ gridTemplateColumns: `repeat(${items.length}, minmax(0, 1fr))` }"
  >
    <el-card v-for="it in items" :key="it.label" shadow="never" :body-style="{ padding: '14px' }">
      <div class="text-13px text-[var(--el-text-color-secondary)]">{{ it.label }}</div>
      <div class="text-24px font-600 mt-6px">{{ it.value }}</div>
      <div
        v-if="it.sub"
        class="text-12px mt-4px"
        :class="
          it.tone === 'warn'
            ? 'text-[var(--el-color-warning)]'
            : it.tone === 'up'
              ? 'text-[var(--el-color-success)]'
              : 'text-[var(--el-text-color-secondary)]'
        "
      >
        {{ it.sub }}
      </div>
    </el-card>
  </div>
</template>
<script lang="ts" setup>
/** 履约类页面（收成、配送、农资订单、农资商品、代管工单）顶部的统计卡，对应原型 08 的 kpis */
defineProps<{
  items: { label: string; value: string | number; sub?: string; tone?: 'up' | 'warn' | 'muted' }[]
}>()
</script>
