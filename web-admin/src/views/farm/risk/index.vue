<template>
  <div v-loading="loading">
    <el-row :gutter="12">
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="mb-12px">
          <div class="text-center py-20px">
            <div class="text-14px text-[var(--el-text-color-secondary)]">风险健康分</div>
            <div class="score" :style="{ color: scoreColor }">{{ data?.score ?? '-' }}</div>
            <div class="text-13px" :style="{ color: scoreColor }">{{ scoreText }}</div>
            <div class="mt-12px text-12px text-[var(--el-text-color-secondary)] leading-20px">
              资质证照、资金路径、合同签署率、文案合规、溯源完整度五项，各 20
              分，按本农场当前数据计算。
            </div>
            <el-button class="mt-12px" @click="load"
              ><Icon icon="ep:refresh" class="mr-5px" />重新自检</el-button
            >
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="mb-12px">
          <template #header><span class="font-700">检查项</span></template>
          <div v-for="it in data?.items || []" :key="it.key" class="item">
            <div class="flex items-center justify-between">
              <span class="font-600">
                {{ it.name }}
                <el-tag class="ml-6px" size="small" :type="STATUS[it.status]?.type">{{
                  STATUS[it.status]?.label
                }}</el-tag>
              </span>
              <span class="text-13px">{{ it.score }} / {{ it.max }}</span>
            </div>
            <el-progress
              class="my-6px"
              :percentage="Math.round((it.score / it.max) * 100)"
              :status="STATUS[it.status]?.progress"
              :show-text="false"
            />
            <div class="text-12px text-[var(--el-text-color-secondary)]">{{ it.detail }}</div>
            <div
              v-if="it.suggestion"
              class="mt-4px text-12px text-[var(--el-color-warning-dark-2)]"
            >
              建议：{{ it.suggestion }}
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-card shadow="never">
      <template #header><span class="font-700">近 30 天被拦截的文案</span></template>
      <el-table :data="data?.hits || []" empty-text="近 30 天没有被拦截的文案">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.time, 'YYYY-MM-DD HH:mm:ss') }}</template>
        </el-table-column>
        <el-table-column label="商品标题" prop="productTitle" min-width="200">
          <template #default="{ row }">{{ row.productTitle || '（新建商品）' }}</template>
        </el-table-column>
        <el-table-column label="命中的词" min-width="200">
          <template #default="{ row }">
            <el-tag v-for="w in row.words" :key="w" type="danger" size="small" class="mr-4px">{{
              w
            }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
<script lang="ts" setup>
import * as DashboardApi from '@/api/farm/dashboard'
import { fmtTime } from '../constants'

defineOptions({ name: 'FarmRiskSelfCheck' })

const STATUS: Record<
  string,
  {
    label: string
    type: 'success' | 'warning' | 'danger'
    progress: 'success' | 'warning' | 'exception'
  }
> = {
  ok: { label: '正常', type: 'success', progress: 'success' },
  warn: { label: '需关注', type: 'warning', progress: 'warning' },
  bad: { label: '有风险', type: 'danger', progress: 'exception' }
}

const loading = ref(false)
const data = ref<any>()
const scoreColor = computed(() => {
  const s = data.value?.score ?? 0
  return s >= 90 ? '#2e7d32' : s >= 70 ? '#f9a825' : '#d32f2f'
})
const scoreText = computed(() => {
  const s = data.value?.score
  if (s == null) return ''
  return s >= 90 ? '经营合规，保持' : s >= 70 ? '有几项需要处理' : '风险较高，请尽快整改'
})

const load = async () => {
  loading.value = true
  try {
    data.value = await DashboardApi.getRiskSelfCheck()
  } finally {
    loading.value = false
  }
}
onMounted(load)
let mounted = false
onActivated(() => {
  if (mounted) load()
  mounted = true
})
</script>
<style scoped>
.score {
  font-size: 64px;
  font-weight: 700;
  line-height: 1.2;
}
.item {
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
</style>
