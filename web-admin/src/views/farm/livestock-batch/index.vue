<template>
  <ContentWrap>
    <div class="flex items-start justify-between mb-8px">
      <div>
        <div class="text-18px font-600">畜禽批次</div>
        <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
          按批次管理畜禽的入栏、在栏和出栏数量；出栏数累加，满额后批次自动置为已出栏
        </div>
      </div>
      <el-button v-hasPermi="['farm:livestockbatch:create']" type="primary" @click="openForm()">
        <Icon icon="ep:plus" class="mr-5px" />新建批次
      </el-button>
    </div>
    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="品种">
        <el-input
          v-model="queryParams.species"
          placeholder="如：土鸡"
          clearable
          class="!w-160px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部" clearable class="!w-130px">
          <el-option v-for="s in LIVESTOCK_STATUS" :key="s.value" :label="s.label" :value="s.value" />
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
      <el-table-column label="批次号" prop="no" width="140" />
      <el-table-column label="基地" prop="baseName" min-width="120" show-overflow-tooltip />
      <el-table-column label="畜禽" min-width="130">
        <template #default="{ row }">
          <div class="font-600">{{ row.species }}</div>
          <div class="text-12px text-[var(--el-text-color-secondary)]">{{ row.breed || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="入栏日期" width="110">
        <template #default="{ row }">{{ fmtDay(row.inDate) }}</template>
      </el-table-column>
      <el-table-column label="栏舍" prop="pen" width="90" align="center" />
      <el-table-column label="数量（出栏 / 入栏）" width="170">
        <template #default="{ row }">
          <el-progress
            :percentage="row.count ? Math.round(((row.outCount || 0) / row.count) * 100) : 0"
            :stroke-width="6"
            :show-text="false"
          />
          <div class="text-12px text-[var(--el-text-color-secondary)]">
            出栏 {{ row.outCount || 0 }} / 入栏 {{ row.count }}，在栏 {{ row.count - (row.outCount || 0) }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="enumOf(LIVESTOCK_STATUS, row.status).type">
            {{ enumLabel(LIVESTOCK_STATUS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="200" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            v-hasPermi="['farm:livestockbatch:update']"
            link
            type="primary"
            @click="openForm(row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="row.status !== 20"
            v-hasPermi="['farm:livestockbatch:out']"
            link
            type="warning"
            @click="openOut(row)"
          >
            出栏
          </el-button>
          <el-button
            v-if="!row.outCount"
            v-hasPermi="['farm:livestockbatch:delete']"
            link
            type="danger"
            @click="handleDelete(row)"
          >
            删除
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

  <Dialog v-model="dialogVisible" :title="form.id ? '编辑畜禽批次' : '新建畜禽批次'" width="560px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" v-loading="formLoading">
      <el-form-item label="基地" prop="baseId">
        <el-select v-model="form.baseId" placeholder="选择基地" class="!w-full">
          <el-option v-for="b in bases" :key="b.id" :label="b.name" :value="b.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="畜禽种类" prop="species">
        <el-select
          v-model="form.species"
          filterable
          allow-create
          default-first-option
          placeholder="选择或输入，如：土鸡"
          class="!w-full"
        >
          <el-option v-for="s in SPECIES" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item label="品种">
        <el-input v-model="form.breed" placeholder="如：清远麻鸡" />
      </el-form-item>
      <el-form-item label="入栏日期" prop="inDate">
        <el-date-picker v-model="form.inDate" type="date" value-format="x" placeholder="选择日期" />
      </el-form-item>
      <el-form-item label="入栏数量" prop="count">
        <el-input-number
          v-model="form.count"
          :min="Math.max(1, form.outCount || 0)"
          :precision="0"
          controls-position="right"
        />
        <span class="ml-8px text-12px text-[var(--el-text-color-secondary)]">只</span>
      </el-form-item>
      <el-form-item label="栏舍">
        <el-input v-model="form.pen" placeholder="如：A 栋 3 栏" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>

  <Dialog v-model="outVisible" :title="`出栏 · ${outRow?.no ?? ''}`" width="420px">
    <el-form label-width="90px">
      <el-form-item label="当前在栏">{{ outLeft }} 只</el-form-item>
      <el-form-item label="本次出栏">
        <el-input-number
          v-model="outCount"
          :min="1"
          :max="outLeft"
          :precision="0"
          controls-position="right"
        />
        <span class="ml-8px text-12px text-[var(--el-text-color-secondary)]">只，不能超过在栏数</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="outLoading" @click="submitOut">确认出栏</el-button>
      <el-button @click="outVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus'
import * as LivestockApi from '@/api/farm/livestockBatch'
import * as BaseApi from '@/api/farm/base'
import { enumLabel, enumOf, fmtDay } from '../constants'
import { LIVESTOCK_STATUS } from './constants'

defineOptions({ name: 'FarmLivestockBatch' })

const SPECIES = ['鸡', '鸭', '鹅', '猪', '牛', '羊', '兔']

const message = useMessage()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const bases = ref<{ id: number; name: string }[]>([])
const queryParams = reactive<any>({ pageNo: 1, pageSize: 10, species: undefined, status: undefined })

const getList = async () => {
  loading.value = true
  try {
    const res = await LivestockApi.getLivestockBatchPage(queryParams)
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
  queryParams.species = undefined
  queryParams.status = undefined
  handleQuery()
}

// ---------- 新建 / 编辑 ----------
const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const emptyForm = (): any => ({
  baseId: undefined,
  species: '',
  breed: '',
  inDate: Date.now(),
  count: undefined,
  outCount: 0,
  pen: '',
  remark: ''
})
const form = ref<any>(emptyForm())
const rules: FormRules = {
  baseId: [{ required: true, message: '请选择基地', trigger: 'change' }],
  species: [{ required: true, message: '请选择或输入畜禽种类', trigger: 'change' }],
  inDate: [{ required: true, message: '请选择入栏日期', trigger: 'change' }],
  count: [{ required: true, message: '请填写入栏数量', trigger: 'change' }]
}
const openForm = (row?: any) => {
  form.value = row
    ? {
        id: row.id,
        baseId: row.baseId,
        species: row.species,
        breed: row.breed,
        inDate: row.inDate,
        count: row.count,
        outCount: row.outCount || 0,
        pen: row.pen,
        remark: row.remark
      }
    : emptyForm()
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}
const submitForm = async () => {
  if (!(await formRef.value?.validate().catch(() => false))) return
  formLoading.value = true
  try {
    const { outCount: _o, ...data } = form.value
    data.inDate = Number(data.inDate)
    if (data.id) {
      await LivestockApi.updateLivestockBatch(data)
      message.success('已保存')
    } else {
      await LivestockApi.createLivestockBatch(data)
      message.success('批次已创建')
    }
    dialogVisible.value = false
    getList()
  } finally {
    formLoading.value = false
  }
}

// ---------- 出栏 ----------
const outVisible = ref(false)
const outLoading = ref(false)
const outRow = ref<any>()
const outCount = ref(1)
const outLeft = computed(() => (outRow.value ? outRow.value.count - (outRow.value.outCount || 0) : 0))
const openOut = (row: any) => {
  outRow.value = row
  outCount.value = Math.max(1, row.count - (row.outCount || 0))
  outVisible.value = true
}
const submitOut = async () => {
  outLoading.value = true
  try {
    await LivestockApi.outLivestockBatch({ id: outRow.value.id, count: outCount.value })
    message.success(`已出栏 ${outCount.value} 只`)
    outVisible.value = false
    getList()
  } finally {
    outLoading.value = false
  }
}

const handleDelete = async (row: any) => {
  try {
    await message.delConfirm(`确定删除批次 ${row.no} 吗？（只有还没出栏过的批次能删）`)
  } catch {
    return
  }
  await LivestockApi.deleteLivestockBatch(row.id)
  message.success('已删除')
  getList()
}

onMounted(async () => {
  getList()
  bases.value = await BaseApi.getBaseList()
})
</script>
