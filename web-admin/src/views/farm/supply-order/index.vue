<template>
  <ContentWrap>
    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="订单号">
        <el-input
          v-model="queryParams.no"
          placeholder="NZ..."
          clearable
          class="!w-200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="留痕">
        <el-select v-model="queryParams.traceState" placeholder="全部" clearable class="!w-120px">
          <el-option v-for="s in TRACE_STATE" :key="s.value" :label="s.label" :value="s.value" />
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
      <el-table-column label="农资" min-width="180">
        <template #default="{ row }">
          <div>{{ row.spuName }}</div>
          <el-tag size="small" :type="enumOf(SUPPLY_CATS, row.cat).type" effect="plain">
            {{ enumLabel(SUPPLY_CATS, row.cat) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="数量" width="80" align="center">
        <template #default="{ row }">{{ row.qty }} {{ row.unit }}</template>
      </el-table-column>
      <el-table-column
        label="金额"
        prop="amount"
        width="100"
        align="right"
        :formatter="moneyFormatter"
      />
      <el-table-column label="认养人" prop="memberName" width="100" />
      <el-table-column label="用于地块" prop="assetCode" width="90" />
      <el-table-column label="交付方式" width="100">
        <template #default="{ row }">{{ enumLabel(DELIVER_WAYS, row.deliverWay) }}</template>
      </el-table-column>
      <el-table-column label="批次号" prop="batchNo" width="130" />
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
      <el-table-column label="下单时间" prop="createTime" width="150" :formatter="timeFormatter" />
      <el-table-column label="操作" width="100" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.traceState === 0" link type="primary" @click="openTrace(row)">
            登记施用
          </el-button>
          <span v-else>-</span>
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
import {
  DELIVER_WAYS,
  SUPPLY_CATS,
  TRACE_STATE,
  enumLabel,
  enumOf,
  fmtTime,
  moneyFormatter,
  timeFormatter
} from '../constants'

defineOptions({ name: 'FarmSupplyOrder' })

const message = useMessage()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const queryParams = reactive<any>({ pageNo: 1, pageSize: 10, no: undefined, traceState: undefined })

const getList = async () => {
  loading.value = true
  try {
    const res = await SupplyApi.getSupplyOrderPage(queryParams)
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}
const resetQuery = () => {
  queryParams.no = undefined
  queryParams.traceState = undefined
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
