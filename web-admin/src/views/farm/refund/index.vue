<template>
  <ContentWrap>
    <div class="flex items-start justify-between mb-8px">
      <div>
        <div class="text-18px font-600">退款审核</div>
        <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
          退款金额由系统按合同公式自动试算，经营者只能「通过 / 驳回」，不可随意改金额
        </div>
      </div>
      <div>
        <el-button v-hasPermi="['farm:refund:export']" @click="notImp">导出退款台账</el-button>
      </div>
    </div>

    <StatCards :items="statItems" />

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane v-for="t in REFUND_TABS" :key="tabKey(t.value)" :name="tabKey(t.value)">
        <template #label>
          {{ t.label }}
          <span class="text-11px text-[var(--el-text-color-secondary)] ml-2px">
            {{ stats.counts?.[tabKey(t.value)] ?? '-' }}
          </span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="退款单号">
        <el-input
          v-model="queryParams.no"
          placeholder="R..."
          clearable
          class="!w-180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="订单号">
        <el-input
          v-model="queryParams.orderNo"
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
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="退款单号" prop="no" width="150" />
      <el-table-column label="关联订单" width="160">
        <template #default="{ row }">
          <el-link type="primary" @click="openOrder(row.orderId)">{{ row.orderNo }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="客户" min-width="120">
        <template #default="{ row }">
          <div class="font-600">{{ row.memberName }}</div>
          <div class="text-12px text-[var(--el-text-color-secondary)]">{{ row.memberMobile }}</div>
        </template>
      </el-table-column>
      <el-table-column
        label="实付"
        prop="amount"
        width="110"
        align="right"
        :formatter="moneyFormatter"
      />
      <el-table-column label="扣减" width="110" align="right">
        <template #default="{ row }">
          <span class="text-[var(--el-text-color-secondary)]">-{{ formatMoney(row.deduct) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="应退" width="110" align="right">
        <template #default="{ row }">
          <span class="font-600 text-[var(--el-color-danger)]">{{
            formatMoney(row.refundAmount)
          }}</span>
        </template>
      </el-table-column>
      <el-table-column label="原因" prop="reason" min-width="160" show-overflow-tooltip />
      <el-table-column label="申请时间" prop="createTime" width="150" :formatter="timeFormatter" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="enumOf(REFUND_STATUS, row.status).type">
            {{ enumLabel(REFUND_STATUS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPermi="['farm:refund:approve']" v-if="row.status === 0" link type="primary" @click="openDetail(row.id)">
            审核
          </el-button>
          <el-button v-hasPermi="['farm:refund:query']" v-else link type="primary" @click="openDetail(row.id)">查看</el-button>
          <el-button v-hasPermi="['farm:refund:contact']" link type="primary" @click="notImp">联系客户</el-button>
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
      v-if="stats.reasonAlert"
      class="mt-12px"
      type="warning"
      :closable="false"
      show-icon
      :title="`近 30 天「${stats.reasonAlert.reason}」已出现 ${stats.reasonAlert.count} 单，退款原因集中在同一类时，建议复核对应商品的产出承诺口径。`"
    />
    <el-alert
      v-else
      class="mt-12px"
      type="info"
      :closable="false"
      show-icon
      title="退款原因若集中在同一类（如「产出未达约定」），系统会向经营者推送经营预警，并建议调整产出承诺口径。"
    />
  </ContentWrap>

  <el-drawer v-model="detailVisible" :title="`退款审核 — ${detail?.no ?? ''}`" size="520px">
    <div v-loading="detailLoading">
      <template v-if="detail">
        <el-alert
          :type="detail.amount > 100000 ? 'warning' : 'info'"
          :closable="false"
          show-icon
          title="审核通过后：认养标的自动释放为「空闲」，合同状态转为已解除，订单变为「已退款」。款项原路退回是真实支付能力，演示环境只变更状态。"
        />
        <el-descriptions :column="1" border class="mt-12px">
          <el-descriptions-item label="关联订单">
            <el-link type="primary" @click="openOrder(detail.orderId)">{{
              detail.orderNo
            }}</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="客户">
            {{ detail.memberName }}（{{ detail.memberMobile }}）
          </el-descriptions-item>
          <el-descriptions-item label="商品 / 标的">
            {{ detail.productTitle
            }}<template v-if="detail.assetCode"> · {{ detail.assetCode }}</template>
            <template v-if="detail.careModeName">（{{ detail.careModeName }}）</template>
          </el-descriptions-item>
          <el-descriptions-item label="实付金额">
            <span class="font-600">{{ formatMoney(detail.amount) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="退款原因">{{ detail.reason }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{
            fmtTime(detail.createTime)
          }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="enumOf(REFUND_STATUS, detail.status).type">
              {{ enumLabel(REFUND_STATUS, detail.status) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div class="mt-16px mb-8px font-600">系统试算（按合同第六条公式，申请时生成）</div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="已发生农事投入">
            -{{ formatMoney(detail.investDeduct) }}
            <span class="text-12px text-[var(--el-text-color-secondary)] ml-4px">
              认养周期已过 {{ Math.round(detail.elapsedRate * 100) }}%，按 20% 计（演示系数）
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="已交付农产品折算">
            -{{ formatMoney(detail.deliverDeduct) }}
            <span class="text-12px text-[var(--el-text-color-secondary)] ml-4px">
              <template v-if="detail.adoption?.promisedJin">
                已收 {{ detail.adoption.harvestedJin }} / 约定 {{ detail.adoption.promisedJin }} 斤
              </template>
              <template v-else>无约定交付量，不折算</template>
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="应退金额">
            <span class="font-700 text-[var(--el-color-danger)]">
              {{ formatMoney(detail.refundAmount) }}
            </span>
          </el-descriptions-item>
        </el-descriptions>
        <div class="text-12px text-[var(--el-text-color-secondary)] mt-8px">
          金额来源于平台统一规则：已投入按认养周期已过比例试算。
        </div>

        <template v-if="detail.status === 0">
          <div class="mt-16px mb-8px font-600">审核意见</div>
          <el-input
            v-model="remark"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请填写审核说明（将同步给客户；驳回时必填）"
          />
          <div class="mt-16px text-right">
            <el-button v-hasPermi="['farm:refund:approve']" :loading="submitting" @click="submitAudit(false)">驳回</el-button>
            <el-button v-hasPermi="['farm:refund:approve']" type="primary" :loading="submitting" @click="submitAudit(true)">
              通过并退款
            </el-button>
          </div>
        </template>
        <el-descriptions v-else :column="1" border class="mt-16px">
          <el-descriptions-item label="审核意见">{{
            detail.auditRemark || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="审核时间">{{
            fmtTime(detail.auditTime)
          }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </div>
  </el-drawer>
</template>
<script lang="ts" setup>
import * as RefundApi from '@/api/farm/refund'
import { enumLabel, enumOf, fmtTime, formatMoney, moneyFormatter, timeFormatter } from '../constants'
import StatCards from '../fulfill/StatCards.vue'
import { REFUND_STATUS, REFUND_TABS } from './constants'

defineOptions({ name: 'FarmRefund' })

const router = useRouter()
const message = useMessage()
const loading = ref(false)
const list = ref<RefundApi.RefundVO[]>([])
const total = ref(0)
const stats = ref<Partial<RefundApi.RefundStatsVO>>({})
const activeTab = ref('all')
const queryParams = reactive<any>({
  pageNo: 1,
  pageSize: 10,
  no: undefined,
  orderNo: undefined,
  memberMobile: undefined,
  status: undefined
})

const tabKey = (v: number | undefined) => (v === undefined ? 'all' : String(v))
const notImp = () => message.info('演示环境暂不支持该操作')
const openOrder = (id: number) => router.push({ name: 'FarmOrderDetail', params: { id } })

const statItems = computed(() => {
  const s = stats.value
  return [
    {
      label: '本月申请',
      value: s.monthApply ?? '-',
      sub: s.refundRate != null ? `退费率 ${s.refundRate}%` : '本月暂无已付订单'
    },
    {
      label: '待审核',
      value: s.pending ?? '-',
      sub: s.pendingOver24h ? `超 24h 未处理 ${s.pendingOver24h} 单` : '无超时未处理',
      tone: s.pendingOver24h ? ('warn' as const) : undefined
    },
    {
      label: '平均处理时长',
      value: s.avgHandleHours != null ? `${s.avgHandleHours}h` : '-',
      sub: s.avgHandleHours != null ? '已审核退款单的申请到审核' : '暂无已审核的退款单'
    },
    {
      label: '已退金额',
      value: formatMoney(s.refundedAmount ?? 0),
      sub: `涉及 ${s.refundedCount ?? 0} 单`
    }
  ]
})

const getList = async () => {
  loading.value = true
  try {
    const res = await RefundApi.getRefundPage(queryParams)
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}
const loadStats = async () => {
  stats.value = await RefundApi.getRefundStats()
}
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}
const resetQuery = () => {
  Object.assign(queryParams, { no: undefined, orderNo: undefined, memberMobile: undefined })
  handleQuery()
}
const onTabChange = (name: string | number) => {
  queryParams.status = name === 'all' ? undefined : Number(name)
  handleQuery()
}

// ---- 审核抽屉 ----
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<RefundApi.RefundDetailVO>()
const remark = ref('')
const submitting = ref(false)

const openDetail = async (id: number) => {
  detailVisible.value = true
  detailLoading.value = true
  remark.value = ''
  detail.value = undefined
  try {
    detail.value = await RefundApi.getRefund(id)
  } finally {
    detailLoading.value = false
  }
}
const submitAudit = async (approve: boolean) => {
  if (!detail.value) return
  if (!approve && !remark.value.trim()) {
    message.warning('驳回时请填写审核说明')
    return
  }
  if (approve) {
    try {
      await message.confirm(
        `确认通过并退款 ${formatMoney(detail.value.refundAmount)}？通过后认养标的将释放为空闲，合同解除。`
      )
    } catch {
      return
    }
  }
  submitting.value = true
  try {
    await RefundApi.auditRefund({ id: detail.value.id, approve, remark: remark.value.trim() })
    message.success(approve ? '已通过，订单已变为「已退款」' : '已驳回，订单恢复为「认养中」')
    detailVisible.value = false
    await Promise.all([getList(), loadStats()])
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  getList()
  loadStats()
})
let mounted = false
onActivated(() => {
  if (mounted) {
    getList()
    loadStats()
  }
  mounted = true
})
</script>
