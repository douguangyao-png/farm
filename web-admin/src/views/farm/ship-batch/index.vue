<template>
  <ContentWrap>
    <div class="flex items-start justify-between mb-8px">
      <div>
        <div class="text-18px font-600">配送批次</div>
        <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
          把同一批收成的配送单归为一个批次，统一发出、统一收尾；批次发出后，下属配送单一并置为已发出
        </div>
      </div>
      <el-button v-hasPermi="['farm:shipbatch:create']" type="primary" @click="openCreate">
        <Icon icon="ep:plus" class="mr-5px" />新建批次
      </el-button>
    </div>
    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部" clearable class="!w-140px">
          <el-option v-for="s in BATCH_STATUS" :key="s.value" :label="s.label" :value="s.value" />
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
      <el-table-column label="批次号" prop="no" width="150">
        <template #default="{ row }">
          <el-link type="primary" @click="openDetail(row.id)">{{ row.no }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="计划日期" width="120">
        <template #default="{ row }">{{ fmtDay(row.planDate) }}</template>
      </el-table-column>
      <el-table-column label="收成" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.harvestLabel || row.harvestCrop || (row.harvestId ? '收成 #' + row.harvestId : '-') }}
        </template>
      </el-table-column>
      <el-table-column label="配送单数" width="100" align="right">
        <template #default="{ row }">{{ row.shipmentCount ?? row.shipments?.length ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="enumOf(BATCH_STATUS, row.status).type">
            {{ enumLabel(BATCH_STATUS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip />
      <el-table-column label="创建时间" width="150" :formatter="timeFormatter" prop="createTime" />
      <el-table-column label="操作" width="220" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-button
            v-if="row.status === 0"
            v-hasPermi="['farm:shipbatch:ship']"
            link
            type="warning"
            @click="handleShip(row)"
          >
            发出
          </el-button>
          <el-button
            v-if="row.status === 10"
            v-hasPermi="['farm:shipbatch:finish']"
            link
            type="success"
            @click="handleFinish(row)"
          >
            完成
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

  <!-- 新建 -->
  <Dialog v-model="createVisible" title="新建配送批次" width="720px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" v-loading="formLoading">
      <el-form-item label="收成" prop="harvestId">
        <el-select
          v-model="form.harvestId"
          placeholder="选择一批收成"
          filterable
          class="!w-full"
          @change="onHarvestChange"
        >
          <el-option
            v-for="h in harvests"
            :key="h.id"
            :label="`${h.assetCode} · ${h.memberName} · ${h.crop} ${h.weightJin} 斤（${fmtDay(h.harvestDate)}）`"
            :value="h.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="计划日期" prop="planDate">
        <el-date-picker v-model="form.planDate" type="date" value-format="x" placeholder="选择日期" />
      </el-form-item>
      <el-form-item label="配送单" prop="shipmentIds">
        <div class="w-full">
          <el-table
            ref="pickRef"
            :data="candidates"
            size="small"
            border
            max-height="260"
            empty-text="这批收成下没有可归批的配送单（需要先有配送单且未归入其他批次）"
            @selection-change="onSelect"
          >
            <el-table-column type="selection" width="45" />
            <el-table-column label="配送单号" prop="no" width="150" />
            <el-table-column label="收货人" min-width="160">
              <template #default="{ row }">{{ row.receiverName }} {{ row.receiverMobile }}</template>
            </el-table-column>
            <el-table-column label="重量" width="80" align="right">
              <template #default="{ row }">{{ row.weightJin }} 斤</template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">{{ enumLabel(SHIP_STATUS, row.status) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" placeholder="选填" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submitCreate">创 建</el-button>
      <el-button @click="createVisible = false">取 消</el-button>
    </template>
  </Dialog>

  <!-- 详情 -->
  <el-drawer v-model="drawerVisible" :title="`批次详情 · ${detail?.no ?? ''}`" size="560px">
    <div v-loading="detailLoading">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="批次号">{{ detail.no }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="enumOf(BATCH_STATUS, detail.status).type">
              {{ enumLabel(BATCH_STATUS, detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="计划日期">{{ fmtDay(detail.planDate) }}</el-descriptions-item>
          <el-descriptions-item label="收成">
            {{ detail.harvestLabel || detail.harvestCrop || '#' + detail.harvestId }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="font-600 my-12px">配送单（{{ detail.shipments?.length ?? 0 }}）</div>
        <el-table :data="detail.shipments || []" size="small" border empty-text="批次下没有配送单">
          <el-table-column label="配送单号" prop="no" width="150" />
          <el-table-column label="收货人" min-width="150">
            <template #default="{ row }">
              <div>{{ row.receiverName }} {{ row.receiverMobile }}</div>
              <div class="text-12px text-[var(--el-text-color-secondary)]">{{ row.address }}</div>
            </template>
          </el-table-column>
          <el-table-column label="重量" width="70" align="right">
            <template #default="{ row }">{{ row.weightJin }} 斤</template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="enumOf(SHIP_STATUS, row.status).type">
                {{ enumLabel(SHIP_STATUS, row.status) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </div>
    <template #footer>
      <el-button
        v-if="detail?.status === 0"
        v-hasPermi="['farm:shipbatch:ship']"
        type="warning"
        @click="handleShip(detail)"
      >
        发 出
      </el-button>
      <el-button
        v-if="detail?.status === 10"
        v-hasPermi="['farm:shipbatch:finish']"
        type="success"
        @click="handleFinish(detail)"
      >
        完 成
      </el-button>
      <el-button @click="drawerVisible = false">关 闭</el-button>
    </template>
  </el-drawer>
</template>
<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus'
import * as ShipBatchApi from '@/api/farm/shipBatch'
import * as HarvestApi from '@/api/farm/harvest'
import { enumLabel, enumOf, fmtDay, timeFormatter } from '../constants'
import { SHIP_STATUS } from '../shipment/constants'
import { BATCH_STATUS } from './constants'

defineOptions({ name: 'FarmShipBatch' })

const message = useMessage()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const queryParams = reactive<any>({ pageNo: 1, pageSize: 10, status: undefined })

const getList = async () => {
  loading.value = true
  try {
    const res = await ShipBatchApi.getShipBatchPage(queryParams)
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
  queryParams.status = undefined
  handleQuery()
}

// ---------- 新建 ----------
const createVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const pickRef = ref()
const harvests = ref<any[]>([])
const allShipments = ref<any[]>([])
const form = ref<any>({})
const candidates = computed(() =>
  allShipments.value.filter((s) => s.harvestId === form.value.harvestId && !s.batchId)
)
const rules: FormRules = {
  harvestId: [{ required: true, message: '请选择收成', trigger: 'change' }],
  planDate: [{ required: true, message: '请选择计划日期', trigger: 'change' }],
  shipmentIds: [
    {
      validator: (_r: any, v: any, cb: (e?: Error) => void) =>
        v?.length ? cb() : cb(new Error('请至少勾选一张配送单')),
      trigger: 'change'
    }
  ]
}
const onSelect = (rows: any[]) => {
  form.value.shipmentIds = rows.map((r) => r.id)
}
const onHarvestChange = () => {
  form.value.shipmentIds = []
  nextTick(() => pickRef.value?.clearSelection())
}
const openCreate = async () => {
  form.value = { harvestId: undefined, planDate: Date.now(), remark: '', shipmentIds: [] }
  createVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
  formLoading.value = true
  try {
    const [h, s] = await Promise.all([
      HarvestApi.getHarvestPage({ pageNo: 1, pageSize: 100 }),
      HarvestApi.getShipmentPage({ pageNo: 1, pageSize: 100 })
    ])
    harvests.value = h.list
    allShipments.value = s.list
  } finally {
    formLoading.value = false
  }
}
const submitCreate = async () => {
  if (!(await formRef.value?.validate().catch(() => false))) return
  formLoading.value = true
  try {
    const f = form.value
    await ShipBatchApi.createShipBatch({
      harvestId: f.harvestId,
      planDate: Number(f.planDate),
      remark: f.remark?.trim() || undefined,
      shipmentIds: f.shipmentIds
    })
    message.success('批次已创建，状态为待发')
    createVisible.value = false
    getList()
  } finally {
    formLoading.value = false
  }
}

// ---------- 详情与状态流转 ----------
const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<any>()
const openDetail = async (id: number) => {
  drawerVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await ShipBatchApi.getShipBatch(id)
  } finally {
    detailLoading.value = false
  }
}
const handleShip = async (row: any) => {
  try {
    await message.confirm(`确认发出批次 ${row.no}？下属配送单会全部置为已发出。`)
  } catch {
    return
  }
  await ShipBatchApi.shipShipBatch(row.id)
  message.success('批次已发出')
  getList()
  if (drawerVisible.value) openDetail(row.id)
}
const handleFinish = async (row: any) => {
  try {
    await message.confirm(`确认批次 ${row.no} 已完成？`)
  } catch {
    return
  }
  await ShipBatchApi.finishShipBatch(row.id)
  message.success('批次已完成')
  getList()
  if (drawerVisible.value) openDetail(row.id)
}

onMounted(getList)
let mounted = false
onActivated(() => {
  if (mounted) getList()
  mounted = true
})
</script>
