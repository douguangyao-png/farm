<!-- 权限串：farm:compliancecert:query / create / update。基地备案证来自基地资料（只读），其余预置证照为演示数据 -->
<template>
  <div v-loading="loading">
    <div class="flex items-start justify-between mb-12px">
      <div>
        <div class="text-18px font-600">资质证照</div>
        <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
          到期前 30 天自动提醒；未上传必备证照将限制商品上架
        </div>
      </div>
      <el-button v-hasPermi="['farm:compliancecert:create']" type="primary" @click="openForm">
        <Icon icon="ep:upload" class="mr-5px" />上传证照
      </el-button>
    </div>

    <StatCards :items="statItems" />

    <ContentWrap>
      <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
        <el-form-item label="证照名称">
          <el-input v-model="queryParams.name" clearable class="!w-200px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.type" clearable placeholder="全部" class="!w-120px">
            <el-option v-for="t in CERT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
          <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <ContentWrap>
      <el-table :data="list">
        <el-table-column label="证照名称" min-width="190">
          <template #default="{ row }">
            <b>{{ row.name }}</b>
            <el-tag v-if="row.demo" type="warning" size="small" effect="plain" class="ml-6px"
              >演示</el-tag
            >
          </template>
        </el-table-column>
        <el-table-column label="编号" prop="no" min-width="150" />
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="enumOf(CERT_TYPES, row.type).type" effect="plain">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="有效期至" width="190">
          <template #default="{ row }">
            {{ row.validUntil ? fmtDay(row.validUntil) : '长期' }}
            <el-tag v-if="row.expiringSoon" type="danger" size="small" class="ml-4px">
              {{ row.daysLeft }} 天内到期
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="核验状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="enumOf(VERIFY_STATUS, row.verify).type">{{ row.verifyName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="是否向 C 端公示" min-width="200">
          <template #default="{ row }">
            <el-switch
              v-if="row.editable"
              :model-value="row.publicMode === 'public'"
              inline-prompt
              active-text="公示"
              inactive-text="不公示"
              @change="(v: any) => togglePublic(row, !!v)"
            />
            <span v-else>{{ row.publicName }}</span>
            <span
              v-if="row.publicMode === 'private'"
              class="ml-6px text-12px text-[var(--el-text-color-secondary)]"
              >可预约查阅</span
            >
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button v-hasPermi="['farm:compliancecert:query']" link type="primary" @click="showDetail(row)">查看</el-button>
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

    <el-row :gutter="12">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="mb-12px">
          <template #header><span class="font-700">公示规则</span></template>
          <div v-for="x in RULES" :key="x.k" class="flex py-6px text-13px">
            <span class="w-120px flex-shrink-0 text-[var(--el-text-color-secondary)]">{{ x.k }}</span>
            <span>{{ x.v }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="mb-12px">
          <template #header><span class="font-700">核验记录</span></template>
          <el-timeline>
            <el-timeline-item
              v-for="(r, i) in records"
              :key="i"
              :timestamp="`${fmtTime(r.time)} · ${r.by}`"
              :type="r.done ? 'success' : 'warning'"
              :hollow="!r.done"
            >
              {{ r.text }}
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>

  <Dialog v-model="dialogVisible" title="上传证照" width="520px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" v-loading="formLoading">
      <el-form-item label="证照名称" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="证照编号" prop="no"><el-input v-model="form.no" /></el-form-item>
      <el-form-item label="类型" prop="type">
        <el-radio-group v-model="form.type">
          <el-radio v-for="t in CERT_TYPES" :key="t.value" :value="t.value">{{ t.label }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="有效期至">
        <el-date-picker
          v-model="form.validUntil"
          type="date"
          value-format="x"
          placeholder="不选表示长期有效"
          class="!w-full"
        />
      </el-form-item>
      <el-form-item label="C 端公示">
        <el-radio-group v-model="form.publicMode">
          <el-radio value="public">公示</el-radio>
          <el-radio value="private">不公示（可预约查阅）</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="证照文件">
        <UploadImg v-model="form.fileUrl" height="80px" width="120px" />
      </el-form-item>
      <div class="pl-90px text-12px text-[var(--el-text-color-secondary)]">
        提交后状态为「待核验」，由平台风控人工复核（演示环境不模拟复核结果）。
      </div>
    </el-form>
    <template #footer>
      <el-button v-hasPermi="['farm:compliancecert:create']" type="primary" :loading="formLoading" @click="submitForm">提 交</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>

  <Dialog v-model="detailVisible" :title="detail?.name" width="480px">
    <div v-if="detail">
      <div v-for="x in detailRows" :key="x.k" class="flex py-6px text-13px">
        <span class="w-100px flex-shrink-0 text-[var(--el-text-color-secondary)]">{{ x.k }}</span>
        <span>{{ x.v }}</span>
      </div>
      <el-image
        v-if="detail.fileUrl"
        :src="detail.fileUrl"
        fit="contain"
        class="mt-8px w-full"
        :preview-src-list="[detail.fileUrl]"
      />
      <div v-else class="mt-8px text-12px text-[var(--el-text-color-secondary)]">
        未上传证照文件。C 端公示时脱敏展示，并附可核验二维码（二维码未接入）。
      </div>
    </div>
  </Dialog>
</template>
<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus'
import * as ComplianceApi from '@/api/farm/compliance'
import { enumOf, fmtDay, fmtTime } from '../constants'
import StatCards from '../fulfill/StatCards.vue'
import { CERT_TYPES, RULES, VERIFY_STATUS } from './constants'

defineOptions({ name: 'FarmComplianceCert' })

const message = useMessage()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const stats = ref<any>({})
const records = ref<any[]>([])
const queryParams = reactive<any>({ pageNo: 1, pageSize: 10, name: undefined, type: undefined })

const statItems = computed(() => [
  { label: '证照总数', value: stats.value.total ?? 0, sub: '含基地备案证' },
  {
    label: '30 天内到期',
    value: stats.value.expiringSoon ?? 0,
    sub: stats.value.expiringSoon ? '请尽快上传新版' : '暂无',
    tone: stats.value.expiringSoon ? ('warn' as const) : ('muted' as const)
  },
  {
    label: '已过期',
    value: stats.value.expired ?? 0,
    sub: stats.value.expired ? '相关品类将自动下架' : '暂无',
    tone: stats.value.expired ? ('warn' as const) : ('muted' as const)
  },
  {
    label: '缺失的必备证照',
    value: stats.value.missingRequired ?? 0,
    sub: stats.value.missingRequired ? '缺失将限制商品上架' : '必备证照齐全',
    tone: stats.value.missingRequired ? ('warn' as const) : ('up' as const)
  }
])

const getList = async () => {
  loading.value = true
  try {
    const res = await ComplianceApi.getCertPage(queryParams)
    list.value = res.list
    total.value = res.total
    stats.value = res.stats ?? {}
    records.value = res.stats?.records ?? []
  } finally {
    loading.value = false
  }
}
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}
const resetQuery = () => {
  queryParams.name = undefined
  queryParams.type = undefined
  handleQuery()
}

const togglePublic = async (row: any, pub: boolean) => {
  await ComplianceApi.updateCertPublic(row.id, pub ? 'public' : 'private')
  message.success('公示设置已更新')
  getList()
}

const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const form = ref<any>({})
const rules: FormRules = {
  name: [{ required: true, message: '请填写证照名称', trigger: 'blur' }],
  no: [{ required: true, message: '请填写证照编号', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}
const openForm = () => {
  form.value = {
    name: '',
    no: '',
    type: '必备',
    validUntil: undefined,
    publicMode: 'public',
    fileUrl: ''
  }
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}
const submitForm = async () => {
  if (!(await formRef.value?.validate().catch(() => false))) return
  formLoading.value = true
  try {
    await ComplianceApi.createCert({
      ...form.value,
      validUntil: form.value.validUntil ? Number(form.value.validUntil) : null
    })
    message.success('已提交，等待平台核验')
    dialogVisible.value = false
    getList()
  } finally {
    formLoading.value = false
  }
}

const detailVisible = ref(false)
const detail = ref<any>(null)
const detailRows = computed(() => {
  const d = detail.value
  if (!d) return []
  return [
    { k: '证照编号', v: d.no },
    { k: '类型', v: d.type },
    { k: '有效期至', v: d.validUntil ? fmtDay(d.validUntil) : '长期' },
    { k: '核验状态', v: d.verifyName },
    { k: 'C 端公示', v: d.publicName },
    {
      k: '数据来源',
      v: d.source === 'base' ? '基地资料（备案编号）' : d.demo ? '演示数据' : '经营者上传'
    }
  ]
})
const showDetail = (row: any) => {
  detail.value = row
  detailVisible.value = true
}

onMounted(getList)
let mounted = false
onActivated(() => {
  if (mounted) getList()
  mounted = true
})
</script>
