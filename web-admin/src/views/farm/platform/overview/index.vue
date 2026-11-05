<template>
  <div v-loading="loading">
    <el-row :gutter="12">
      <el-col v-for="card in cards" :key="card.label" :xs="12" :sm="8" :lg="4" class="mb-12px">
        <el-card
          shadow="never"
          class="kpi-card"
          :class="{ 'is-link': card.to }"
          @click="go(card.to)"
        >
          <div class="text-13px text-[var(--el-text-color-secondary)]">{{ card.label }}</div>
          <div
            class="mt-8px text-24px font-600"
            :class="card.warn ? 'text-[var(--el-color-warning)]' : ''"
          >
            {{ card.value }}
          </div>
        </el-card>
      </el-col>
    </el-row>

    <ContentWrap title="本月租户排行（按 GMV）">
      <el-table :data="data?.tenantRank ?? []" :stripe="true">
        <el-table-column label="排名" type="index" width="70" align="center" />
        <el-table-column label="租户" prop="tenantName" min-width="160" />
        <el-table-column label="套餐" prop="packageName" min-width="120" />
        <el-table-column label="本月 GMV" min-width="140" align="right">
          <template #default="{ row }">{{ fenToYuan(row.gmvMonth) }}</template>
        </el-table-column>
        <el-table-column label="认养中" prop="adoptingCount" width="100" align="right" />
      </el-table>
      <div class="mt-8px text-12px text-[var(--el-text-color-secondary)]">
        GMV = 本月已支付的认养订单金额 + 本月农资订单金额，不含平台租户。
      </div>
    </ContentWrap>
  </div>
</template>

<script lang="ts" setup>
import * as PlatformApi from '@/api/farm/platform'
import { fenToYuan } from '../constants'

defineOptions({ name: 'FarmPlatformOverview' })

const router = useRouter()
const loading = ref(false)
const data = ref<PlatformApi.PlatformOverviewVO>()

const cards = computed(() => {
  const d = data.value
  return [
    { label: '租户数', value: d?.tenantCount ?? '-', to: '/platform/tenant' },
    { label: '活跃租户', value: d?.activeTenantCount ?? '-', to: '/platform/tenant' },
    { label: '本月 GMV', value: fenToYuan(d?.gmvMonth) },
    { label: '本月订单', value: d?.orderCountMonth ?? '-' },
    {
      label: '待处理预警',
      value: d?.pendingAlertCount ?? '-',
      to: '/platform/risk-alert',
      warn: !!d?.pendingAlertCount
    },
    {
      label: '待处理反馈',
      value: d?.feedbackPendingCount ?? '-',
      to: '/system/feedback',
      warn: !!d?.feedbackPendingCount
    }
  ]
})

const go = (to?: string) => {
  if (to) router.push(to)
}

const load = async () => {
  loading.value = true
  try {
    data.value = await PlatformApi.getPlatformOverview()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.kpi-card.is-link {
  cursor: pointer;
}
.kpi-card.is-link:hover {
  border-color: var(--el-color-primary);
}
</style>
