<template>
  <div class="flex items-start justify-between mb-12px">
    <div>
      <div class="text-18px font-600">配送批次</div>
      <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
        按认养商品配置自动生成配送计划；录入收成后汇总到批次并推送物流通知
      </div>
    </div>
    <div class="flex gap-8px">
      <el-button v-hasPermi="['farm:shipment:print']" @click="notOpen">打印面单</el-button>
      <el-button v-hasPermi="['farm:shipment:create']" type="primary" @click="openForm">
        <Icon icon="ep:plus" class="mr-5px" />新建批次
      </el-button>
    </div>
  </div>

  <ContentWrap>
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane v-for="t in SHIP_TABS" :key="String(t.name)" :name="t.name">
        <template #label>{{ t.label }}（{{ counts[t.countKey] ?? 0 }}）</template>
      </el-tab-pane>
    </el-tabs>
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
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table ref="tableRef" v-loading="loading" :data="list" row-key="id">
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
      <el-table-column label="批次号" prop="batchNo" width="120">
        <template #default="{ row }">
          <b>{{ row.batchNo }}</b>
        </template>
      </el-table-column>
      <el-table-column label="计划日期" width="110">
        <template #default="{ row }">{{ fmtDay(row.planDate) }}</template>
      </el-table-column>
      <el-table-column label="配送单号" prop="no" width="160" />
      <el-table-column label="地块" prop="assetCode" width="80" />
      <el-table-column label="收货人" min-width="210">
        <template #default="{ row }">
          <div>{{ row.receiverName }} {{ row.receiverMobile }}</div>
          <div class="text-12px text-[var(--el-text-color-secondary)]">{{ row.address }}</div>
        </template>
      </el-table-column>
      <el-table-column label="重量" width="80" align="right">
        <template #default="{ row }">{{ row.weightJin }} 斤</template>
      </el-table-column>
      <el-table-column label="物流方式" min-width="160">
        <template #default="{ row }">
          <div>
            {{ row.carrier }}
            <el-tag v-if="row.coldChain" size="small" effect="plain">冷链</el-tag>
          </div>
          <div class="text-12px text-[var(--el-text-color-secondary)]">{{ row.logisticsNo }}</div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="enumOf(SHIP_STATUS, row.status).type">
            {{ enumLabel(SHIP_STATUS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="完成度" width="120">
        <template #default="{ row }">
          <el-progress :percentage="row.progress" :stroke-width="6" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="toggleDetail(row)">明细</el-button>
          <el-button v-hasPermi="['farm:shipment:export']" link type="primary" @click="notOpen">导出</el-button>
          <el-button v-if="row.status === 10" v-hasPermi="['farm:shipment:sign']" link type="primary" @click="handleSign(row)">
            确认签收
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

  <el-row :gutter="12">
    <el-col :xs="24" :lg="12">
      <ContentWrap>
        <div class="font-600 mb-8px">待发货与在途</div>
        <div v-for="x in summaryRows" :key="x.k" class="flex mb-8px text-13px">
          <span class="w-100px text-[var(--el-text-color-secondary)] flex-shrink-0">{{ x.k }}</span>
          <span>{{ x.v }}</span>
        </div>
        <div class="flex gap-8px mt-12px">
          <el-button v-hasPermi="['farm:shipment:notify']" type="primary" size="small" @click="notOpen">一键通知认养人</el-button>
          <el-button v-hasPermi="['farm:shipment:print']" size="small" @click="notOpen">批量打印</el-button>
        </div>
      </ContentWrap>
    </el-col>
    <el-col :xs="24" :lg="12">
      <ContentWrap>
        <div class="font-600 mb-8px">自动配送规则</div>
        <div v-for="x in AUTO_RULES" :key="x.k" class="flex mb-8px text-13px">
          <span class="w-100px text-[var(--el-text-color-secondary)] flex-shrink-0">{{ x.k }}</span>
          <span>{{ x.v }}</span>
        </div>
        <el-alert
          type="success"
          :closable="false"
          show-icon
          title="收成录入 → 批次 → 物流单号 → C 端物流详情，全链路自动串接，无需人工转录。除批次归并外，其余规则为演示说明。"
        />
      </ContentWrap>
    </el-col>
  </el-row>

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
import { enumLabel, enumOf, fmtDay, fmtTime } from '../constants'
import { AUTO_RULES, SHIP_STATUS, SHIP_TABS } from './constants'

defineOptions({ name: 'FarmShipment' })

const message = useMessage()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const queryParams = reactive<any>({ pageNo: 1, pageSize: 10, no: undefined, status: undefined })
const stats = ref<any>({})
const activeTab = ref<string | number>('all')
const counts = computed<Record<string, number>>(() => stats.value.counts ?? {})
/** 待发货收成（还没建配送单的收成）的数量与斤数，来自收成接口的 stats */
const unshipped = ref<{ count: number; weight: number }>({ count: 0, weight: 0 })
const summaryRows = computed(() => [
  {
    k: '待发货收成',
    v: `${unshipped.value.count} 批 / 共 ${unshipped.value.weight} 斤（点「新建批次」发货）`
  },
  {
    k: '在途重量',
    v: `${stats.value.shippedWeightJin ?? 0} 斤（${counts.value.shipped ?? 0} 单已发货未签收）`
  },
  { k: '批次数', v: `${stats.value.batchCount ?? 0} 个批次 / ${counts.value.all ?? 0} 张配送单` },
  { k: '冷链要求', v: `${stats.value.coldChainCount ?? 0} 单冷链，0-4℃ 冷运` }
])
const tableRef = ref()
const toggleDetail = (row: any) => tableRef.value?.toggleRowExpansion(row)
const notOpen = () => message.info('演示版暂未开放')
const onTabChange = (name: string | number) => {
  queryParams.status = name === 'all' ? undefined : name
  handleQuery()
}
const loadUnshipped = async () => {
  const res = await HarvestApi.getHarvestPage({ pageNo: 1, pageSize: 1, shipped: false })
  unshipped.value = {
    count: res.stats?.unshippedCount ?? 0,
    weight: res.stats?.unshippedWeightJin ?? 0
  }
}

const getList = async () => {
  loading.value = true
  try {
    const res = await HarvestApi.getShipmentPage(queryParams)
    list.value = res.list
    total.value = res.total
    stats.value = res.stats ?? {}
    loadUnshipped()
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
  activeTab.value = 'all'
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
    message.success('已发货，状态为已发货')
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
