<!-- 权限串：farm:financesplit:query（本页只读展示，真实分账规则编辑、新增分账方未开放） -->
<template>
  <div v-loading="loading">
    <div class="flex items-start justify-between mb-12px">
      <div>
        <div class="text-18px font-600">
          分账结算
          <el-tag type="warning" size="small" effect="plain" class="ml-6px">演示数据</el-tag>
        </div>
        <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
          多方合作场景（农户 / 合作社 / 平台 / 渠道）下的自动分账规则；规则由支付机构执行，未接入真实分账
        </div>
      </div>
      <el-button v-hasPermi="['farm:financesplit:create']" type="primary" @click="message.info('演示版暂未开放')">
        <Icon icon="ep:plus" class="mr-5px" />新增分账方
      </el-button>
    </div>

    <el-row :gutter="12">
      <el-col :xs="24" :lg="15">
        <el-card shadow="never" class="mb-12px">
          <template #header>
            <span class="font-700"
              >默认分账规则
              <span v-if="data.sampleProductTitle" class="font-400 text-13px text-[var(--el-text-color-secondary)]"
                >（{{ data.sampleProductTitle }}）</span
              ></span
            >
          </template>
          <el-table :data="data.rules || []">
            <el-table-column label="分账方" prop="name" min-width="170">
              <template #default="{ row }"><b>{{ row.name }}</b></template>
            </el-table-column>
            <el-table-column label="类型" width="100">
              <template #default="{ row }">
                <el-tag :type="row.key === 'platform' ? 'primary' : row.key === 'operator' ? 'success' : 'info'" effect="plain">{{ row.type }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="比例" width="80" align="right">
              <template #default="{ row }"><b>{{ row.percent }}%</b></template>
            </el-table-column>
            <el-table-column :label="`单笔示例（${formatMoney(data.exampleAmount)}）`" width="170" align="right">
              <template #default="{ row }">{{ formatMoney(row.exampleAmount) }}</template>
            </el-table-column>
          </el-table>
          <div class="mt-8px text-12px text-[var(--el-text-color-secondary)]">
            规则由支付机构执行，资金不经过平台账户；每方可在各自商户后台查看流水。比例为演示值。
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="9">
        <el-card shadow="never" class="mb-12px">
          <template #header><span class="font-700">结算账户</span></template>
          <div v-for="x in accountRows" :key="x.k" class="kv">
            <span class="k">{{ x.k }}</span>
            <span>{{ x.v }}</span>
          </div>
          <el-alert
            class="mt-10px"
            type="success"
            :closable="false"
            show-icon
            title="个人账户不可作为结算账户。账户信息为演示，真实实名核验未接入。"
          />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="mb-12px">
          <template #header
            ><span class="font-700">结算明细（{{ data.month?.label }}）</span></template
          >
          <div v-for="x in data.month?.items || []" :key="x.key" class="kv">
            <span class="k">{{ x.label }}</span>
            <span :class="x.key === 'operator' ? 'font-700' : ''">{{ formatMoney(x.amount) }}</span>
          </div>
          <el-divider class="!my-10px" />
          <div class="kv">
            <span class="k">合计（核对项）</span>
            <span class="font-700">{{ formatMoney(data.month?.total ?? 0) }}</span>
          </div>
          <div class="text-12px text-[var(--el-text-color-secondary)] mt-6px">
            合计 = 近 30 天交易流水总额，与「交易流水」页一致；分成按左侧规则比例计算。
          </div>
          <el-divider class="!my-10px" />
          <div class="font-600 mb-6px">代管工单分账（近 30 天已完成 {{ data.serviceSplit?.count ?? 0 }} 单，真实比例）</div>
          <div class="kv"><span class="k">工单金额</span><span>{{ formatMoney(data.serviceSplit?.total ?? 0) }}</span></div>
          <div class="kv"><span class="k">管家</span><span>{{ formatMoney(data.serviceSplit?.steward ?? 0) }}</span></div>
          <div class="kv"><span class="k">农场</span><span>{{ formatMoney(data.serviceSplit?.farm ?? 0) }}</span></div>
          <div class="kv"><span class="k">平台</span><span>{{ formatMoney(data.serviceSplit?.platform ?? 0) }}</span></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="mb-12px">
          <template #header>
            <span class="font-700"
              >发票管理
              <el-tag type="warning" size="small" effect="plain" class="ml-6px">演示</el-tag></span
            >
          </template>
          <el-table :data="data.invoices || []" empty-text="暂无已支付订单">
            <el-table-column label="发票抬头" prop="title" min-width="150" />
            <el-table-column label="金额" width="110" align="right">
              <template #default="{ row }"><b>{{ formatMoney(row.amount) }}</b></template>
            </el-table-column>
            <el-table-column label="类型" prop="kind" width="100" />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.statusName === '申请中' ? 'warning' : 'success'">{{ row.statusName }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div class="mt-8px text-12px text-[var(--el-text-color-secondary)]">
            抬头和金额取自金额最高的 3 笔已支付认养订单，开票状态为演示，未接入电子发票。
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
<script lang="ts" setup>
import * as FinanceApi from '@/api/farm/finance'
import { formatMoney } from '../constants'

defineOptions({ name: 'FarmFinanceSplit' })

const message = useMessage()
const loading = ref(false)
const data = ref<any>({})
const accountRows = computed(() => {
  const a = data.value.account
  if (!a) return []
  return [
    { k: '账户类型', v: a.type },
    { k: '开户名', v: a.name },
    { k: '开户行', v: a.bank },
    { k: '结算周期', v: a.cycle },
    { k: '开票方', v: a.invoiceBy }
  ]
})

const load = async () => {
  loading.value = true
  try {
    data.value = await FinanceApi.getSplit()
  } finally {
    loading.value = false
  }
}
onMounted(load)
let mounted = false
onActivated(() => {
  if (mounted) load()
  mounted = true
})
</script>
<style scoped>
.kv {
  display: flex;
  padding: 6px 0;
  font-size: 13px;
}
.kv .k {
  width: 110px;
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
}
</style>
