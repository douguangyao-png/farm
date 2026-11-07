<template>
  <div class="flex items-start justify-between mb-12px">
    <div>
      <div class="text-18px font-600">
        交易流水
        <el-tag type="warning" size="small" effect="plain" class="ml-6px">演示数据</el-tag>
      </div>
      <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
        所有资金由第三方支付机构在经营者对公账户内结算，平台仅收取服务费
      </div>
    </div>
    <div class="flex gap-8px">
      <el-button @click="notOpen">下载对账单</el-button>
      <el-button @click="notOpen">导出凭证</el-button>
    </div>
  </div>

  <StatCards :items="statItems" />

  <ContentWrap>
    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="交易号">
        <el-input v-model="queryParams.tradeNo" placeholder="T..." clearable class="!w-180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="客户">
        <el-input v-model="queryParams.memberName" placeholder="认养人昵称" clearable class="!w-160px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="queryParams.type" clearable placeholder="全部" class="!w-140px">
          <el-option v-for="t in FLOW_TYPES" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="结算状态">
        <el-select v-model="queryParams.settleStatus" clearable placeholder="全部" class="!w-130px">
          <el-option v-for="t in SETTLE_STATUS" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="时间">
        <el-date-picker
          v-model="range"
          type="daterange"
          value-format="x"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="交易号" prop="tradeNo" width="150" />
      <el-table-column label="客户" prop="memberName" width="100" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="enumOf(FLOW_TYPES, row.type).type" effect="plain">{{ row.typeName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="关联单号" prop="refNo" min-width="150" />
      <el-table-column label="交易金额" width="120" align="right">
        <template #default="{ row }"><b>{{ formatMoney(row.amount) }}</b></template>
      </el-table-column>
      <el-table-column label="服务费" width="100" align="right">
        <template #default="{ row }">{{ formatMoney(row.fee) }}</template>
      </el-table-column>
      <el-table-column label="到账金额" width="120" align="right">
        <template #default="{ row }"><b>{{ formatMoney(row.net) }}</b></template>
      </el-table-column>
      <el-table-column label="结算状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="enumOf(SETTLE_STATUS, row.settleStatus).type">{{ row.settleStatusName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ fmtTime(row.time) }}</template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <el-alert
    type="success"
    :closable="false"
    show-icon
    title="资金直达经营者对公账户，平台不归集、不二次清分；每笔交易可在支付机构后台独立核对。本页流水由订单派生，服务费 3%、T+1 结算为演示口径，未接入真实支付。"
  />
</template>
<script lang="ts" setup>
import * as FinanceApi from '@/api/farm/finance'
import { enumOf, fmtTime, formatMoney } from '../constants'
import StatCards from '../fulfill/StatCards.vue'
import { FLOW_TYPES, SETTLE_STATUS } from './constants'

defineOptions({ name: 'FarmFinanceFlow' })

const message = useMessage()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const stats = ref<any>({})
const range = ref<string[] | null>(null)
const queryParams = reactive<any>({
  pageNo: 1,
  pageSize: 10,
  tradeNo: undefined,
  memberName: undefined,
  type: undefined,
  settleStatus: undefined,
  beginTime: undefined,
  endTime: undefined
})
const notOpen = () => message.info('演示版暂未开放')

const statItems = computed(() => {
  const s = stats.value
  const g = s.last30Growth
  return [
    { label: '今日收款', value: formatMoney(s.todayAmount ?? 0), sub: `${s.todayCount ?? 0} 笔` },
    {
      label: '近 30 天收款',
      value: formatMoney(s.last30Amount ?? 0),
      sub: g == null ? `${s.last30Count ?? 0} 笔` : `较前 30 天 ${g >= 0 ? '+' : ''}${g}%`,
      tone: g != null && g >= 0 ? ('up' as const) : ('muted' as const)
    },
    {
      label: '平台服务费',
      value: formatMoney(s.last30Fee ?? 0),
      sub: `费率 ${s.feeRate ?? 3}%（近 30 天）`
    },
    {
      label: '待结算',
      value: formatMoney(s.pendingAmount ?? 0),
      sub: `${s.pendingCount ?? 0} 笔 · T+1 自动到账`
    }
  ]
})

const getList = async () => {
  loading.value = true
  try {
    queryParams.beginTime = range.value ? Number(range.value[0]) : undefined
    // 结束日期含当天
    queryParams.endTime = range.value ? Number(range.value[1]) + 86400_000 - 1 : undefined
    const res = await FinanceApi.getFlowPage(queryParams)
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
  queryParams.tradeNo = undefined
  queryParams.memberName = undefined
  queryParams.type = undefined
  queryParams.settleStatus = undefined
  range.value = null
  handleQuery()
}

onMounted(getList)
let mounted = false
onActivated(() => {
  if (mounted) getList()
  mounted = true
})
</script>
