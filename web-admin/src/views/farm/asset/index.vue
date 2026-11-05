<template>
  <ContentWrap>
    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="56px">
      <el-form-item label="基地">
        <el-select v-model="queryParams.baseId" placeholder="全部" clearable class="!w-180px">
          <el-option v-for="b in baseList" :key="b.id" :label="b.name" :value="b.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="queryParams.assetType" placeholder="全部" clearable class="!w-120px">
          <el-option v-for="t in ASSET_TYPES" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部" clearable class="!w-120px">
          <el-option v-for="s in ASSET_STATUS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="编号">
        <el-input
          v-model="queryParams.code"
          placeholder="如 A-01"
          clearable
          class="!w-140px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button type="primary" plain @click="openForm()">
          <Icon icon="ep:plus" class="mr-5px" />新建资产
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="编号" prop="code" width="100" />
      <el-table-column label="名称" prop="name" min-width="150" />
      <el-table-column label="类型" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="enumOf(ASSET_TYPES, row.assetType).type" effect="plain">
            {{ enumLabel(ASSET_TYPES, row.assetType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="基地" prop="baseName" min-width="120" />
      <el-table-column label="规格" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ specText(row) }}</template>
      </el-table-column>
      <el-table-column label="设备" min-width="170">
        <template #default="{ row }">
          <div v-if="row.cameraName" class="text-12px">
            <Icon icon="ep:video-camera" class="mr-4px" />{{ row.cameraName }}
          </div>
          <div v-if="row.sensorName" class="text-12px">
            <Icon icon="ep:odometer" class="mr-4px" />{{ row.sensorName }}
          </div>
          <span
            v-if="!row.cameraName && !row.sensorName"
            class="text-12px text-[var(--el-text-color-placeholder)]"
          >
            未挂设备
          </span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="enumOf(ASSET_STATUS, row.status).type">
            {{ enumLabel(ASSET_STATUS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="认养人" min-width="140">
        <template #default="{ row }">
          <template v-if="row.memberName">
            {{ row.memberName }}
            <el-tag size="small" :type="enumOf(CARE_MODES, row.careMode).type" effect="plain">
              {{ enumLabel(CARE_MODES, row.careMode) }}
            </el-tag>
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)">编辑</el-button>
          <el-button link type="primary" @click="openEnv(row)">环境数据</el-button>
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

  <!-- 新建 / 编辑 -->
  <Dialog v-model="dialogVisible" :title="form.id ? '编辑资产' : '新建资产'" width="620px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" v-loading="formLoading">
      <el-form-item label="基地" prop="baseId">
        <el-select
          v-model="form.baseId"
          placeholder="请选择基地"
          :disabled="!!form.id"
          class="!w-full"
        >
          <el-option v-for="b in baseList" :key="b.id" :label="b.name" :value="b.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="资产类型" prop="assetType">
        <el-radio-group v-model="form.assetType" :disabled="!!form.id">
          <el-radio-button v-for="t in ASSET_TYPES" :key="t.value" :value="t.value">
            {{ t.label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="编号" prop="code">
        <el-input
          v-model="form.code"
          :placeholder="form.assetType === 'animal' ? '耳标号 / 脚环号' : '如 A-01'"
        />
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="form.name" placeholder="不填则与编号相同" />
      </el-form-item>
      <template v-if="form.assetType === 'plot'">
        <el-form-item label="面积（㎡）">
          <el-input-number v-model="form.specs.areaM2" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="种植作物">
          <el-input v-model="form.specs.crop" placeholder="如：时令蔬菜（番茄、黄瓜轮作）" />
        </el-form-item>
      </template>
      <template v-else>
        <el-form-item label="品种">
          <el-input
            v-model="form.specs.breed"
            :placeholder="form.assetType === 'animal' ? '如：跑山鸡' : '如：安吉白茶'"
          />
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="form.specs.count" :min="1" controls-position="right" />
          <span class="ml-8px">{{ form.assetType === 'animal' ? '只 / 头' : '株' }}</span>
        </el-form-item>
      </template>
      <el-form-item label="摄像头">
        <el-input v-model="form.cameraName" placeholder="如：A 区 1 号枪机" />
      </el-form-item>
      <el-form-item label="传感器">
        <el-input v-model="form.sensorName" placeholder="如：A 区墒情站 1" />
      </el-form-item>
      <el-form-item v-if="form.id" label="状态">
        <el-radio-group v-model="form.status">
          <el-radio :value="0" :disabled="form.status === 10">空闲</el-radio>
          <el-radio :value="10" disabled>已认养</el-radio>
          <el-radio :value="20" :disabled="form.status === 10">停用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>

  <!-- 环境数据 -->
  <el-drawer v-model="envVisible" :title="`环境数据 · ${envAsset?.code ?? ''}`" size="560px">
    <div v-loading="envLoading">
      <template v-if="env">
        <div
          class="mb-8px flex items-center justify-between text-12px text-[var(--el-text-color-secondary)]"
        >
          <span>
            {{ env.sensorName || '未挂传感器' }} · 更新于
            {{ fmtTime(env.current.updateTime, 'MM-DD HH:mm') }}
          </span>
          <el-tag v-if="env.demo" type="warning" size="small" effect="plain">演示数据</el-tag>
        </div>
        <el-row :gutter="8">
          <el-col v-for="m in envMetrics" :key="m.label" :span="8">
            <div class="env-card">
              <div class="text-12px text-[var(--el-text-color-secondary)]">{{ m.label }}</div>
              <div class="text-20px font-700">
                {{ m.value ?? '-' }}<span class="text-12px ml-2px">{{ m.unit }}</span>
              </div>
            </div>
          </el-col>
        </el-row>
        <div class="mt-16px font-600">近 24 小时</div>
        <Echart :height="300" :options="envOption" />
      </template>
    </div>
  </el-drawer>
</template>
<script lang="ts" setup>
import type { EChartsOption } from 'echarts'
import type { FormInstance, FormRules } from 'element-plus'
import * as AssetApi from '@/api/farm/asset'
import * as BaseApi from '@/api/farm/base'
import { ASSET_STATUS, ASSET_TYPES, CARE_MODES, enumLabel, enumOf, fmtTime } from '../constants'

defineOptions({ name: 'FarmAsset' })

const message = useMessage()
const baseList = ref<{ id: number; name: string }[]>([])
const loading = ref(false)
const list = ref<AssetApi.AssetVO[]>([])
const total = ref(0)
const queryParams = reactive<any>({
  pageNo: 1,
  pageSize: 10,
  baseId: undefined,
  assetType: undefined,
  status: undefined,
  code: undefined
})

const specText = (row: AssetApi.AssetVO) => {
  const s = row.specs || {}
  if (row.assetType === 'plot') {
    return [s.areaM2 ? `${s.areaM2}㎡` : '', s.crop].filter(Boolean).join(' · ') || '-'
  }
  const unit = row.assetType === 'animal' ? '只' : '株'
  return [s.breed, s.count ? `${s.count} ${unit}` : ''].filter(Boolean).join(' · ') || '-'
}

const getList = async () => {
  loading.value = true
  try {
    const res = await AssetApi.getAssetPage(queryParams)
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
  Object.assign(queryParams, {
    baseId: undefined,
    assetType: undefined,
    status: undefined,
    code: undefined
  })
  handleQuery()
}

// 表单
const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const emptyForm = (): AssetApi.AssetVO => ({
  baseId: baseList.value[0]?.id,
  assetType: 'plot',
  code: '',
  name: '',
  specs: { areaM2: 66, crop: '' },
  cameraName: '',
  sensorName: ''
})
const form = ref<AssetApi.AssetVO>(emptyForm())
const rules: FormRules = {
  baseId: [{ required: true, message: '请选择基地', trigger: 'change' }],
  assetType: [{ required: true, message: '请选择资产类型', trigger: 'change' }],
  code: [{ required: true, message: '编号不能为空', trigger: 'blur' }]
}
const openForm = (row?: AssetApi.AssetVO) => {
  form.value = row
    ? {
        id: row.id,
        baseId: row.baseId,
        assetType: row.assetType,
        code: row.code,
        name: row.name,
        specs: { ...(row.specs || {}) },
        cameraName: row.cameraName ?? '',
        sensorName: row.sensorName ?? '',
        status: row.status
      }
    : emptyForm()
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}
const submitForm = async () => {
  if (!(await formRef.value?.validate().catch(() => false))) return
  const f = form.value
  // 只提交当前类型用得到的规格字段
  const specs =
    f.assetType === 'plot'
      ? { areaM2: f.specs.areaM2, crop: f.specs.crop }
      : { breed: f.specs.breed, count: f.specs.count }
  formLoading.value = true
  try {
    if (f.id) {
      await AssetApi.updateAsset({ ...f, specs })
      message.success('已保存')
    } else {
      await AssetApi.createAsset({ ...f, specs })
      message.success('资产已创建')
    }
    dialogVisible.value = false
    getList()
  } finally {
    formLoading.value = false
  }
}

// 环境数据
const envVisible = ref(false)
const envLoading = ref(false)
const envAsset = ref<AssetApi.AssetVO>()
const env = ref<any>()
const openEnv = async (row: AssetApi.AssetVO) => {
  envAsset.value = row
  env.value = undefined
  envVisible.value = true
  envLoading.value = true
  try {
    env.value = await AssetApi.getAssetEnv(row.id!)
  } finally {
    envLoading.value = false
  }
}
const envMetrics = computed(() => {
  const c = env.value?.current || {}
  return [
    { label: '气温', value: c.airTemp, unit: '℃' },
    { label: '空气湿度', value: c.airHumidity, unit: '%' },
    { label: '土壤湿度', value: c.soilMoisture, unit: '%' },
    { label: '土壤温度', value: c.soilTemp, unit: '℃' },
    { label: '土壤 pH', value: c.ph, unit: '' },
    { label: '光照', value: c.light, unit: 'lux' }
  ]
})
const envOption = computed<EChartsOption>(() => {
  const s = env.value?.series || []
  return {
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 40, right: 20, top: 36, bottom: 30 },
    xAxis: { type: 'category', data: s.map((x: any) => fmtTime(x.ts, 'HH:mm')) },
    yAxis: { type: 'value' },
    series: [
      {
        name: '气温 ℃',
        type: 'line',
        smooth: true,
        data: s.map((x: any) => x.airTemp),
        itemStyle: { color: '#ef6c00' }
      },
      {
        name: '空气湿度 %',
        type: 'line',
        smooth: true,
        data: s.map((x: any) => x.airHumidity),
        itemStyle: { color: '#1976d2' }
      },
      {
        name: '土壤湿度 %',
        type: 'line',
        smooth: true,
        data: s.map((x: any) => x.soilMoisture),
        itemStyle: { color: '#2e7d32' }
      }
    ]
  }
})

onMounted(async () => {
  baseList.value = await BaseApi.getBaseList()
  getList()
})
</script>
<style scoped>
.env-card {
  padding: 10px;
  margin-bottom: 8px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}
</style>
