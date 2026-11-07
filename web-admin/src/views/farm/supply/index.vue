<template>
  <div class="flex items-start justify-between mb-12px">
    <div>
      <div class="text-18px font-600">农资与服务商品</div>
      <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
        面向半托管 / 不托管认养人的第二增长曲线：把「认养费」降下来的同时，用农资与代管服务把客单价补回来
      </div>
    </div>
    <div class="flex gap-8px">
      <el-button @click="notOpen">导入批次</el-button>
      <el-button type="primary" @click="openForm()">
        <Icon icon="ep:plus" class="mr-5px" />发布农资
      </el-button>
    </div>
  </div>

  <StatCards :items="statItems" />

  <ContentWrap>
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane v-for="t in SUPPLY_TABS" :key="t.name" :name="t.name">
        <template #label>{{ t.label }}（{{ catCounts[t.name] ?? 0 }}）</template>
      </el-tab-pane>
    </el-tabs>
    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="商品名称">
        <el-input
          v-model="queryParams.name"
          placeholder="农资名称"
          clearable
          class="!w-200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="批次号">
        <el-input
          v-model="queryParams.batchNo"
          placeholder="如 SEED-2026"
          clearable
          class="!w-180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部" clearable class="!w-120px">
          <el-option v-for="s in SUPPLY_STATUS" :key="s.value" :label="s.label" :value="s.value" />
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
      <el-table-column label="编号" prop="code" width="90" />
      <el-table-column label="商品名称" min-width="220">
        <template #default="{ row }">
          <div class="flex items-center">
            <el-image
              v-if="row.cover"
              :src="row.cover"
              fit="cover"
              class="w-40px h-40px rounded-4px mr-10px flex-shrink-0"
            />
            <div>
              <div class="font-600">{{ row.name }}</div>
              <div class="text-12px text-[var(--el-text-color-secondary)]">
                {{ row.spec || '-' }}
                <el-tag v-if="!row.retailAllowed" type="danger" size="small" class="ml-4px">
                  只能农场代施
                </el-tag>
              </div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="分类" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="enumOf(SUPPLY_CAT_PROTO, row.cat).type" effect="plain">
            {{ enumLabel(SUPPLY_CAT_PROTO, row.cat) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="售价" width="100" align="right">
        <template #default="{ row }">
          <b>{{ formatMoney(row.price) }}</b>
        </template>
      </el-table-column>
      <el-table-column
        label="成本"
        prop="cost"
        width="90"
        align="right"
        :formatter="moneyFormatter"
      />
      <el-table-column label="毛利率" width="80" align="right">
        <template #default="{ row }">{{ row.grossMargin }}%</template>
      </el-table-column>
      <el-table-column label="库存" prop="stock" width="80" align="right" />
      <el-table-column label="销量" prop="sold" width="80" align="right" />
      <el-table-column label="适用模式" width="130">
        <template #default="{ row }">
          {{ (row.fitModes || []).map((m: string) => enumLabel(CARE_MODES, m)).join(' / ') || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="批次" prop="batchNo" width="150" show-overflow-tooltip />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="SUPPLY_STATE_TYPE[row.stateName] ?? 'info'">{{ row.stateName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row.id)">编辑</el-button>
          <el-button link type="primary" @click="notOpen">批次</el-button>
          <el-button
            link
            :type="row.status === 0 ? 'danger' : 'success'"
            @click="toggleStatus(row)"
          >
            {{ row.status === 0 ? '下架' : '上架' }}
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
    <el-alert
      class="mt-12px"
      type="warning"
      :closable="false"
      show-icon
      title="合规校验已开启：检测到「化学农药」类目商品时，系统将强制关闭 C 端零售开关，仅允许以「农场代施服务」形式绑定工单出售；所有农资售出即写入购入地块的投入品台账。"
    />
  </ContentWrap>

  <Dialog v-model="dialogVisible" :title="form.id ? '编辑农资' : '新增农资'" width="640px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" v-loading="formLoading">
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="如：有机蔬菜种子组合" />
      </el-form-item>
      <el-form-item label="类目" prop="cat">
        <el-select
          v-model="form.cat"
          placeholder="请选择类目"
          class="!w-200px"
          @change="onCatChange"
        >
          <el-option
            v-for="c in SUPPLY_CAT_PROTO"
            :key="c.value"
            :label="c.label"
            :value="c.value"
          />
        </el-select>
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="售价（元）" prop="price">
            <el-input-number
              :model-value="form.price / 100"
              :min="0"
              :precision="2"
              controls-position="right"
              class="!w-160px"
              @update:model-value="
                (v: number | undefined) => (form.price = Math.round((v || 0) * 100))
              "
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="成本（元）">
            <el-input-number
              :model-value="form.cost / 100"
              :min="0"
              :precision="2"
              controls-position="right"
              class="!w-160px"
              @update:model-value="
                (v: number | undefined) => (form.cost = Math.round((v || 0) * 100))
              "
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="库存">
            <el-input-number
              v-model="form.stock"
              :min="0"
              controls-position="right"
              class="!w-160px"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="单位">
            <el-input v-model="form.unit" placeholder="件 / 袋 / 套" class="!w-160px" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="规格">
        <el-input v-model="form.spec" placeholder="如：8 种时令蔬菜" />
      </el-form-item>
      <el-form-item label="批次号">
        <el-input v-model="form.batchNo" placeholder="写进地块的投入品档案" />
      </el-form-item>
      <el-form-item label="适用托管">
        <el-checkbox-group v-model="form.fitModes">
          <el-checkbox v-for="m in CARE_MODES" :key="m.value" :value="m.value">{{
            m.label
          }}</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="可零售">
        <el-switch v-model="form.retailAllowed" :disabled="isPesticide" />
        <span v-if="isPesticide" class="ml-8px text-12px text-[var(--el-color-danger)]">
          化学农药不对个人零售，只能农场代施
        </span>
      </el-form-item>
      <el-form-item label="需要工单">
        <el-switch v-model="form.needWorkorder" :disabled="isPesticide" />
        <span class="ml-8px text-12px text-[var(--el-text-color-secondary)]">
          打开后，认养人购买会生成管家代施工单
        </span>
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio v-for="s in SUPPLY_STATUS" :key="s.value" :value="s.value">{{
            s.label
          }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="2" />
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
import * as SupplyApi from '@/api/farm/supply'
import {
  CARE_MODES,
  SUPPLY_STATUS,
  enumLabel,
  enumOf,
  formatMoney,
  moneyFormatter
} from '../constants'
import StatCards from '../fulfill/StatCards.vue'
import { SUPPLY_CAT_PROTO, SUPPLY_STATE_TYPE, SUPPLY_TABS } from './constants'

defineOptions({ name: 'FarmSupply' })

const message = useMessage()
const loading = ref(false)
const list = ref<SupplyApi.SupplyVO[]>([])
const total = ref(0)
const queryParams = reactive<any>({
  pageNo: 1,
  pageSize: 10,
  name: undefined,
  cat: undefined,
  batchNo: undefined,
  status: undefined
})
const stats = ref<any>({})
const activeTab = ref('all')
const catCounts = computed<Record<string, number>>(() => stats.value.catCounts ?? {})
const statItems = computed(() => {
  const s = stats.value
  return [
    { label: '本月农资 GMV', value: formatMoney(s.monthGmv ?? 0), sub: '本月已下单金额合计' },
    {
      label: '关联订单',
      value: `${s.monthOrderCount ?? 0} 单`,
      sub: `客单 ${formatMoney(s.avgOrderAmount ?? 0)}`
    },
    {
      label: '认养人复购率',
      value: `${s.repeatRate ?? 0}%`,
      sub: `买过 2 次以上 ${s.repeatMemberCount ?? 0} 人`
    },
    {
      label: '代管服务收入',
      value: formatMoney(s.serviceIncome ?? 0),
      sub: '由逾期自管任务转化'
    }
  ]
})
const onTabChange = (name: string | number) => {
  queryParams.cat = name === 'all' ? undefined : name
  handleQuery()
}
const notOpen = () => message.info('演示版暂未开放')
/** 上下架：取完整数据再改状态保存（update 接口按字段更新，这样不会漏字段） */
const toggleStatus = async (row: SupplyApi.SupplyVO) => {
  const to = row.status === 0 ? 1 : 0
  try {
    await message.confirm(`确认${to === 1 ? '下架' : '上架'}「${row.name}」？`)
  } catch {
    return
  }
  const full = await SupplyApi.getSupply(row.id!)
  await SupplyApi.updateSupply({ ...full, status: to })
  message.success(to === 1 ? '已下架' : '已上架')
  getList()
}

const getList = async () => {
  loading.value = true
  try {
    const res = await SupplyApi.getSupplyPage(queryParams)
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
  Object.assign(queryParams, {
    name: undefined,
    cat: undefined,
    batchNo: undefined,
    status: undefined
  })
  activeTab.value = 'all'
  handleQuery()
}

const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const emptyForm = (): SupplyVO => ({
  name: '',
  cat: 'seed',
  cover: '',
  price: 0,
  cost: 0,
  stock: 100,
  unit: '件',
  spec: '',
  fitModes: ['semi', 'self'],
  batchNo: '',
  retailAllowed: true,
  needWorkorder: false,
  status: 0,
  description: ''
})
type SupplyVO = SupplyApi.SupplyVO
const form = ref<SupplyVO>(emptyForm())
const isPesticide = computed(() => form.value.cat === 'pesticide')
const rules: FormRules = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  cat: [{ required: true, message: '请选择类目', trigger: 'change' }],
  price: [
    {
      validator: (_r, _v, cb) => (form.value.price > 0 ? cb() : cb(new Error('价格要大于 0'))),
      trigger: 'change'
    }
  ]
}
/** 化学农药：服务端也会强制，这里先把开关锁住 */
const onCatChange = () => {
  if (isPesticide.value) {
    form.value.retailAllowed = false
    form.value.needWorkorder = true
  }
}
const openForm = async (id?: number) => {
  form.value = emptyForm()
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
  if (id) {
    formLoading.value = true
    try {
      form.value = await SupplyApi.getSupply(id)
    } finally {
      formLoading.value = false
    }
  }
}
const submitForm = async () => {
  if (!(await formRef.value?.validate().catch(() => false))) return
  onCatChange()
  formLoading.value = true
  try {
    if (form.value.id) {
      await SupplyApi.updateSupply(form.value)
      message.success('已保存')
    } else {
      await SupplyApi.createSupply(form.value)
      message.success('农资已新增')
    }
    dialogVisible.value = false
    getList()
  } finally {
    formLoading.value = false
  }
}

onMounted(getList)
</script>
