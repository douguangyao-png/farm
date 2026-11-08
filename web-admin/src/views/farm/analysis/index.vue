<!-- 权限串：farm:analysis:query / farm:analysis:export。曝光、浏览、渠道、续费等无埋点的指标为演示，页面上逐项标「演示」 -->
<template>
  <div v-loading="loading">
    <div class="flex items-start justify-between mb-12px">
      <div>
        <div class="text-18px font-600">经营分析</div>
        <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
          从曝光到复购的全链路转化；也是判断「该改运营还是改产品」的依据
        </div>
      </div>
      <div class="flex gap-8px items-center">
        <el-radio-group v-model="days" @change="load">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
          <el-radio-button :value="90">近 90 天</el-radio-button>
        </el-radio-group>
        <el-button v-hasPermi="['farm:analysis:export']" @click="message.info('演示版暂未开放')">导出报表</el-button>
      </div>
    </div>

    <el-row :gutter="12">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="mb-12px">
          <template #header><span class="font-700">认养转化漏斗</span></template>
          <Echart :height="320" :options="funnelOption" />
          <div class="text-12px text-[var(--el-text-color-secondary)]">
            灰色标「演示」的环节没有埋点：曝光、浏览按下单数和原型转化率反推，首季续费按 62.8% 估算。下单、支付、签署合同来自订单。
          </div>
          <div class="mt-8px text-13px">
            <b>关键发现：</b>{{ finding }}
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="mb-12px">
          <template #header>
            <span class="font-700"
              >渠道来源占比
              <el-tag type="warning" size="small" effect="plain" class="ml-6px">演示</el-tag></span
            >
          </template>
          <Echart :height="150" :options="channelOption" />
          <div class="text-12px text-[var(--el-text-color-secondary)]">单位：成交订单数（订单上暂无渠道字段，按固定占比拆分）</div>
        </el-card>
        <el-card shadow="never" class="mb-12px">
          <template #header><span class="font-700">品类结构（GMV 占比）</span></template>
          <Echart :height="150" :options="categoryOption" />
          <div class="text-12px text-[var(--el-text-color-secondary)]">单位：窗口内已支付金额（元）；认养订单按商品类型，农资和代管单列</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="mb-12px">
          <template #header><span class="font-700">复购与留存</span></template>
          <MetricRow v-for="m in data.retention || []" :key="m.label" :m="m" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="mb-12px">
          <template #header><span class="font-700">履约质量</span></template>
          <MetricRow v-for="m in data.quality || []" :key="m.label" :m="m" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
<script lang="ts" setup>
import type { EChartsOption } from 'echarts'
import * as FinanceApi from '@/api/farm/finance'
import { formatMoney } from '../constants'
import MetricRow from './MetricRow.vue'

defineOptions({ name: 'FarmAnalysis' })

const message = useMessage()
const loading = ref(false)
const days = ref(30)
const data = ref<any>({})

const funnelOption = computed<EChartsOption>(() => {
  const f: any[] = data.value.funnel || []
  return {
    tooltip: {
      trigger: 'item',
      formatter: (p: any) => {
        const x = f[p.dataIndex]
        return `${x.name}${x.demo ? '（演示）' : ''}<br/>${Number(x.value).toLocaleString()} · 环节转化 ${x.rate}%`
      }
    },
    series: [
      {
        type: 'funnel',
        left: '8%',
        width: '84%',
        top: 10,
        bottom: 10,
        sort: 'none',
        gap: 3,
        minSize: '12%',
        label: {
          show: true,
          position: 'inside',
          formatter: (p: any) => {
            const x = f[p.dataIndex]
            return `${x.name}${x.demo ? '（演示）' : ''}  ${Number(x.value).toLocaleString()}`
          }
        },
        // 演示环节用灰绿，真实环节用主色
        data: f.map((x) => ({
          name: x.name,
          // 漏斗宽度按顺序递减展示，数值为 0 时给 0.0001 避免整段消失
          value: Math.max(x.value, 0.0001),
          itemStyle: { color: x.demo ? '#9ccfb4' : '#22a06b' }
        }))
      }
    ]
  }
})

const finding = computed(() => {
  const f: any[] = data.value.funnel || []
  if (f.length < 5 || !f[2].value) return '窗口内还没有下单数据。'
  const pay = f[3]
  const lowest = [...f.slice(3, 5)].sort((a, b) => a.rate - b.rate)[0]
  return `窗口内 ${f[2].value} 单进入下单，${pay.value} 单完成支付（${pay.rate}%），${f[4].value} 单已签合同；真实环节里转化最低的是「${lowest.name}」（${lowest.rate}%）。`
})

const barOption = (rows: { name: string; value: number }[], fmt: (v: number) => string, color: string): EChartsOption => ({
  grid: { left: 90, right: 50, top: 4, bottom: 4 },
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: (v: any) => fmt(Number(v)) },
  xAxis: { type: 'value', show: false },
  yAxis: { type: 'category', inverse: true, data: rows.map((r) => r.name), axisTick: { show: false }, axisLine: { show: false } },
  series: [
    {
      type: 'bar',
      barWidth: 12,
      itemStyle: { color, borderRadius: 6 },
      label: { show: true, position: 'right', formatter: (p: any) => fmt(Number(p.value)) },
      data: rows.map((r) => r.value)
    }
  ]
})
const channelOption = computed(() => barOption(data.value.channels || [], (v) => `${v}`, '#9ccfb4'))
const categoryOption = computed(() =>
  barOption(
    (data.value.categories || []).map((c: any) => ({ name: c.name, value: c.value / 100 })),
    (v) => formatMoney(v * 100).replace(/\.00$/, ''),
    '#22a06b'
  )
)

const load = async () => {
  loading.value = true
  try {
    data.value = await FinanceApi.getAnalysis(days.value)
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
