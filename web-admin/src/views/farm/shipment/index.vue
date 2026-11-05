<template>
  <ContentWrap>
    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="配送单号">
        <el-input
          v-model="queryParams.no"
          placeholder="PS..."
          clearable
          class="!w-200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部" clearable class="!w-120px">
          <el-option
            v-for="s in SHIPMENT_STATUS"
            :key="s.value"
            :label="s.label"
            :value="s.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button type="primary" plain @click="openForm">
          <Icon icon="ep:van" class="mr-5px" />新建配送
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="px-40px py-8px">
            <el-timeline>
              <el-timeline-item
                v-for="(t, i) in [...row.tracks].reverse()"
                :key="i"
                :timestamp="fmtTime(t.time)"
                :type="i === 0 ? 'primary' : undefined"
              >
                {{ t.content }}
              </el-timeline-item>
            </el-timeline>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="配送单号" prop="no" width="160" />
      <el-table-column label="地块" prop="assetCode" width="80" />
      <el-table-column label="收货人" min-width="220">
        <template #default="{ row }">
          <div>{{ row.receiverName }} {{ row.receiverMobile }}</div>
          <div class="text-12px text-[var(--el-text-color-secondary)]">{{ row.address }}</div>
        </template>
      </el-table-column>
      <el-table-column label="重量" width="90" align="right">
        <template #default="{ row }">{{ row.weightJin }} 斤</template>
      </el-table-column>
      <el-table-column label="承运" min-width="170">
        <template #default="{ row }">
          <div>
            {{ row.carrier }}
            <el-tag v-if="row.coldChain" size="small" effect="plain">冷链</el-tag>
          </div>
          <div class="text-12px text-[var(--el-text-color-secondary)]">{{ row.logisticsNo }}</div>
        </template>
      </el-table-column>
      <el-table-column label="最新轨迹" min-width="200">
        <template #default="{ row }">
          <template v-if="row.tracks?.length">{{
            row.tracks[row.tracks.length - 1].content
          }}</template>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="enumOf(SHIPMENT_STATUS, row.status).type">
            {{ enumLabel(SHIPMENT_STATUS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="150" :formatter="timeFormatter" />
      <el-table-column label="操作" width="100" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 10" link type="primary" @click="handleSign(row)">
            确认签收
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

  <Dialog v-model="dialogVisible" title="新建配送批次" width="600px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" v-loading="formLoading">
      <el-form-item label="收成" prop="harvestId">
        <el-select v-model="form.harvestId" placeholder="选择一批未发货的收成" class="!w-full">
          <el-option
            v-for="h in harvests"
            :key="h.id"
            :label="`${h.assetCode} · ${h.memberName} · ${h.crop} ${h.weightJin} 斤（${fmtDay(h.harvestDate)}）`"
            :value="h.id"
          />
        </el-select>
        <div v-if="!harvests.length" class="text-12px text-[var(--el-text-color-secondary)]">
          没有未发货的收成，先去「收成录入」录一批。
        </div>
      </el-form-item>
      <el-form-item label="承运商" prop="carrier">
        <el-input v-model="form.carrier" placeholder="如：顺丰冷运" />
      </el-form-item>
      <el-form-item label="物流单号" prop="logisticsNo">
        <el-input v-model="form.logisticsNo" placeholder="快递单号" />
      </el-form-item>
      <el-form-item label="冷链">
        <el-switch v-model="form.coldChain" />
      </el-form-item>
      <div class="text-12px text-[var(--el-text-color-secondary)] pl-90px">
        收货地址取认养订单上的地址，没有填就用认养人资料里的地址。
      </div>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submitForm">发 货</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus'
import * as HarvestApi from '@/api/farm/harvest'
import { SHIPMENT_STATUS, enumLabel, enumOf, fmtDay, fmtTime, timeFormatter } from '../constants'

defineOptions({ name: 'FarmShipment' })

const message = useMessage()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const queryParams = reactive<any>({ pageNo: 1, pageSize: 10, no: undefined, status: undefined })

const getList = async () => {
  loading.value = true
  try {
    const res = await HarvestApi.getShipmentPage(queryParams)
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
  queryParams.status = undefined
  handleQuery()
}

const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const harvests = ref<any[]>([])
const form = ref<any>({})
const rules: FormRules = {
  harvestId: [{ required: true, message: '请选择收成', trigger: 'change' }],
  carrier: [{ required: true, message: '请填写承运商', trigger: 'blur' }],
  logisticsNo: [{ required: true, message: '请填写物流单号', trigger: 'blur' }]
}
const openForm = async () => {
  form.value = { harvestId: undefined, carrier: '顺丰冷运', logisticsNo: '', coldChain: true }
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
  formLoading.value = true
  try {
    const res = await HarvestApi.getHarvestPage({ pageNo: 1, pageSize: 100, shipped: false })
    harvests.value = res.list
    if (harvests.value.length === 1) form.value.harvestId = harvests.value[0].id
  } finally {
    formLoading.value = false
  }
}
const submitForm = async () => {
  if (!(await formRef.value?.validate().catch(() => false))) return
  formLoading.value = true
  try {
    await HarvestApi.createShipment(form.value)
    message.success('已发货，状态为运输中')
    dialogVisible.value = false
    getList()
  } finally {
    formLoading.value = false
  }
}
const handleSign = async (row: any) => {
  try {
    await message.confirm(`确认配送单 ${row.no} 已签收？`)
  } catch {
    return
  }
  await HarvestApi.signShipment(row.id)
  message.success('已确认签收')
  getList()
}

onMounted(getList)
// keep-alive 切回来时刷新（从工作台待办跳过来、或在别的页面改过数据）
let mounted = false
onActivated(() => {
  if (mounted) getList()
  mounted = true
})
</script>
