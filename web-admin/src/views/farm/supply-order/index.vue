<template>
  <div class="flex items-start justify-between mb-12px">
    <div>
      <div class="text-18px font-600">农资订单</div>
      <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
        认养人自购农资与服务订单；每一笔都绑定到具体地块的投入品台账，与农场自采投入品合并进入溯源档案
      </div>
    </div>
    <div class="flex gap-8px">
      <el-button @click="exportCsv">导出台账</el-button>
      <el-button @click="notOpen">批量登记施用</el-button>
      <el-button type="primary" @click="notOpen">新增线下单</el-button>
    </div>
  </div>

  <StatCards :items="statItems" />

  <ContentWrap>
    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="96px">
      <el-form-item label="订单号 / 认养人">
        <el-input
          v-model="queryParams.keyword"
          placeholder="订单号或认养人"
          clearable
          class="!w-200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="绑定地块" label-width="68px">
        <el-input
          v-model="queryParams.assetCode"
          placeholder="如 A-01"
          clearable
          class="!w-140px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" label-width="56px">
        <el-select v-model="queryParams.state" placeholder="全部" clearable class="!w-130px">
          <el-option v-for="s in SUPPLY_ORDER_STATES" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="订单号" prop="no" width="160" />
      <el-table-column label="认养人" prop="memberName" width="100" />
      <el-table-column label="商品 / 服务" min-width="190">
        <template #default="{ row }">
          <div class="font-600">{{ row.spuName }}</div>
          <el-tag size="small" :type="enumOf(SUPPLY_CAT_PROTO, row.cat).type" effect="plain">
            {{ enumLabel(SUPPLY_CAT_PROTO, row.cat) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="数量" width="80" align="right">
        <template #default="{ row }">{{ row.qty }} {{ row.unit }}</template>
      </el-table-column>
      <el-table-column label="金额" width="100" align="right">
        <template #default="{ row }">
          <b>{{ formatMoney(row.amount) }}</b>
        </template>
      </el-table-column>
      <el-table-column label="绑定地块" prop="assetCode" width="90" />
      <el-table-column label="交付方式" width="100">
        <template #default="{ row }">{{ enumLabel(DELIVER_WAYS, row.deliverWay) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="SUPPLY_ORDER_STATE_TYPE[row.stateName] ?? 'info'">{{ row.stateName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="投入品留痕" min-width="170">
        <template #default="{ row }">
          <el-tag :type="enumOf(TRACE_STATE, row.traceState).type">
            {{ enumLabel(TRACE_STATE, row.traceState) }}
          </el-tag>
          <div v-if="row.traceState === 10" class="text-12px text-[var(--el-text-color-secondary)]">
            {{ fmtTime(row.usedAt) }} · {{ row.dosage }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button v-if="row.traceState === 0" link type="primary" @click="openTrace(row)">
            登记施用
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
    <el-alert
      class="mt-12px"
      type="info"
      :closable="false"
      show-icon
      title="「投入品留痕」为强制字段：认养人自购农资未登记施用时间与用量时，该地块的溯源档案会标记为「待补充」，第三方检测报告将无法出具，这是半托管 / 不托管模式维持合规透明的关键闸门。"
    />
  </ContentWrap>

  <Dialog v-model="detailVisible" title="农资订单详情" width="560px">
    <el-descriptions v-if="detailRow" :column="1" border>
      <el-descriptions-item label="订单号">{{ detailRow.no }}</el-descriptions-item>
      <el-descriptions-item label="认养人">{{ detailRow.memberName }}</el-descriptions-item>
      <el-descriptions-item label="商品 / 服务">
        {{ detailRow.spuName }}（{{ enumLabel(SUPPLY_CAT_PROTO, detailRow.cat) }}）
      </el-descriptions-item>
      <el-descriptions-item label="数量 / 金额">
        {{ detailRow.qty }} {{ detailRow.unit }} / {{ formatMoney(detailRow.amount) }}
      </el-descriptions-item>
      <el-descriptions-item label="绑定地块">{{ detailRow.assetCode }}</el-descriptions-item>
      <el-descriptions-item label="交付方式">
        {{ enumLabel(DELIVER_WAYS, detailRow.deliverWay) }}
      </el-descriptions-item>
      <el-descriptions-item label="批次号">{{ detailRow.batchNo || '-' }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ detailRow.stateName }}</el-descriptions-item>
      <el-descriptions-item label="投入品留痕">
        {{ enumLabel(TRACE_STATE, detailRow.traceState) }}
        <template v-if="detailRow.traceState === 10">
          （{{ fmtTime(detailRow.usedAt) }} · {{ detailRow.dosage }}）
        </template>
      </el-descriptions-item>
      <el-descriptions-item label="下单时间">{{ fmtTime(detailRow.createTime) }}</el-descriptions-item>
    </el-descriptions>
  </Dialog>

  <Dialog v-model="traceVisible" title="登记施用" width="520px">
    <div class="mb-12px text-13px">
      {{ traceRow?.spuName }}（批次 {{ traceRow?.batchNo || '-' }}）用于地块
      {{ traceRow?.assetCode }}
    </div>
    <el-form ref="traceFormRef" :model="traceForm" :rules="traceRules" label-width="90px">
      <el-form-item label="施用时间" prop="usedAt">
        <el-date-picker
          v-model="traceForm.usedAt"
          type="datetime"
          value-format="x"
          placeholder="选择时间"
        />
      </el-form-item>
      <el-form-item label="用量" prop="dosage">
        <el-input v-model="traceForm.dosage" placeholder="如：一套 / 2 袋 / 每亩 50 公斤" />
      </el-form-item>
    </el-form>
    <div class="text-12px text-[var(--el-text-color-secondary)] pl-90px">
      登记后写入地块的投入品档案，认养人在 C 端溯源页能看到批次和施用时间。
    </div>
    <template #footer>
      <el-button type="primary" :loading="tracing" @click="submitTrace">确 定</el-button>
      <el-button @click="traceVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus'
import * as SupplyApi from '@/api/farm/supply'
import { DELIVER_WAYS, TRACE_STATE, enumLabel, enumOf, fmtTime, formatMoney } from '../constants'
import StatCards from '../fulfill/StatCards.vue'
import {
  SUPPLY_CAT_PROTO,
  SUPPLY_ORDER_STATES,
  SUPPLY_ORDER_STATE_TYPE
} from '../supply/constants'

defineOptions({ name: 'FarmSupplyOrder' })

const message = useMessage()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const queryParams = reactive<any>({
  pageNo: 1,
  pageSize: 10,
  keyword: undefined,
  assetCode: undefined,
  state: undefined
})
const stats = ref<any>({})
const statItems = computed(() => {
  const s = stats.value
  return [
    {
      label: '今日订单',
      value: `${s.todayCount ?? 0} 单`,
      sub: `金额 ${formatMoney(s.todayAmount ?? 0)}`
    },
    {
      label: '待派单服务',
      value: `${s.pendingDispatch ?? 0} 单`,
      sub: '需在 24h 内接单',
      tone: s.pendingDispatch ? ('warn' as const) : undefined
    },
    {
      label: '未登记施用',
      value: `${s.untraced ?? 0} 单`,
      sub: '系统强制留痕',
      tone: s.untraced ? ('warn' as const) : undefined
    },
    {
      label: '农资复购人数',
      value: `${s.repeatMemberCount ?? 0} 人`,
      sub: `占下单认养人 ${s.repeatRate ?? 0}%`
    }
  ]
})
const notOpen = () => message.info('演示版暂未开放')

/** 导出台账：把当前页导成 CSV（带 BOM，Excel 直接打开不乱码） */
const exportCsv = () => {
  if (!list.value.length) return message.warning('当前没有可导出的订单')
  const head = ['订单号', '认养人', '商品/服务', '数量', '金额(元)', '绑定地块', '交付方式', '批次号', '状态', '投入品留痕', '施用时间', '用量']
  const rows = list.value.map((r) => [
    r.no,
    r.memberName,
    r.spuName,
    `${r.qty}${r.unit}`,
    (Number(r.amount) / 100).toFixed(2),
    r.assetCode,
    enumLabel(DELIVER_WAYS, r.deliverWay),
    r.batchNo || '',
    r.stateName,
    enumLabel(TRACE_STATE, r.traceState),
    r.traceState === 10 ? fmtTime(r.usedAt) : '',
    r.dosage || ''
  ])
  const csv = [head, ...rows]
    .map((line) => line.map((c) => `"${String(c ?? '').replace(/"/g, '""')}"`).join(','))
    .join('\r\n')
  const url = URL.createObjectURL(new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url
  a.download = `农资订单台账-${fmtTime(Date.now(), 'YYYYMMDD-HHmm')}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

const detailVisible = ref(false)
const detailRow = ref<any>()
const openDetail = (row: any) => {
  detailRow.value = row
  detailVisible.value = true
}

const getList = async () => {
  loading.value = true
  try {
    const res = await SupplyApi.getSupplyOrderPage(queryParams)
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
const resetQuery = () => {
  queryParams.keyword = undefined
  queryParams.assetCode = undefined
  queryParams.state = undefined
  handleQuery()
}

const traceVisible = ref(false)
const tracing = ref(false)
const traceRow = ref<any>()
const traceFormRef = ref<FormInstance>()
const traceForm = ref<{ usedAt?: number; dosage: string }>({ dosage: '' })
const traceRules: FormRules = {
  usedAt: [{ required: true, message: '请填写施用时间', trigger: 'change' }],
  dosage: [{ required: true, message: '请填写用量', trigger: 'blur' }]
}
const openTrace = (row: any) => {
  traceRow.value = row
  traceForm.value = { usedAt: Date.now(), dosage: '' }
  traceVisible.value = true
  nextTick(() => traceFormRef.value?.clearValidate())
}
const submitTrace = async () => {
  if (!(await traceFormRef.value?.validate().catch(() => false))) return
  tracing.value = true
  try {
    await SupplyApi.traceSupplyOrder({
      id: traceRow.value.id,
      usedAt: Number(traceForm.value.usedAt),
      dosage: traceForm.value.dosage.trim()
    })
    message.success('已登记，投入品档案已写入')
    traceVisible.value = false
    getList()
  } finally {
    tracing.value = false
  }
}

onMounted(getList)
// keep-alive 切回来时刷新（从工作台待办跳过来、或在别的页面改过数据）
let mounted = false
onActivated(() => {
  if (mounted) getList()
  mounted = true
})
</script>
