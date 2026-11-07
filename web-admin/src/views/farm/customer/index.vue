<template>
  <ContentWrap>
    <div class="flex items-start justify-between mb-8px">
      <div>
        <div class="text-18px font-600">客户管理</div>
        <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
          共 {{ stats.total ?? '-' }} 位认养人；系统按 RFM 自动分层，名单由会员与订单派生，等级为按累计消费的演示区间
        </div>
      </div>
      <div>
        <el-button @click="notImp">导出名单</el-button>
        <el-button @click="notImp">批量打标签</el-button>
        <el-button type="primary" @click="notImp"
          ><Icon icon="ep:plus" class="mr-5px" />新增客户</el-button
        >
      </div>
    </div>

    <StatCards :items="statItems" />

    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="姓名/手机">
        <el-input
          v-model="queryParams.keyword"
          placeholder="姓名或手机号"
          clearable
          class="!w-180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="分层">
        <el-select v-model="queryParams.tier" placeholder="全部" clearable class="!w-130px">
          <el-option v-for="t in TIER_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="客户标签">
        <el-select v-model="queryParams.tag" placeholder="全部" clearable class="!w-130px">
          <el-option v-for="t in stats.tags ?? []" :key="t" :label="t" :value="t" />
        </el-select>
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
      <el-form-item label="消费区间">
        <el-select v-model="spendIdx" placeholder="全部" clearable class="!w-140px">
          <el-option
            v-for="(s, i) in SPEND_RANGES"
            :key="s.label"
            :label="s.label"
            :value="i"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="最近活跃">
        <el-select v-model="queryParams.activeDays" placeholder="全部" clearable class="!w-120px">
          <el-option v-for="a in ACTIVE_RANGES" :key="a.days" :label="a.label" :value="a.days" />
        </el-select>
      </el-form-item>
      <el-form-item label="城市">
        <el-select v-model="queryParams.city" placeholder="全部" clearable class="!w-120px">
          <el-option v-for="c in stats.cities ?? []" :key="c" :label="c" :value="c" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />查询</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="客户" min-width="150">
        <template #default="{ row }">
          <div class="flex items-center gap-8px">
            <el-avatar :size="32">{{ (row.name || '?').charAt(0) }}</el-avatar>
            <div>
              <div class="font-600">{{ row.name }}</div>
              <div class="text-12px text-[var(--el-text-color-secondary)]">{{ row.mobile }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="等级" width="80" align="center">
        <template #default="{ row }">
          <el-tag type="warning" size="small">{{ row.level }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="分层" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="TIER_TAG_TYPE[row.tier]" size="small">{{ row.tierName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="城市" prop="city" width="90">
        <template #default="{ row }">{{ row.city || '-' }}</template>
      </el-table-column>
      <el-table-column label="累计认养" width="100" align="right">
        <template #default="{ row }">{{ row.adoptCount }} 单</template>
      </el-table-column>
      <el-table-column label="累计消费" width="120" align="right">
        <template #default="{ row }">
          <span class="font-600">{{ formatMoney(row.totalAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="标签" min-width="170">
        <template #default="{ row }">
          <el-tag v-for="t in row.tags" :key="t" size="small" class="mr-4px">{{ t }}</el-tag>
          <span v-if="!row.tags.length">-</span>
        </template>
      </el-table-column>
      <el-table-column label="最近活跃" prop="lastActive" width="150" :formatter="timeFormatter" />
      <el-table-column label="续费倾向" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="RENEW_TAG_TYPE[row.renewIntent]" :type="RENEW_TAG_TYPE[row.renewIntent]" size="small">
            {{ row.renewIntent }}
          </el-tag>
          <span v-else>{{ row.renewIntent }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" align="center" fixed="right">
        <template #default>
          <el-button link type="primary" @click="notImp">档案</el-button>
          <el-button link type="primary" @click="notImp">发消息</el-button>
          <el-button link type="primary" @click="notImp">关怀</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
    <el-alert class="mt-12px" type="info" :closable="false" show-icon :title="COMPLIANCE_NOTE" />
  </ContentWrap>
</template>
<script lang="ts" setup>
import * as CustomerApi from '@/api/farm/customer'
import * as ProductApi from '@/api/farm/product'
import { formatMoney, timeFormatter } from '../constants'
import StatCards from '../fulfill/StatCards.vue'
import {
  ACTIVE_RANGES,
  COMPLIANCE_NOTE,
  RENEW_TAG_TYPE,
  SPEND_RANGES,
  TIER_OPTIONS,
  TIER_TAG_TYPE
} from './constants'

defineOptions({ name: 'FarmCustomer' })

const route = useRoute()
const message = useMessage()
const loading = ref(false)
const list = ref<CustomerApi.CustomerVO[]>([])
const total = ref(0)
const stats = ref<Partial<CustomerApi.CustomerStatsVO>>({})
const products = ref<any[]>([])
const spendIdx = ref<number | undefined>(undefined)
const queryParams = reactive<any>({
  pageNo: 1,
  pageSize: 10,
  keyword: undefined,
  tier: undefined,
  tag: undefined,
  productId: undefined,
  activeDays: undefined,
  city: undefined,
  expiring: undefined,
  minAmount: undefined,
  maxAmount: undefined
})

const notImp = () => message.info('演示环境暂不支持该操作')

const statItems = computed(() => {
  const s = stats.value
  return [
    { label: '总客户数', value: s.total ?? '-', sub: `本月新增 ${s.newThisMonth ?? 0}` },
    { label: '认养中', value: s.adopting ?? '-', sub: `30 天内到期 ${s.expiring30 ?? 0} 人` },
    {
      label: '复购/续费率',
      value: s.repeatRate != null ? `${s.repeatRate}%` : '-',
      sub: '已付订单 ≥ 2 单的认养人占比'
    },
    { label: '平均客单价', value: formatMoney(s.avgOrderAmount ?? 0), sub: '累计消费 / 已付订单数' },
    { label: '到访率', value: '暂无数据', sub: '尚未接入到访登记', tone: 'muted' as const }
  ]
})

const getList = async () => {
  loading.value = true
  try {
    const range = spendIdx.value === undefined ? undefined : SPEND_RANGES[spendIdx.value]
    queryParams.minAmount = range?.min
    queryParams.maxAmount = range?.max
    const res = await CustomerApi.getCustomerPage(queryParams)
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
    keyword: undefined,
    tier: undefined,
    tag: undefined,
    productId: undefined,
    activeDays: undefined,
    city: undefined,
    expiring: undefined
  })
  spendIdx.value = undefined
  handleQuery()
}

onMounted(async () => {
  // 客户分层页「明细」会带 ?tier=xxx 过来
  if (typeof route.query.tier === 'string') queryParams.tier = route.query.tier
  if (route.query.expiring === '1') queryParams.expiring = 1
  getList()
  stats.value = await CustomerApi.getCustomerStats()
  const res = await ProductApi.getProductPage({ pageNo: 1, pageSize: 100 })
  products.value = res.list
})
let mounted = false
onActivated(() => {
  if (mounted) getList()
  mounted = true
})
</script>
