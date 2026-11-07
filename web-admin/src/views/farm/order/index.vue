<template>
  <ContentWrap>
    <div class="flex items-start justify-between mb-8px">
      <div>
        <div class="text-18px font-600">认养订单</div>
        <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
          认养订单 = 合同 + 标的 + 履约计划；支付成功后自动生成电子合同与溯源码
        </div>
      </div>
      <div>
        <el-button @click="notImp"><Icon icon="ep:upload" class="mr-5px" />导出 Excel</el-button>
        <el-button type="primary" @click="notImp"
          ><Icon icon="ep:plus" class="mr-5px" />代客下单</el-button
        >
      </div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane v-for="t in ORDER_TABS" :key="tabKey(t.value)" :name="tabKey(t.value)">
        <template #label>
          {{ t.label }}
          <span class="text-11px text-[var(--el-text-color-secondary)] ml-2px">
            {{ counts[tabKey(t.value)] ?? '-' }}
          </span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="订单号">
        <el-input
          v-model="queryParams.no"
          placeholder="RY..."
          clearable
          class="!w-180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input
          v-model="queryParams.memberMobile"
          placeholder="客户手机号"
          clearable
          class="!w-160px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="认养商品">
        <el-select
          v-model="queryParams.productId"
          placeholder="全部"
          clearable
          filterable
          class="!w-200px"
        >
          <el-option v-for="p in products" :key="p.id" :label="p.title" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="下单时间">
        <el-date-picker
          v-model="createRange"
          type="daterange"
          value-format="x"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="订单号" prop="no" width="160">
        <template #default="{ row }">
          <el-link type="primary" @click="openDetail(row.id)">{{ row.no }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="客户" min-width="130">
        <template #default="{ row }">
          <div class="font-600">{{ row.memberName }}</div>
          <div class="text-12px text-[var(--el-text-color-secondary)]">{{ row.memberMobile }}</div>
        </template>
      </el-table-column>
      <el-table-column label="认养商品" prop="productTitle" min-width="170" show-overflow-tooltip />
      <el-table-column label="标的编号" width="90" align="center">
        <template #default="{ row }">{{ row.assetCode || '待分配' }}</template>
      </el-table-column>
      <el-table-column
        label="金额"
        prop="amount"
        width="110"
        align="right"
        :formatter="moneyFormatter"
      />
      <el-table-column label="支付方式" prop="payMethod" width="90" align="center">
        <template #default="{ row }">{{ row.payTime ? row.payMethod : '-' }}</template>
      </el-table-column>
      <el-table-column label="合同" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="signed(row)" type="success" size="small">已签署</el-tag>
          <el-tag v-else type="warning" size="small">待签署</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="履约进度" width="130">
        <template #default="{ row }">
          <el-progress :percentage="row.progress || 0" :stroke-width="8" :show-text="false" />
          <div class="text-11px text-[var(--el-text-color-secondary)]">
            <template v-if="row.promisedJin">
              {{ row.progress }}%（{{ row.harvestedJin }}/{{ row.promisedJin }} 斤）
            </template>
            <template v-else>{{ row.progress || 0 }}%</template>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="enumOf(ORDER_STATUS, row.status).type">
            {{ enumLabel(ORDER_STATUS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="下单时间" prop="createTime" width="150" :formatter="timeFormatter" />
      <el-table-column label="操作" width="110" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-button link type="primary" @click="notImp">更多</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
    <div class="text-12px text-[var(--el-text-color-secondary)] mt-8px">
      履约进度 = 已收斤数 / 约定交付斤数，约定交付不足的部分按同等品质补发。支付方式为演示数据。
    </div>
    <div class="split-note mt-12px">
      <Icon icon="ep:lock" class="mr-5px" />
      所有订单款项进入经营者对公账户，由第三方支付机构按约定比例自动分账（见「财务结算 - 分账结算」），平台不经手资金。
    </div>
  </ContentWrap>
</template>
<script lang="ts" setup>
import * as OrderApi from '@/api/farm/order'
import * as ProductApi from '@/api/farm/product'
import {
  ORDER_STATUS,
  enumLabel,
  enumOf,
  moneyFormatter,
  timeFormatter
} from '../constants'
import { ORDER_TABS } from './constants'

defineOptions({ name: 'FarmOrder' })

const router = useRouter()
const message = useMessage()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const products = ref<any[]>([])
const createRange = ref<[string, string] | null>(null)
const activeTab = ref('all')
const counts = reactive<Record<string, number>>({})
const queryParams = reactive<any>({
  pageNo: 1,
  pageSize: 10,
  no: undefined,
  memberMobile: undefined,
  productId: undefined,
  careMode: undefined,
  status: undefined
})

const tabKey = (v: number | undefined) => (v === undefined ? 'all' : String(v))
const signed = (row: any) => !!(row.contractAgreed && row.payTime)
const notImp = () => message.info('演示环境暂不支持该操作')

/** 下单时间区间 → 毫秒时间戳；结束日按当天 23:59:59 算 */
const timeParams = () => ({
  createTimeStart: createRange.value ? Number(createRange.value[0]) : undefined,
  createTimeEnd: createRange.value ? Number(createRange.value[1]) + 86399999 : undefined
})

const getList = async () => {
  loading.value = true
  try {
    const res = await OrderApi.getOrderPage({ ...queryParams, ...timeParams() })
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}
/** 页签上的数量：不带筛选条件，每个页签取一次总数 */
const loadCounts = async () => {
  await Promise.all(
    ORDER_TABS.map(async (t) => {
      const res = await OrderApi.getOrderPage({ pageNo: 1, pageSize: 1, status: t.value })
      counts[tabKey(t.value)] = res.total
    })
  )
}
const onTabChange = (name: string | number) => {
  queryParams.status = name === 'all' ? undefined : Number(name)
  handleQuery()
}
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}
const resetQuery = () => {
  Object.assign(queryParams, {
    no: undefined,
    memberMobile: undefined,
    productId: undefined,
    careMode: undefined
  })
  createRange.value = null
  handleQuery()
}
const openDetail = (id: number) => router.push({ name: 'FarmOrderDetail', params: { id } })

onMounted(async () => {
  getList()
  loadCounts()
  const res = await ProductApi.getProductPage({ pageNo: 1, pageSize: 100 })
  products.value = res.list
})
// keep-alive 切回来时刷新（从工作台待办跳过来、或在别的页面改过数据）
let mounted = false
onActivated(() => {
  if (mounted) {
    getList()
    loadCounts()
  }
  mounted = true
})
</script>

<style lang="scss" scoped>
// 对齐原型 08：订单页底部绿色分账提示条
.split-note {
  padding: 9px 11px;
  font-size: 12px;
  line-height: 1.75;
  color: #2f7358;
  background: #eaf7f0;
  border: 1px dashed #9ad7b6;
  border-radius: 8px;
}
</style>
