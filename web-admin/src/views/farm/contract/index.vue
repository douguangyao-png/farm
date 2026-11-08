<template>
  <ContentWrap>
    <div class="flex items-start justify-between mb-12px">
      <div>
        <div class="text-18px font-600">合同管理</div>
        <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
          合同台账由认养订单派生；系统内置 4 套合规合同模板，签署后 CA 认证与区块链存证为演示数据
        </div>
      </div>
      <div>
        <el-button v-hasPermi="['farm:contract:template']" @click="notImp">模板管理</el-button>
        <el-button v-hasPermi="['farm:contract:create']" type="primary" @click="notImp"
          ><Icon icon="ep:plus" class="mr-5px" />发起签署</el-button
        >
      </div>
    </div>
    <el-row :gutter="12">
      <el-col v-for="k in kpis" :key="k.label" :xs="12" :md="6">
        <el-card shadow="never" class="mb-12px">
          <div class="text-13px text-[var(--el-text-color-secondary)]">{{ k.label }}</div>
          <div class="text-24px font-700 my-4px">{{ counts[k.key] ?? '-' }}</div>
          <div class="text-12px text-[var(--el-text-color-secondary)]">{{ k.hint }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="合同编号">
        <el-input
          v-model="queryParams.no"
          placeholder="HT... 或订单号"
          clearable
          class="!w-200px"
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
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部" clearable class="!w-120px">
          <el-option v-for="s in CONTRACT_STATUS" :key="s.value" :label="s.label" :value="s.value" />
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
      <el-table-column label="合同编号" prop="no" width="150" />
      <el-table-column label="关联订单" width="160">
        <template #default="{ row }">
          <el-link type="primary" @click="openOrder(row.orderId)">{{ row.orderNo }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="客户" min-width="130">
        <template #default="{ row }">
          <div class="font-600">{{ row.memberName }}</div>
          <div class="text-12px text-[var(--el-text-color-secondary)]">{{ row.memberMobile }}</div>
        </template>
      </el-table-column>
      <el-table-column label="关联商品" prop="productTitle" min-width="170" show-overflow-tooltip />
      <el-table-column label="版本" prop="version" width="70" align="center" />
      <el-table-column label="签署方式" prop="signMethod" width="90" align="center" />
      <el-table-column label="CA 认证" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.caAuth" size="small" type="success">已认证</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="区块链存证" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.chainProof" size="small">已上链</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="enumOf(CONTRACT_STATUS, row.status).type">
            {{ enumLabel(CONTRACT_STATUS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="签署时间" prop="signedAt" width="150" :formatter="timeFormatter" />
      <el-table-column label="创建时间" prop="createTime" width="150" :formatter="timeFormatter" />
      <el-table-column label="操作" width="190" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openOrder(row.orderId)">查看订单</el-button>
          <el-button v-hasPermi="['farm:contract:download']" link type="primary" @click="notImp">下载</el-button>
          <el-button link type="primary" @click="notImp">验真</el-button>
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
      <ContentWrap title="合同模板库（演示数据）">
        <el-table :data="CONTRACT_TEMPLATES">
          <el-table-column label="模板名称" prop="name" min-width="160" />
          <el-table-column label="适用品类" prop="scope" width="110" />
          <el-table-column label="版本" width="80" align="center">
            <template #default="{ row }"
              ><el-tag size="small">{{ row.version }}</el-tag></template
            >
          </el-table-column>
          <el-table-column label="最近更新" prop="updated" width="110" />
        </el-table>
        <div class="text-12px text-[var(--el-text-color-secondary)] mt-8px">
          模板更新后仅对新订单生效，历史合同不可追溯修改，确保证据效力。
        </div>
      </ContentWrap>
    </el-col>
    <el-col :xs="24" :lg="12">
      <ContentWrap title="反欺诈条款（所有模板内置）">
        <el-descriptions :column="1" border>
          <el-descriptions-item v-for="c in ANTI_FRAUD_CLAUSES" :key="c" :label="c">
            <Icon icon="ep:circle-check-filled" color="var(--el-color-success)" class="mr-4px" />已内置
          </el-descriptions-item>
        </el-descriptions>
        <el-alert
          class="mt-8px"
          type="info"
          :closable="false"
          show-icon
          title="条款不可由经营者删除，仅可追加更有利于消费者的条款。"
        />
      </ContentWrap>
    </el-col>
  </el-row>
</template>
<script lang="ts" setup>
import * as ContractApi from '@/api/farm/contract'
import { enumLabel, enumOf, timeFormatter } from '../constants'
import { ANTI_FRAUD_CLAUSES, CONTRACT_STATUS, CONTRACT_TEMPLATES } from './constants'

defineOptions({ name: 'FarmContract' })

const router = useRouter()
const message = useMessage()
const loading = ref(false)
const list = ref<ContractApi.ContractVO[]>([])
const total = ref(0)
const counts = reactive<Record<string, number>>({})
const queryParams = reactive<any>({
  pageNo: 1,
  pageSize: 10,
  no: undefined,
  memberMobile: undefined,
  status: undefined
})

const kpis = [
  { key: 'all', label: '合同总数', hint: '含已解除' },
  { key: '10', label: '已签署', hint: '支付成功后自动签署归档' },
  { key: '0', label: '待签署', hint: '未支付订单的合同' },
  { key: '20', label: '已解除', hint: '订单取消后合同解除' }
]

const notImp = () => message.info('演示环境暂不支持该操作')
const openOrder = (id: number) => router.push({ name: 'FarmOrderDetail', params: { id } })

const getList = async () => {
  loading.value = true
  try {
    const res = await ContractApi.getContractPage(queryParams)
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}
/** 统计卡：各状态取一次总数（不带筛选条件） */
const loadCounts = async () => {
  await Promise.all(
    [undefined, 10, 0, 20].map(async (s) => {
      const res = await ContractApi.getContractPage({ pageNo: 1, pageSize: 1, status: s })
      counts[s === undefined ? 'all' : String(s)] = res.total
    })
  )
}
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}
const resetQuery = () => {
  Object.assign(queryParams, { no: undefined, memberMobile: undefined, status: undefined })
  handleQuery()
}

onMounted(() => {
  getList()
  loadCounts()
})
let mounted = false
onActivated(() => {
  if (mounted) {
    getList()
    loadCounts()
  }
  mounted = true
})
</script>
