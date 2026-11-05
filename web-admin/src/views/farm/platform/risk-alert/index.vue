<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :inline="true"
      :model="queryParams"
      class="-mb-15px"
      label-width="68px"
    >
      <el-form-item label="租户" prop="tenantId">
        <el-select v-model="queryParams.tenantId" class="!w-200px" clearable placeholder="全部租户">
          <el-option v-for="t in tenantList" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型" prop="type">
        <el-select v-model="queryParams.type" class="!w-160px" clearable placeholder="全部类型">
          <el-option
            v-for="t in RISK_ALERT_TYPES"
            :key="t.value"
            :label="t.label"
            :value="t.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" class="!w-160px" clearable placeholder="全部状态">
          <el-option
            v-for="s in RISK_ALERT_STATUS"
            :key="s.value"
            :label="s.label"
            :value="s.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true">
      <el-table-column label="等级" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="findEnum(RISK_ALERT_LEVELS, row.level)?.tag ?? 'info'" effect="dark">
            {{ findEnum(RISK_ALERT_LEVELS, row.level)?.label ?? row.level }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="租户" prop="tenantName" width="130" />
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag :type="findEnum(RISK_ALERT_TYPES, row.type)?.tag ?? 'info'">
            {{ findEnum(RISK_ALERT_TYPES, row.type)?.label ?? row.type }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="预警内容" min-width="260">
        <template #default="{ row }">
          <div class="font-600">{{ row.title }}</div>
          <div class="text-12px text-[var(--el-text-color-secondary)]">{{ row.content }}</div>
        </template>
      </el-table-column>
      <el-table-column label="来源" prop="source" width="120" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="findEnum(RISK_ALERT_STATUS, row.status)?.tag ?? 'info'">
            {{ findEnum(RISK_ALERT_STATUS, row.status)?.label ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="处理说明" prop="handleRemark" min-width="160" show-overflow-tooltip />
      <el-table-column label="预警时间" prop="createTime" width="170" :formatter="dateFormatter" />
      <el-table-column label="操作" width="90" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" link type="primary" @click="openHandle(row)"
            >处理</el-button
          >
          <span v-else class="text-12px text-[var(--el-text-color-secondary)]">
            {{ row.handleTime ? formatDate(row.handleTime, 'MM-DD HH:mm') : '' }}
          </span>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <Dialog v-model="handleVisible" title="处理预警" width="520px">
    <div v-if="current" class="mb-12px">
      <div class="font-600">{{ current.tenantName }} · {{ current.title }}</div>
      <div class="mt-4px text-13px text-[var(--el-text-color-secondary)]">{{
        current.content
      }}</div>
    </div>
    <el-form ref="handleFormRef" :model="handleForm" :rules="handleRules" label-width="80px">
      <el-form-item label="处理结果" prop="status">
        <el-radio-group v-model="handleForm.status">
          <el-radio :value="10">已处理</el-radio>
          <el-radio :value="20">已忽略</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="处理说明" prop="handleRemark">
        <el-input
          v-model="handleForm.handleRemark"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="例如：已联系农场下架相关文案；核实为误报"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleVisible = false">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitHandle">确 定</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus'
import { dateFormatter, formatDate } from '@/utils/formatTime'
import * as PlatformApi from '@/api/farm/platform'
import * as TenantApi from '@/api/system/tenant'
import { RISK_ALERT_LEVELS, RISK_ALERT_STATUS, RISK_ALERT_TYPES, findEnum } from '../constants'

defineOptions({ name: 'FarmRiskAlert' })

const message = useMessage()

const loading = ref(false)
const list = ref<PlatformApi.RiskAlertVO[]>([])
const total = ref(0)
const queryFormRef = ref<FormInstance>()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  tenantId: undefined as number | undefined,
  type: undefined as string | undefined,
  status: undefined as number | undefined
})
const tenantList = ref<{ id: number; name: string }[]>([])

const getList = async () => {
  loading.value = true
  try {
    const data = await PlatformApi.getRiskAlertPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

// ---------- 处理 ----------
const handleVisible = ref(false)
const submitting = ref(false)
const current = ref<PlatformApi.RiskAlertVO>()
const handleFormRef = ref<FormInstance>()
const handleForm = reactive({ status: 10, handleRemark: '' })
const handleRules: FormRules = {
  status: [{ required: true, message: '请选择处理结果', trigger: 'change' }],
  handleRemark: [{ required: true, whitespace: true, message: '请填写处理说明', trigger: 'blur' }]
}
const openHandle = (row: PlatformApi.RiskAlertVO) => {
  current.value = row
  handleForm.status = 10
  handleForm.handleRemark = ''
  handleVisible.value = true
  nextTick(() => handleFormRef.value?.clearValidate())
}
const submitHandle = async () => {
  if (!(await handleFormRef.value?.validate().catch(() => false))) return
  submitting.value = true
  try {
    await PlatformApi.handleRiskAlert({
      id: current.value!.id,
      status: handleForm.status,
      handleRemark: handleForm.handleRemark.trim()
    })
    message.success('已处理')
    handleVisible.value = false
    await getList()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  getList()
  const tenants = (await TenantApi.getTenantList()) ?? []
  // 平台租户自己不会有预警，不放进筛选
  tenantList.value = tenants.filter((t: { id: number }) => t.id !== 1)
})
</script>
