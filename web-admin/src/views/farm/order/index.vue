<template>
  <ContentWrap>
    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="订单号">
        <el-input
          v-model="queryParams.no"
          placeholder="RY..."
          clearable
          class="!w-200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input
          v-model="queryParams.memberMobile"
          placeholder="认养人手机号"
          clearable
          class="!w-180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="托管方式">
        <el-select v-model="queryParams.careMode" placeholder="全部" clearable class="!w-120px">
          <el-option v-for="m in CARE_MODES" :key="m.value" :label="m.label" :value="m.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部" clearable class="!w-120px">
          <el-option v-for="s in ORDER_STATUS" :key="s.value" :label="s.label" :value="s.value" />
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
      <el-table-column label="订单号" prop="no" width="160">
        <template #default="{ row }">
          <el-link type="primary" @click="openDetail(row.id)">{{ row.no }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="认养商品" prop="productTitle" min-width="170" show-overflow-tooltip />
      <el-table-column label="认养人" min-width="140">
        <template #default="{ row }">
          <div>{{ row.memberName }}</div>
          <div class="text-12px text-[var(--el-text-color-secondary)]">{{ row.memberMobile }}</div>
        </template>
      </el-table-column>
      <el-table-column label="托管方式" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="enumOf(CARE_MODES, row.careMode).type" effect="plain">
            {{ enumLabel(CARE_MODES, row.careMode) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="金额"
        prop="amount"
        width="110"
        align="right"
        :formatter="moneyFormatter"
      />
      <el-table-column label="地块" prop="assetCode" width="80" align="center">
        <template #default="{ row }">{{ row.assetCode || '-' }}</template>
      </el-table-column>
      <el-table-column label="合同" prop="contractVersion" width="70" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="enumOf(ORDER_STATUS, row.status).type">
            {{ enumLabel(ORDER_STATUS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="下单时间" prop="createTime" width="150" :formatter="timeFormatter" />
      <el-table-column label="支付时间" prop="payTime" width="150" :formatter="timeFormatter" />
      <el-table-column label="操作" width="80" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
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
</template>
<script lang="ts" setup>
import * as OrderApi from '@/api/farm/order'
import {
  CARE_MODES,
  ORDER_STATUS,
  enumLabel,
  enumOf,
  moneyFormatter,
  timeFormatter
} from '../constants'

defineOptions({ name: 'FarmOrder' })

const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const queryParams = reactive<any>({
  pageNo: 1,
  pageSize: 10,
  no: undefined,
  memberMobile: undefined,
  careMode: undefined,
  status: undefined
})

const getList = async () => {
  loading.value = true
  try {
    const res = await OrderApi.getOrderPage(queryParams)
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
    no: undefined,
    memberMobile: undefined,
    careMode: undefined,
    status: undefined
  })
  handleQuery()
}
const openDetail = (id: number) => router.push({ name: 'FarmOrderDetail', params: { id } })

onMounted(getList)
// keep-alive 切回来时刷新（从工作台待办跳过来、或在别的页面改过数据）
let mounted = false
onActivated(() => {
  if (mounted) getList()
  mounted = true
})
</script>
