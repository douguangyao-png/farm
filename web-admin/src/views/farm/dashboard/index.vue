<template>
  <div v-loading="loading">
    <!-- 页头：问候 + 摘要 + 快捷按钮（原型 08 dash） -->
    <div class="mb-12px flex items-start justify-between">
      <div>
        <div class="text-18px font-700">{{ greeting }}，{{ userName }}</div>
        <div v-if="data" class="mt-4px text-13px text-[var(--el-text-color-secondary)]">
          今天有 <b class="text-[var(--el-color-primary)]">{{ todoCount }} 项待办</b>、
          <b class="text-[var(--el-color-danger)]">{{ riskCount }} 条风控提醒</b>；最近 7 天新增认养
          {{ data.orderCountLast7d }} 单，环比 {{ fmtRate(data.orderCountLast7dRate) }}
        </div>
      </div>
      <el-button type="primary" @click="router.push('/farm-product/product')">
        <Icon icon="ep:plus" class="mr-5px" />发布认养商品
      </el-button>
    </div>

    <!-- 4 张统计卡 -->
    <el-row :gutter="12" class="mb-12px">
      <el-col v-for="k in kpis" :key="k.label" :xs="12" :lg="6">
        <el-card
          shadow="never"
          class="kpi-card"
          :class="{ 'cursor-pointer': !!k.path }"
          @click="k.path && router.push(k.path)"
        >
          <div class="text-13px text-[var(--el-text-color-secondary)]">{{ k.label }}</div>
          <div class="mt-6px text-24px font-700">{{ k.value }}</div>
          <div class="mt-4px text-12px text-[var(--el-text-color-secondary)]">
            {{ k.sub }}
            <span v-if="k.delta" :class="k.deltaUp ? 'text-up' : 'text-down'">{{ k.delta }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="mb-12px">
          <template #header>
            <div class="flex items-center justify-between">
              <span class="font-700">近 30 天认养 GMV（元）</span>
              <span>
                <el-tag v-if="data?.demo" type="warning" size="small" effect="plain" class="mr-8px"
                  >演示数据</el-tag
                >
                <el-radio-group v-model="grain" size="small">
                  <el-radio-button value="day">日</el-radio-button>
                  <el-radio-button value="week">周</el-radio-button>
                  <el-radio-button value="month">月</el-radio-button>
                </el-radio-group>
              </span>
            </div>
          </template>
          <Echart :height="280" :options="gmvOption" />
          <div class="text-12px text-[var(--el-text-color-secondary)]">
            <template v-if="data?.gmvPeak">
              峰值 {{ data.gmvPeak.date }} {{ formatMoney(data.gmvPeak.amount) }} ·
            </template>
            认养款和农资款按支付日汇总；演示环境叠加了固定底数，见《演示数据说明》。
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="mb-12px">
          <template #header>
            <div class="flex items-center justify-between">
              <span class="font-700">待办与风控提醒</span>
              <span class="text-12px text-[var(--el-text-color-secondary)]"
                >全部 {{ data?.todos?.length || 0 }} 项</span
              >
            </div>
          </template>
          <div v-for="t in data?.todos || []" :key="t.type" class="todo-row">
            <span class="dot" :class="`dot-${t.level}`" :style="{ opacity: t.count > 0 ? 1 : 0.35 }" />
            <div class="flex-1 min-w-0">
              <div class="flex items-center justify-between">
                <span class="text-13px font-600"
                  >{{ t.title }}
                  <span class="font-400 text-[var(--el-text-color-placeholder)]"
                    >×{{ t.count }}</span
                  ></span
                >
                <el-button link type="primary" size="small" @click="router.push(t.path)"
                  >处理 →</el-button
                >
              </div>
              <div class="mt-2px text-12px text-[var(--el-text-color-secondary)]">{{ t.desc }}</div>
            </div>
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
    </el-row>

    <el-row :gutter="12">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="mb-12px">
          <template #header><span class="font-700">认养位使用情况</span></template>
          <div v-for="s in data?.slotUsage || []" :key="s.type" class="mb-12px">
            <div class="flex items-center justify-between text-13px">
              <span class="text-[var(--el-text-color-secondary)]">{{ s.name }}</span>
              <span class="font-700">{{ s.used }} / {{ s.total }} {{ s.unit }}</span>
            </div>
            <el-progress
              :percentage="slotPercent(s)"
              :show-text="false"
              :status="slotPercent(s) > 90 ? 'exception' : slotPercent(s) > 70 ? 'warning' : undefined"
            />
          </div>
          <div class="mt-8px text-12px text-[var(--el-text-color-secondary)]">
            认养位即将售罄时，系统会自动提醒补充可售地块，并暂停超卖。
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="mb-12px">
          <template #header>
            <div class="flex items-center justify-between">
              <span class="font-700">最新认养订单</span>
              <el-button link type="primary" @click="router.push('/farm-order/order')"
                >查看全部 →</el-button
              >
            </div>
          </template>
          <el-table :data="data?.latestOrders || []" size="small">
            <el-table-column label="订单号" prop="no" min-width="120" />
            <el-table-column label="客户" prop="memberName" width="80" />
            <el-table-column label="商品" min-width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ (row.productTitle || '').split('·')[0] }}</template>
            </el-table-column>
            <el-table-column label="金额" width="100" align="right">
              <template #default="{ row }"
                ><b>{{ formatMoney(row.amount) }}</b></template
              >
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="enumOf(ORDER_STATUS, row.status).type">
                  {{ enumLabel(ORDER_STATUS, row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="时间" width="100">
              <template #default="{ row }">{{ fmtTime(row.createTime, 'MM-DD HH:mm') }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="mb-12px">
          <template #header>
            <div class="flex items-center justify-between">
              <span class="font-700">今日农事动态</span>
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
            <el-tag class="ml-8px" size="small" :type="enumOf(RECORD_PUBLISH_STATUS, r.status).type">
              {{ enumLabel(RECORD_PUBLISH_STATUS, r.status) }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="mb-12px">
          <template #header><span class="font-700">本周经营概览</span></template>
          <div v-for="c in data?.weekOverview?.categories || []" :key="c.name" class="bar-row">
            <span class="bar-name">{{ c.name }}</span>
            <div class="bar-track">
              <div class="bar-fill" :style="{ width: barWidth(c.count) + '%' }" />
            </div>
            <span class="bar-val">{{ c.count }} 单</span>
          </div>
          <el-divider class="!my-12px" />
          <div class="kv"><span>客单价</span><b>{{ formatMoney(data?.weekOverview?.avgOrderAmount) }}</b></div>
          <div class="kv">
            <span>复购 / 续费率</span><b>{{ data?.weekOverview?.repurchaseRate ?? '-' }}%</b>
          </div>
          <div class="kv">
            <span>退费率</span>
            <b>{{
              data?.weekOverview?.refundRate == null ? '暂无退款数据' : data.weekOverview.refundRate + '%'
            }}</b>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- farmer-saas: 平台公告（平台在「系统管理 → 文章管理」发布的公告、帮助） -->
    <el-card v-if="notices.length" shadow="never" class="mb-12px">
      <template #header><span class="font-700">平台公告</span></template>
      <div
        v-for="n in notices"
        :key="n.id"
        class="flex cursor-pointer items-center justify-between py-8px hover:text-[var(--el-color-primary)]"
        @click="openNotice(n.id)"
      >
        <span
          ><el-tag size="small" class="mr-8px">{{ n.categoryName || '公告' }}</el-tag
          >{{ n.title }}</span
        >
        <span class="text-12px text-[var(--el-text-color-secondary)]">{{
          fmtTime(n.createTime)
        }}</span>
      </div>
    </el-card>
    <div class="mb-12px text-12px text-[var(--el-text-color-secondary)]">
      <Icon icon="ep:warning" class="mr-4px" />本页面所有金额均为演示数据。
    </div>
    <Dialog v-model="noticeVisible" :title="notice?.title || '平台公告'" width="560px">
      <div v-if="notice" class="notice-body">
        <div class="mb-10px text-12px text-[var(--el-text-color-secondary)]"
          >{{ notice.author }} · {{ fmtTime(notice.createTime) }}</div
        >
        <div v-dompurify-html="notice.content"></div>
      </div>
    </Dialog>
  </div>
</template>
<script lang="ts" setup>
import type { EChartsOption } from 'echarts'
import * as DashboardApi from '@/api/farm/dashboard'
import { useUserStore } from '@/store/modules/user'
import { ORDER_STATUS, enumLabel, enumOf, fmtTime, formatMoney } from '../constants'
import { RECORD_PUBLISH_STATUS } from '../record/constants'

defineOptions({ name: 'FarmDashboard' })

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const data = ref<any>()

const userName = computed(() => userStore.getUser?.nickname || '经营者')
const greeting = computed(() => {
  const h = new Date().getHours()
  return h < 12 ? '早上好' : h < 18 ? '下午好' : '晚上好'
})
// 待办 = 有数量的普通待办；风控提醒 = 有数量的 danger 级别项
const todoCount = computed(
  () => (data.value?.todos || []).filter((t: any) => t.count > 0 && t.level !== 'danger').length
)
const riskCount = computed(
  () => (data.value?.todos || []).filter((t: any) => t.count > 0 && t.level === 'danger').length
)

const fmtRate = (v: number | null | undefined) => (v == null ? '-' : (v > 0 ? '+' : '') + v + '%')
const diff = (now: number, before: number) => {
  const d = now - before
  return d === 0 ? '' : (d > 0 ? '+' : '') + d
}

const kpis = computed(() => {
  const d = data.value
  if (!d) return []
  return [
    {
      label: '今日认养金额',
      value: formatMoney(d.gmvToday),
      sub: '认养 + 农资 · 较昨日',
      delta: d.gmvDayOnDayRate == null ? '' : fmtRate(d.gmvDayOnDayRate),
      deltaUp: (d.gmvDayOnDayRate ?? 0) >= 0
    },
    {
      label: '新增认养订单',
      value: d.orderCountToday,
      sub: `本月累计 ${d.orderCountMonth} 单`,
      delta: diff(d.orderCountToday, d.orderCountYesterday),
      deltaUp: d.orderCountToday >= d.orderCountYesterday,
      path: '/farm-order/order'
    },
    {
      label: '待发货',
      value: d.pendingShipCount,
      sub: `${d.pendingShipCount} 单收成待发出`,
      path: '/farm-order/shipment'
    },
    {
      label: '在养认养人',
      value: d.adoptingMemberCount,
      sub: `续费率 ${d.repurchaseRate}%`,
      path: '/farm-asset/asset'
    }
  ] as { label: string; value: any; sub: string; delta?: string; deltaUp?: boolean; path?: string }[]
})

// 曲线：日 = 30 个点；周 = 每 7 天合并（从今天往前数）；月 = 按月合并
const grain = ref<'day' | 'week' | 'month'>('day')
const trendPoints = computed(() => {
  const trend: { date: string; amount: number }[] = data.value?.gmvTrend || []
  if (grain.value === 'day') return trend
  if (grain.value === 'week') {
    const out: { date: string; amount: number }[] = []
    for (let end = trend.length; end > 0; end -= 7) {
      const slice = trend.slice(Math.max(0, end - 7), end)
      out.unshift({ date: slice[slice.length - 1].date, amount: slice.reduce((s, x) => s + x.amount, 0) })
    }
    return out
  }
  const byMonth = new Map<string, number>()
  for (const x of trend) {
    const k = x.date.slice(0, 2) + '月'
    byMonth.set(k, (byMonth.get(k) || 0) + x.amount)
  }
  return Array.from(byMonth, ([date, amount]) => ({ date, amount }))
})

const gmvOption = computed<EChartsOption>(() => {
  const trend = trendPoints.value
  return {
    grid: { left: 60, right: 20, top: 20, bottom: 30 },
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v: any) => formatMoney(Number(v) * 100)
    },
    xAxis: { type: 'category', data: trend.map((x) => x.date), boundaryGap: false },
    yAxis: { type: 'value', axisLabel: { formatter: (v: number) => '¥' + v.toLocaleString() } },
    series: [
      {
        name: '认养 GMV',
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.15 },
        itemStyle: { color: '#22a06b' },
        data: trend.map((x) => x.amount / 100)
      }
    ]
  }
})

const slotPercent = (s: { used: number; total: number }) =>
  s.total ? Math.round((s.used / s.total) * 100) : 0
const barWidth = (n: number) => {
  const max = Math.max(1, ...(data.value?.weekOverview?.categories || []).map((c: any) => c.count))
  return Math.round((n / max) * 100)
}

const load = async () => {
  loading.value = true
  try {
    data.value = await DashboardApi.getDashboardSummary()
  } finally {
    loading.value = false
  }
}

// farmer-saas: 平台公告
const notices = ref<any[]>([])
const notice = ref<any>()
const noticeVisible = ref(false)
const openNotice = async (id: number) => {
  notice.value = await DashboardApi.getNotice(id)
  noticeVisible.value = true
}
onMounted(async () => {
  await load()
  notices.value = (await DashboardApi.getNoticeList()) || []
})
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
.text-up {
  margin-left: 4px;
  color: var(--el-color-success);
}
.text-down {
  margin-left: 4px;
  color: var(--el-color-danger);
}
.todo-row {
  display: flex;
  gap: 9px;
  padding: 9px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.dot {
  flex: 0 0 6px;
  width: 6px;
  height: 6px;
  margin-top: 7px;
  border-radius: 50%;
}
.dot-danger {
  background: var(--el-color-danger);
}
.dot-primary {
  background: var(--el-color-success);
}
.dot-warning {
  background: var(--el-color-warning);
}
.record-row {
  display: flex;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  font-size: 13px;
}
.bar-name {
  flex: 0 0 72px;
  color: var(--el-text-color-secondary);
}
.bar-track {
  flex: 1;
  height: 8px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}
.bar-fill {
  height: 100%;
  background: #22a06b;
  border-radius: 4px;
}
.bar-val {
  flex: 0 0 56px;
  text-align: right;
}
.kv {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
  font-size: 13px;
}
</style>
