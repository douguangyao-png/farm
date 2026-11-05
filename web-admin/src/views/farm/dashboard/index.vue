<template>
  <div v-loading="loading">
    <!-- KPI -->
    <el-row :gutter="12" class="mb-12px">
      <el-col v-for="k in kpis" :key="k.label" :xs="12" :sm="8" :lg="4">
        <el-card
          shadow="never"
          class="kpi-card"
          :class="{ 'cursor-pointer': !!k.path }"
          @click="k.path && router.push(k.path)"
        >
          <div class="text-13px text-[var(--el-text-color-secondary)]">{{ k.label }}</div>
          <div class="mt-6px text-24px font-700" :style="{ color: k.color }">{{ k.value }}</div>
          <div class="mt-4px text-12px text-[var(--el-text-color-secondary)]">{{ k.sub }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="mb-12px">
          <template #header>
            <div class="flex items-center justify-between">
              <span class="font-700">近 14 天成交额（GMV）</span>
              <el-tag v-if="data?.demo" type="warning" size="small" effect="plain">演示数据</el-tag>
            </div>
          </template>
          <Echart :height="300" :options="gmvOption" />
          <div class="text-12px text-[var(--el-text-color-secondary)]">
            认养款和农资款按支付日汇总；演示环境叠加了固定底数，见《演示数据说明》。
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="mb-12px">
          <template #header><span class="font-700">认养中 · 托管方式占比</span></template>
          <Echart :height="300" :options="shareOption" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12">
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="mb-12px">
          <template #header><span class="font-700">待办</span></template>
          <div
            v-for="t in data?.todos || []"
            :key="t.type"
            class="todo-row"
            @click="router.push(t.path)"
          >
            <span>{{ t.title }}</span>
            <span>
              <el-tag :type="t.count > 0 ? 'danger' : 'info'" round>{{ t.count }}</el-tag>
              <Icon icon="ep:arrow-right" class="ml-6px" />
            </span>
          </div>
          <div
            v-if="data && data.overdueSelfTaskCount > 0"
            class="mt-8px text-12px text-[var(--el-color-warning)]"
          >
            认养人自管任务逾期
            {{ data.overdueSelfTaskCount }} 项，已提醒认养人，可一键委托管家代办。
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="mb-12px">
          <template #header>
            <div class="flex items-center justify-between">
              <span class="font-700">今日农事</span>
              <el-button link type="primary" @click="router.push('/farm-work/record')"
                >去审核</el-button
              >
            </div>
          </template>
          <el-empty
            v-if="!data?.todayRecords?.length"
            description="今天还没有农事记录"
            :image-size="60"
          />
          <div v-for="r in data?.todayRecords || []" :key="r.id" class="record-row">
            <el-tag size="small" effect="plain">{{ r.assetCode }}</el-tag>
            <span class="flex-1 ml-8px truncate">{{ r.title }}</span>
            <span class="text-12px text-[var(--el-text-color-secondary)]">
              {{ r.stewardName }} · {{ fmtTime(r.createTime, 'MM-DD HH:mm') }}
            </span>
            <el-tag class="ml-8px" size="small" :type="enumOf(RECORD_STATUS, r.status).type">
              {{ enumLabel(RECORD_STATUS, r.status) }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
<script lang="ts" setup>
import type { EChartsOption } from 'echarts'
import * as DashboardApi from '@/api/farm/dashboard'
import { CARE_MODES, RECORD_STATUS, enumLabel, enumOf, fmtTime, formatMoney } from '../constants'

defineOptions({ name: 'FarmDashboard' })

const router = useRouter()
const loading = ref(false)
const data = ref<any>()

const kpis = computed(() => {
  const d = data.value
  if (!d) return []
  const rate = d.assetTotal ? Math.round((d.assetAdopted / d.assetTotal) * 100) : 0
  return [
    { label: '今日 GMV', value: formatMoney(d.gmvToday), sub: '认养 + 农资', color: '' },
    { label: '本月 GMV', value: formatMoney(d.gmvMonth), sub: '自然月累计', color: '' },
    {
      label: '本月订单',
      value: d.orderCountMonth,
      sub: '已支付认养订单',
      color: '',
      path: '/farm-order/order'
    },
    { label: '认养中', value: d.adoptingCount, sub: '进行中的认养关系', color: '' },
    {
      label: '认养位使用率',
      value: rate + '%',
      sub: `${d.assetAdopted} / ${d.assetTotal} 个认养位`,
      color: '',
      path: '/farm-asset/asset'
    },
    {
      label: '风险健康分',
      value: d.riskScore,
      sub: '满分 100，点击看自检',
      color: d.riskScore >= 90 ? '#2e7d32' : d.riskScore >= 70 ? '#f9a825' : '#d32f2f',
      path: '/farm-board/self-check'
    }
  ]
})

const gmvOption = computed<EChartsOption>(() => {
  const trend = data.value?.gmvTrend || []
  return {
    grid: { left: 60, right: 20, top: 20, bottom: 30 },
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v: any) => formatMoney(Number(v) * 100)
    },
    xAxis: { type: 'category', data: trend.map((x: any) => x.date), boundaryGap: false },
    yAxis: { type: 'value', axisLabel: { formatter: (v: number) => '¥' + v.toLocaleString() } },
    series: [
      {
        name: 'GMV',
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.15 },
        itemStyle: { color: '#2e7d32' },
        data: trend.map((x: any) => x.amount / 100)
      }
    ]
  }
})

const shareOption = computed<EChartsOption>(() => {
  const share = data.value?.careModeShare || []
  return {
    tooltip: { trigger: 'item', formatter: '{b}：{c} 个（{d}%）' },
    legend: { bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['50%', '45%'],
        label: { formatter: '{b}\n{c} 个' },
        data: share
          .filter((x: any) => x.count > 0)
          .map((x: any) => ({
            name: enumLabel(CARE_MODES, x.mode),
            value: x.count,
            itemStyle: { color: CARE_MODES.find((m) => m.value === x.mode)?.color }
          }))
      }
    ]
  }
})

const load = async () => {
  loading.value = true
  try {
    data.value = await DashboardApi.getDashboardSummary()
  } finally {
    loading.value = false
  }
}

onMounted(load)
// keep-alive 切回来时刷新（首次挂载已经加载过）
let mounted = false
onActivated(() => {
  if (mounted) load()
  mounted = true
})
</script>
<style scoped>
.kpi-card {
  margin-bottom: 12px;
}
.todo-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 4px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
}
.todo-row:hover {
  background: var(--el-fill-color-light);
}
.record-row {
  display: flex;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
</style>
