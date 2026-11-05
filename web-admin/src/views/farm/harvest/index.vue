<template>
  <ContentWrap>
    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="地块编号">
        <el-input
          v-model="queryParams.assetCode"
          placeholder="如 A-01"
          clearable
          class="!w-160px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="发货">
        <el-select v-model="queryParams.shipped" placeholder="全部" clearable class="!w-120px">
          <el-option label="未发货" value="false" />
          <el-option label="已发货" value="true" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button type="primary" plain @click="openForm">
          <Icon icon="ep:plus" class="mr-5px" />录入收成
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="采收日期" width="120">
        <template #default="{ row }">{{ fmtDay(row.harvestDate) }}</template>
      </el-table-column>
      <el-table-column label="地块" prop="assetCode" width="90" />
      <el-table-column label="认养人" prop="memberName" width="110" />
      <el-table-column label="作物" prop="crop" min-width="140" />
      <el-table-column label="重量" width="100" align="right">
        <template #default="{ row }">{{ row.weightJin }} 斤</template>
      </el-table-column>
      <el-table-column label="照片" width="100">
        <template #default="{ row }">
          <el-image
            v-for="(p, i) in row.photos"
            :key="i"
            :src="p"
            :preview-src-list="row.photos"
            preview-teleported
            fit="cover"
            class="w-40px h-40px rounded-4px mr-4px"
          />
        </template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" min-width="140" show-overflow-tooltip />
      <el-table-column label="发货" width="140" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.shipmentId" type="success">已发货</el-tag>
          <el-button v-else link type="primary" @click="router.push('/farm-order/shipment')">
            去发货
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

  <Dialog v-model="dialogVisible" title="录入收成" width="560px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" v-loading="formLoading">
      <el-form-item label="认养关系" prop="adoptionId">
        <el-select
          v-model="form.adoptionId"
          placeholder="选择地块 / 认养人"
          filterable
          class="!w-full"
        >
          <el-option v-for="a in adoptions" :key="a.id" :label="a.label" :value="a.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="作物" prop="crop">
        <el-input v-model="form.crop" placeholder="如：番茄、黄瓜" />
      </el-form-item>
      <el-form-item label="重量" prop="weightJin">
        <el-input-number
          v-model="form.weightJin"
          :min="0.1"
          :precision="1"
          :step="0.5"
          controls-position="right"
        />
        <span class="ml-6px">斤</span>
      </el-form-item>
      <el-form-item label="采收日期" prop="harvestDate">
        <el-date-picker
          v-model="form.harvestDate"
          type="date"
          value-format="x"
          placeholder="选择日期"
        />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" placeholder="如：第二批" />
      </el-form-item>
      <div class="text-12px text-[var(--el-text-color-secondary)] pl-90px">
        保存后累加到该认养的「已收斤数」，并在认养人的生长档案里多一条采收记录。
      </div>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus'
import * as HarvestApi from '@/api/farm/harvest'
import { fmtDay } from '../constants'

defineOptions({ name: 'FarmHarvest' })

const message = useMessage()
const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const queryParams = reactive<any>({
  pageNo: 1,
  pageSize: 10,
  assetCode: undefined,
  shipped: undefined
})

const getList = async () => {
  loading.value = true
  try {
    const res = await HarvestApi.getHarvestPage(queryParams)
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
  queryParams.assetCode = undefined
  queryParams.shipped = undefined
  handleQuery()
}

const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const adoptions = ref<Awaited<ReturnType<typeof HarvestApi.getAdoptionSimpleList>>>([])
const form = ref<any>({})
const rules: FormRules = {
  adoptionId: [{ required: true, message: '请选择认养关系', trigger: 'change' }],
  crop: [{ required: true, message: '请填写作物', trigger: 'blur' }],
  weightJin: [{ required: true, message: '请填写重量', trigger: 'blur' }],
  harvestDate: [{ required: true, message: '请选择采收日期', trigger: 'change' }]
}
const openForm = async () => {
  form.value = {
    adoptionId: undefined,
    crop: '',
    weightJin: undefined,
    harvestDate: new Date(new Date().toDateString()).getTime(),
    remark: ''
  }
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
  adoptions.value = await HarvestApi.getAdoptionSimpleList()
}
const submitForm = async () => {
  if (!(await formRef.value?.validate().catch(() => false))) return
  formLoading.value = true
  try {
    await HarvestApi.createHarvest({ ...form.value, harvestDate: Number(form.value.harvestDate) })
    message.success('收成已录入')
    dialogVisible.value = false
    getList()
  } finally {
    formLoading.value = false
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
