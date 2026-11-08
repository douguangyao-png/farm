<template>
  <div class="flex items-start justify-between mb-12px">
    <div>
      <div class="text-18px font-600">代管服务工单</div>
      <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
        认养人「委托管家代办」产生的服务工单：管家接单 → 现场执行 → 拍照回传 → 自动写入生长档案并分账
      </div>
    </div>
    <div class="flex gap-8px">
      <el-button v-hasPermi="['farm:serviceorder:rule']" @click="notOpen">派单规则</el-button>
      <el-button v-hasPermi="['farm:serviceorder:create']" type="primary" @click="notOpen">
        <Icon icon="ep:plus" class="mr-5px" />手动建单
      </el-button>
    </div>
  </div>

  <StatCards :items="statItems" />

  <el-row :gutter="12">
    <el-col :xs="24" :lg="16">
      <ContentWrap>
        <div class="font-600 mb-8px">工单列表</div>
        <el-form :model="queryParams" :inline="true" label-width="56px">
          <el-form-item label="工单号">
            <el-input
              v-model="queryParams.no"
              placeholder="DG..."
              clearable
              class="!w-200px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item>
            <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
            <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
          </el-form-item>
        </el-form>
        <el-tabs v-model="activeTab" @tab-change="onTabChange">
          <el-tab-pane v-for="t in SO_TABS" :key="String(t.name)" :name="t.name">
            <template #label>{{ t.label }}（{{ counts[t.countKey] ?? 0 }}）</template>
          </el-tab-pane>
        </el-tabs>
        <el-table v-loading="loading" :data="list">
          <el-table-column label="工单号" prop="no" width="150">
            <template #default="{ row }">
              <el-link type="primary" @click="openDetail(row)">{{ row.no }}</el-link>
            </template>
          </el-table-column>
          <el-table-column label="地块" prop="assetCode" width="80" />
          <el-table-column label="认养人" min-width="130">
            <template #default="{ row }">
              {{ row.memberName }}
              <span v-if="row.careModeName" class="text-12px text-[var(--el-text-color-secondary)]">
                （{{ row.careModeName }}）
              </span>
            </template>
          </el-table-column>
          <el-table-column label="服务项" min-width="150">
            <template #default="{ row }">
              <div class="font-600">{{ row.serviceType }}</div>
              <div class="text-12px text-[var(--el-text-color-secondary)]">
                {{ row.fromTaskId ? '自管任务委托' : '认养人下单' }}
              </div>
            </template>
          </el-table-column>
          <el-table-column
            label="金额"
            prop="price"
            width="90"
            align="right"
            :formatter="moneyFormatter"
          />
          <el-table-column label="执行人" prop="stewardName" width="90">
            <template #default="{ row }">{{ row.stewardName || '待分配' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="enumOf(SO_STATUS, row.status).type">
                {{ enumLabel(SO_STATUS, row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="现场照片" width="110">
            <template #default="{ row }">
              {{ row.proofCount ? `${row.proofCount} 张 · 已归档` : '—' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button link type="primary" :disabled="!row.proofCount" @click="openDetail(row)">
                照片
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <Pagination
          :total="total"
          v-model:page="queryParams.pageNo"
          v-model:limit="queryParams.pageSize"
          @pagination="getList"
        />
      </ContentWrap>
    </el-col>

    <el-col :xs="24" :lg="8">
      <ContentWrap>
        <div class="font-600 mb-8px">分账规则（每单自动执行）</div>
        <el-table :data="SPLIT_RULES" size="small">
          <el-table-column label="分账方" prop="name" />
          <el-table-column label="比例" width="70" align="right">
            <template #default="{ row }">
              <b>{{ row.pct }}%</b>
            </template>
          </el-table-column>
          <el-table-column label="示例（¥30 浇水单）" prop="sample" width="130" />
        </el-table>
        <div class="text-12px text-[var(--el-text-color-secondary)] mt-8px">
          演示：真实分账未接入，这里只展示规则，不会产生资金动作。
        </div>
      </ContentWrap>
      <ContentWrap>
        <div class="font-600 mb-8px">工单执行留痕（自动）</div>
        <div v-for="x in TRACE_ROWS" :key="x.lab" class="flex items-start mb-8px text-13px">
          <span class="w-40px text-[var(--el-text-color-secondary)] flex-shrink-0">{{ x.lab }}</span>
          <span class="flex flex-wrap gap-6px">
            <el-tag v-for="v in x.vals" :key="v" type="info" effect="plain" size="small">{{
              v
            }}</el-tag>
          </span>
        </div>
        <el-alert
          type="success"
          :closable="false"
          show-icon
          title="认养人自管任务逾期未处理时，系统会推送「委托代办」入口，转化成的工单都在这里。"
        />
      </ContentWrap>
    </el-col>
  </el-row>

  <el-drawer v-model="detailVisible" :title="`代管工单 ${detail?.no ?? ''}`" size="520px">
    <div v-loading="detailLoading">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="服务内容">{{ detail.taskTitle }}</el-descriptions-item>
          <el-descriptions-item label="服务类型">{{ detail.serviceType }}</el-descriptions-item>
          <el-descriptions-item label="地块">{{ detail.assetCode }}</el-descriptions-item>
          <el-descriptions-item label="认养人">
            {{ detail.memberName
            }}<template v-if="detail.careModeName">（{{ detail.careModeName }}）</template>
          </el-descriptions-item>
          <el-descriptions-item label="执行人">{{
            detail.stewardName || '待分配'
          }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ formatMoney(detail.price) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="enumOf(SO_STATUS, detail.status).type">
              {{ enumLabel(SO_STATUS, detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="下单">{{ fmtTime(detail.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="接单">{{ fmtTime(detail.acceptTime) }}</el-descriptions-item>
          <el-descriptions-item label="完成">{{ fmtTime(detail.finishTime) }}</el-descriptions-item>
        </el-descriptions>

        <div class="mt-16px mb-8px font-600">分账规则（演示，未执行）</div>
        <el-table :data="splitRows" size="small" border>
          <el-table-column label="分给" prop="name" />
          <el-table-column label="比例" width="80" align="center">
            <template #default="{ row }">{{ row.pct }}%</template>
          </el-table-column>
          <el-table-column label="金额" width="120" align="right">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
        </el-table>

        <div class="mt-16px mb-8px font-600">现场照片</div>
        <div class="flex flex-wrap gap-6px">
          <el-image
            v-for="(p, i) in detail.proofPhotos"
            :key="i"
            :src="p"
            :preview-src-list="detail.proofPhotos"
            :initial-index="Number(i)"
            preview-teleported
            fit="cover"
            class="w-100px h-100px rounded-4px"
          />
          <span
            v-if="!detail.proofPhotos?.length"
            class="text-12px text-[var(--el-text-color-secondary)]"
          >
            管家完成后上传
          </span>
        </div>
      </template>
    </div>
  </el-drawer>
</template>
<script lang="ts" setup>
import * as ServiceOrderApi from '@/api/farm/serviceOrder'
import { enumLabel, enumOf, fmtTime, formatMoney, moneyFormatter } from '../constants'
import StatCards from '../fulfill/StatCards.vue'
import { SO_STATUS, SO_TABS, SPLIT_RULES } from './constants'

defineOptions({ name: 'FarmServiceOrder' })

const message = useMessage()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const stats = ref<any>({})
const queryParams = reactive<any>({ pageNo: 1, pageSize: 10, no: undefined, status: undefined })
const activeTab = ref<string | number>('all')
const counts = computed<Record<string, number>>(() => stats.value.counts ?? {})

const TRACE_ROWS = [
  { lab: '接单', vals: ['管家 App 抢单 / 派单'] },
  { lab: '执行', vals: ['现场拍照 + GPS 围栏 + 时间戳'] },
  { lab: '归档', vals: ['写入生长档案', '投入品台账'] },
  { lab: '推送', vals: ['订阅消息通知认养人'] }
]

const statItems = computed(() => {
  const s = stats.value
  const g = s.monthCountGrowth
  return [
    {
      label: '本月工单',
      value: `${s.monthCount ?? 0} 单`,
      sub: g == null ? '上月暂无数据' : `环比 ${g >= 0 ? '+' : ''}${g}%`,
      tone: g != null && g >= 0 ? ('up' as const) : ('muted' as const)
    },
    {
      label: '工单收入',
      value: formatMoney(s.income ?? 0),
      sub: `管家分成 ${formatMoney(s.stewardIncome ?? 0)}`
    },
    {
      label: '平均接单时长',
      value: s.avgAcceptHours == null ? '-' : `${s.avgAcceptHours} 小时`,
      sub: '承诺 24h 内'
    },
    { label: '执行好评率', value: `${s.goodRate ?? '-'}%`, sub: '认养人评价（演示数据）' }
  ]
})

const getList = async () => {
  loading.value = true
  try {
    const res = await ServiceOrderApi.getServiceOrderPage(queryParams)
    list.value = res.list
    total.value = res.total
    stats.value = res.stats ?? {}
  } finally {
    loading.value = false
  }
}
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}
const onTabChange = (name: string | number) => {
  queryParams.status = name === 'all' ? undefined : name
  handleQuery()
}
const resetQuery = () => {
  queryParams.no = undefined
  queryParams.status = undefined
  activeTab.value = 'all'
  handleQuery()
}
const notOpen = () => message.info('演示版暂未开放')

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<any>()
const openDetail = async (row: any) => {
  detail.value = row
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = { ...row, ...(await ServiceOrderApi.getServiceOrder(row.id)) }
  } finally {
    detailLoading.value = false
  }
}
const splitRows = computed(() => {
  const d = detail.value
  if (!d?.split) return []
  const price = Number(d.price) || 0
  return [
    { name: '管家', pct: d.split.steward },
    { name: '农场', pct: d.split.farm },
    { name: '平台', pct: d.split.platform }
  ].map((x) => ({ ...x, amount: Math.round((price * x.pct) / 100) }))
})

onMounted(getList)
// keep-alive 切回来时刷新（从工作台待办跳过来、或在别的页面改过数据）
let mounted = false
onActivated(() => {
  if (mounted) getList()
  mounted = true
})
</script>
