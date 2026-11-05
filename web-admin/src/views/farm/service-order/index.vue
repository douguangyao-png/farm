<template>
  <ContentWrap>
    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="工单号">
        <el-input
          v-model="queryParams.no"
          placeholder="DG..."
          clearable
          class="!w-200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部" clearable class="!w-120px">
          <el-option
            v-for="s in SERVICE_ORDER_STATUS"
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
    <el-table v-loading="loading" :data="list">
      <el-table-column label="工单号" prop="no" width="160">
        <template #default="{ row }">
          <el-link type="primary" @click="openDetail(row)">{{ row.no }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="服务内容" prop="taskTitle" min-width="170" />
      <el-table-column label="服务类型" prop="serviceType" width="100" />
      <el-table-column label="地块" prop="assetCode" width="80" />
      <el-table-column label="认养人" prop="memberName" width="100" />
      <el-table-column label="管家" prop="stewardName" width="100">
        <template #default="{ row }">{{ row.stewardName || '待分配' }}</template>
      </el-table-column>
      <el-table-column
        label="价格"
        prop="price"
        width="100"
        align="right"
        :formatter="moneyFormatter"
      />
      <el-table-column label="来源" width="110">
        <template #default="{ row }">{{ row.fromTaskId ? '自管任务委托' : '认养人下单' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="enumOf(SERVICE_ORDER_STATUS, row.status).type">
            {{ enumLabel(SERVICE_ORDER_STATUS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="150" :formatter="timeFormatter" />
      <el-table-column label="操作" width="80" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
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

  <el-drawer v-model="detailVisible" :title="`代管工单 ${detail?.no ?? ''}`" size="520px">
    <div v-loading="detailLoading">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="服务内容">{{ detail.taskTitle }}</el-descriptions-item>
          <el-descriptions-item label="服务类型">{{ detail.serviceType }}</el-descriptions-item>
          <el-descriptions-item label="地块">{{ detail.assetCode }}</el-descriptions-item>
          <el-descriptions-item label="认养人">{{ detail.memberName }}</el-descriptions-item>
          <el-descriptions-item label="管家">{{
            detail.stewardName || '待分配'
          }}</el-descriptions-item>
          <el-descriptions-item label="价格">{{ formatMoney(detail.price) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="enumOf(SERVICE_ORDER_STATUS, detail.status).type">
              {{ enumLabel(SERVICE_ORDER_STATUS, detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="下单">{{ fmtTime(detail.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="接单">{{ fmtTime(detail.acceptTime) }}</el-descriptions-item>
          <el-descriptions-item label="完成">{{ fmtTime(detail.finishTime) }}</el-descriptions-item>
        </el-descriptions>

        <div class="mt-16px mb-8px font-600">分账规则（演示，未执行）</div>
        <el-table :data="splitRows" size="small" border>
          <el-table-column label="分给" prop="name" />
          <el-table-column label="比例" width="80" align="center">
            <template #default="{ row }">{{ row.pct }}%</template>
          </el-table-column>
          <el-table-column label="金额" width="120" align="right">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
        </el-table>

        <div class="mt-16px mb-8px font-600">完成凭证</div>
        <div class="flex flex-wrap gap-6px">
          <el-image
            v-for="(p, i) in detail.proofPhotos"
            :key="i"
            :src="p"
            :preview-src-list="detail.proofPhotos"
            :initial-index="Number(i)"
            preview-teleported
            fit="cover"
            class="w-100px h-100px rounded-4px"
          />
          <span
            v-if="!detail.proofPhotos?.length"
            class="text-12px text-[var(--el-text-color-secondary)]"
          >
            管家完成后上传
          </span>
        </div>
      </template>
    </div>
  </el-drawer>
</template>
<script lang="ts" setup>
import * as ServiceOrderApi from '@/api/farm/serviceOrder'
import {
  SERVICE_ORDER_STATUS,
  enumLabel,
  enumOf,
  fmtTime,
  formatMoney,
  moneyFormatter,
  timeFormatter
} from '../constants'

defineOptions({ name: 'FarmServiceOrder' })

const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const queryParams = reactive<any>({ pageNo: 1, pageSize: 10, no: undefined, status: undefined })

const getList = async () => {
  loading.value = true
  try {
    const res = await ServiceOrderApi.getServiceOrderPage(queryParams)
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
  queryParams.no = undefined
  queryParams.status = undefined
  handleQuery()
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<any>()
const openDetail = async (row: any) => {
  detail.value = row
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await ServiceOrderApi.getServiceOrder(row.id)
  } finally {
    detailLoading.value = false
  }
}
const splitRows = computed(() => {
  const d = detail.value
  if (!d?.split) return []
  const price = Number(d.price) || 0
  return [
    { name: '管家', pct: d.split.steward },
    { name: '农场', pct: d.split.farm },
    { name: '平台', pct: d.split.platform }
  ].map((x) => ({ ...x, amount: Math.round((price * x.pct) / 100) }))
})

onMounted(getList)
// keep-alive 切回来时刷新（从工作台待办跳过来、或在别的页面改过数据）
let mounted = false
onActivated(() => {
  if (mounted) getList()
  mounted = true
})
</script>
