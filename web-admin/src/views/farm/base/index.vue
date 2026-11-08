<template>
  <ContentWrap>
    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="基地名称">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入基地名称"
          clearable
          class="!w-220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部" clearable class="!w-160px">
          <el-option v-for="s in BASE_STATUS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button v-hasPermi="['farm:base:create']" type="primary" plain @click="openForm()"
          ><Icon icon="ep:plus" class="mr-5px" />新建基地</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="基地名称" prop="name" min-width="140" />
      <el-table-column label="地址" prop="address" min-width="220" show-overflow-tooltip />
      <el-table-column label="面积" width="90" align="center">
        <template #default="{ row }">{{ row.areaMu }} 亩</template>
      </el-table-column>
      <el-table-column label="资质编号" prop="certNo" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.certNo">{{ row.certNo }}</span>
          <el-tag v-else type="danger" size="small">未上传</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="主要品类" prop="cropSummary" min-width="140" />
      <el-table-column label="资产数" prop="assetCount" width="80" align="center" />
      <el-table-column label="管家数" prop="stewardCount" width="80" align="center" />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="enumOf(BASE_STATUS, row.status).type">{{
            enumLabel(BASE_STATUS, row.status)
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="160" :formatter="timeFormatter" />
      <el-table-column label="操作" width="130" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPermi="['farm:base:update']" link type="primary" @click="openForm(row)">编辑</el-button>
          <el-button v-hasPermi="['farm:base:delete']" link type="danger" @click="handleDelete(row)">删除</el-button>
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

  <Dialog v-model="dialogVisible" :title="form.id ? '编辑基地' : '新建基地'" width="600px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" v-loading="formLoading">
      <el-form-item label="基地名称" prop="name">
        <el-input v-model="form.name" placeholder="如：青山二号基地" />
      </el-form-item>
      <el-form-item label="地址" prop="address">
        <el-input v-model="form.address" placeholder="省市区 + 村 / 详细地址" />
      </el-form-item>
      <el-form-item label="经纬度">
        <el-input-number
          v-model="form.lng"
          :precision="4"
          :step="0.001"
          controls-position="right"
          class="!w-160px"
          placeholder="经度"
        />
        <span class="mx-8px">,</span>
        <el-input-number
          v-model="form.lat"
          :precision="4"
          :step="0.001"
          controls-position="right"
          class="!w-160px"
          placeholder="纬度"
        />
      </el-form-item>
      <el-form-item label="面积（亩）">
        <el-input-number v-model="form.areaMu" :min="0" controls-position="right" />
      </el-form-item>
      <el-form-item label="资质编号">
        <el-input v-model="form.certNo" placeholder="经营 / 防疫资质编号，风险自检会检查" />
      </el-form-item>
      <el-form-item label="主要品类">
        <el-input v-model="form.cropSummary" placeholder="如：时令蔬菜、跑山鸡" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio v-for="s in BASE_STATUS" :key="s.value" :value="s.value">{{
            s.label
          }}</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus'
import * as BaseApi from '@/api/farm/base'
import { BASE_STATUS, enumLabel, enumOf, timeFormatter } from '../constants'

defineOptions({ name: 'FarmBase' })

const message = useMessage()
const loading = ref(false)
const list = ref<BaseApi.BaseVO[]>([])
const total = ref(0)
const queryParams = reactive<any>({ pageNo: 1, pageSize: 10, name: undefined, status: undefined })

const getList = async () => {
  loading.value = true
  try {
    const res = await BaseApi.getBasePage(queryParams)
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
  queryParams.name = undefined
  queryParams.status = undefined
  handleQuery()
}

const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const emptyForm = (): BaseApi.BaseVO => ({
  name: '',
  address: '',
  lng: undefined,
  lat: undefined,
  areaMu: undefined,
  certNo: '',
  cropSummary: '',
  status: 0
})
const form = ref<BaseApi.BaseVO>(emptyForm())
const rules: FormRules = {
  name: [{ required: true, message: '基地名称不能为空', trigger: 'blur' }],
  address: [{ required: true, message: '基地地址不能为空', trigger: 'blur' }]
}
const openForm = (row?: BaseApi.BaseVO) => {
  form.value = row
    ? {
        id: row.id,
        name: row.name,
        address: row.address,
        lng: row.lng,
        lat: row.lat,
        areaMu: row.areaMu,
        certNo: row.certNo,
        cropSummary: row.cropSummary,
        status: row.status
      }
    : emptyForm()
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}
const submitForm = async () => {
  if (!(await formRef.value?.validate().catch(() => false))) return
  formLoading.value = true
  try {
    if (form.value.id) {
      await BaseApi.updateBase(form.value)
      message.success('已保存')
    } else {
      await BaseApi.createBase(form.value)
      message.success('基地已创建')
    }
    dialogVisible.value = false
    getList()
  } finally {
    formLoading.value = false
  }
}
const handleDelete = async (row: BaseApi.BaseVO) => {
  try {
    await message.delConfirm(`确定删除基地「${row.name}」吗？`)
  } catch {
    return
  }
  await BaseApi.deleteBase(row.id!)
  message.success('已删除')
  getList()
}

onMounted(getList)
</script>
