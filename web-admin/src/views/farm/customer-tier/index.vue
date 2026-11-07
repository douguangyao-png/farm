<template>
  <ContentWrap>
    <div class="flex items-start justify-between mb-12px">
      <div>
        <div class="text-18px font-600">客户分层与社群运营</div>
        <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
          按 RFM 模型自动分层；不同层级匹配不同运营动作，提高续费率。人数实时由会员与订单计算
        </div>
      </div>
      <div>
        <el-button type="primary" @click="notImp"
          ><Icon icon="ep:plus" class="mr-5px" />新建人群包</el-button
        >
      </div>
    </div>

    <el-row v-loading="loading" :gutter="12">
      <el-col :xs="24" :lg="15">
        <el-card shadow="never" :body-style="{ padding: '14px' }">
          <template #header>
            <span class="font-600">人群分层</span>
            <span class="text-12px text-[var(--el-text-color-secondary)] ml-8px">
              共 {{ summary.total ?? '-' }} 位客户
            </span>
          </template>
          <div
            v-for="t in summary.tiers ?? []"
            :key="t.key"
            class="border-1 border-solid border-[var(--el-border-color-lighter)] rounded-6px p-12px mb-10px"
          >
            <div class="flex items-center">
              <span class="font-600">{{ t.name }}</span>
              <el-tag size="small" type="success" class="ml-8px">{{ t.count }} 人</el-tag>
              <el-tag size="small" :type="t.levelType" class="ml-auto">{{ t.level }}</el-tag>
            </div>
            <div class="text-12px text-[var(--el-text-color-secondary)] mt-6px">
              判定规则：{{ t.rule }}
            </div>
            <div class="text-12px text-[var(--el-text-color-secondary)] mt-2px">
              建议动作：{{ t.action }}
            </div>
            <div class="text-right">
              <el-button link type="primary" @click="openCustomers({ tier: t.key })"
                >查看名单</el-button
              >
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="9">
        <el-card shadow="never" :body-style="{ padding: '14px' }">
          <template #header><span class="font-600">即将到期提醒</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="30 天内到期">
              <span class="font-600">{{ summary.expiring?.customerCount ?? '-' }} 人</span>
              <span class="text-12px text-[var(--el-text-color-secondary)] ml-4px">
                {{ summary.expiring?.adoptionCount ?? 0 }} 个认养
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="预计续费金额">
              <span class="font-600">{{ formatMoney(summary.expiring?.estimateAmount ?? 0) }}</span>
              <span class="text-12px text-[var(--el-text-color-secondary)] ml-4px"
                >按原订单金额估算</span
              >
            </el-descriptions-item>
            <el-descriptions-item label="提醒节点">
              到期前 {{ (summary.expiring?.remindDays ?? []).join(' / ') }} 天
            </el-descriptions-item>
          </el-descriptions>
          <div class="text-12px text-[var(--el-text-color-secondary)] mt-10px">{{ REMIND_HINT }}</div>
          <el-alert class="mt-10px" type="warning" :closable="false" show-icon :title="EXPIRE_NOTE" />
        </el-card>
      </el-col>
    </el-row>
  </ContentWrap>

  <ContentWrap title="人群包（规则圈选，只读）">
    <el-table v-loading="loading" :data="summary.packs ?? []">
      <el-table-column label="人群包名称" prop="name" min-width="150" />
      <el-table-column label="人数" width="100">
        <template #default="{ row }">{{ row.count }} 人</template>
      </el-table-column>
      <el-table-column label="构建方式" prop="buildWay" min-width="280" show-overflow-tooltip />
      <el-table-column label="最近更新" width="150">
        <template #default>{{ fmtTime(summary.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="notImp">推送</el-button>
          <el-button link type="primary" @click="openPack(row)">明细</el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>
</template>
<script lang="ts" setup>
import * as CustomerApi from '@/api/farm/customer'
import { fmtTime, formatMoney } from '../constants'
import { EXPIRE_NOTE, REMIND_HINT } from './constants'

defineOptions({ name: 'FarmCustomerTier' })

const router = useRouter()
const message = useMessage()
const loading = ref(false)
const summary = ref<Partial<CustomerApi.TierSummaryVO>>({})

const notImp = () => message.info('演示环境暂不支持该操作')
const openCustomers = (query: Record<string, string>) => router.push({ name: 'FarmCustomer', query })
const openPack = (p: { query: string; tier: string | null }) => {
  if (p.query === 'tier' && p.tier) return openCustomers({ tier: p.tier })
  if (p.query === 'expiring') return openCustomers({ expiring: '1' })
  return openCustomers({})
}

const load = async () => {
  loading.value = true
  try {
    summary.value = await CustomerApi.getTierSummary()
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
