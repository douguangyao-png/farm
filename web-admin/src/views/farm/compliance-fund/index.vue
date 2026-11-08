<!-- 权限串：farm:compliancefund:query / farm:compliancefund:update。资金用途占比为演示口径，平台服务费一行为真实 3% -->
<template>
  <div v-loading="loading">
    <div class="flex items-start justify-between mb-12px">
      <div>
        <div class="text-18px font-600">资金公示</div>
        <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
          向 C 端公开资金去向，是这个行业建立信任成本最低、效果最好的动作
        </div>
      </div>
      <el-button v-hasPermi="['farm:compliancefund:update']" type="primary" :loading="saving" @click="save">保存公示设置</el-button>
    </div>

    <el-row :gutter="12">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="mb-12px">
          <template #header><span class="font-700">C 端展示预览</span></template>
          <div class="preview">
            <div class="preview-hd">小程序 · 资金公示模块</div>
            <div class="p-12px">
              <div v-if="on('flowChart')">
                <div class="font-700 text-13px">资金流向说明</div>
                <div class="flex items-center mt-10px gap-6px">
                  <template v-for="(s, i) in data.preview?.steps || []" :key="s">
                    <div class="step">{{ s }}</div>
                    <span v-if="Number(i) < 3" class="text-[var(--el-color-primary)]">→</span>
                  </template>
                </div>
              </div>
              <div class="mt-10px text-13px leading-22px">
                <div v-if="on('payeeName')">收款主体：{{ data.preview?.payeeName }}</div>
                <div v-if="on('channelName')">支付通道：{{ data.preview?.channelName }}</div>
                <div v-if="on('feeRate')">平台服务费率：{{ data.preview?.feeRate }}%</div>
                <div v-if="on('policyNo')">保单编号：{{ data.preview?.policyNo || '（未上传保单）' }}</div>
                <div v-if="on('monthlyTotal')">
                  近 30 天认养总额：{{ formatMoney(data.preview?.monthlyTotal ?? 0) }}
                </div>
                <div v-if="on('memberTotal')">认养人总数：{{ data.preview?.memberTotal }} 人</div>
              </div>
              <el-alert
                class="mt-10px"
                type="success"
                :closable="false"
                title="平台不触碰资金，仅收取 3% 技术服务费；款项用于种苗、人工、物流与基地维护。"
              />
              <div v-if="!anyOn" class="mt-8px text-12px text-[var(--el-text-color-secondary)]">
                当前没有开启任何公示字段，C 端不展示该模块。
              </div>
            </div>
          </div>
          <div class="mt-8px text-12px text-[var(--el-text-color-secondary)]">
            ↑ 即 C 端「合规说明」页内容，随右侧开关实时变化。收款主体取租户名称，保单编号取「资质证照」里的保单。
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="mb-12px">
          <template #header><span class="font-700">公示字段开关</span></template>
          <div v-for="f in fields" :key="f.key" class="flex items-center justify-between py-8px">
            <span class="text-13px">
              {{ f.label }}
              <span class="text-12px text-[var(--el-text-color-secondary)]">{{
                f.recommend ? '（建议公示）' : '（可选）'
              }}</span>
            </span>
            <el-switch v-model="f.enabled" inline-prompt active-text="公示" inactive-text="未公示" />
          </div>
          <el-alert
            class="mt-10px"
            type="success"
            :closable="false"
            show-icon
            title="建议至少开启前 5 项：这是把「我们不碰钱」变成可被验证的事实。"
          />
          <div v-if="data.updateTime" class="mt-8px text-12px text-[var(--el-text-color-secondary)]">
            上次保存：{{ fmtTime(data.updateTime, 'YYYY-MM-DD HH:mm:ss') }}
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="mb-12px">
          <template #header
            ><span class="font-700"
              >资金用途说明（近 30 天，可公示）
              <el-tag type="warning" size="small" effect="plain" class="ml-6px">占比为演示</el-tag></span
            ></template
          >
          <el-table :data="data.usage || []">
            <el-table-column label="用途" min-width="150">
              <template #default="{ row }">
                {{ row.name }}
                <el-tag v-if="!row.demo" size="small" type="success" effect="plain" class="ml-4px">真实</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="金额" width="120" align="right">
              <template #default="{ row }"><b>{{ formatMoney(row.amount) }}</b></template>
            </el-table-column>
            <el-table-column label="占比" width="80" align="right">
              <template #default="{ row }">{{ row.percent }}%</template>
            </el-table-column>
          </el-table>
          <div class="mt-8px text-12px text-[var(--el-text-color-secondary)]">
            合计 {{ formatMoney(data.usageTotal ?? 0) }}（= 近 30 天交易流水）。系统暂无成本台账，除平台服务费外，各项按原型占比换算。
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="mb-12px">
          <template #header><span class="font-700">为什么建议公示</span></template>
          <div class="text-13px mb-8px">
            认养=先付钱后收货，客户天然担心「钱去哪了」。这一页把敏感问题前置回答：
          </div>
          <div v-for="x in WHY" :key="x.k" class="flex py-6px text-13px">
            <span class="w-100px flex-shrink-0 text-[var(--el-text-color-secondary)]">{{ x.k }}</span>
            <span>{{ x.v }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
<script lang="ts" setup>
import * as ComplianceApi from '@/api/farm/compliance'
import { fmtTime, formatMoney } from '../constants'
import { WHY } from './constants'

defineOptions({ name: 'FarmComplianceFund' })

const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const data = ref<any>({})
const fields = ref<{ key: string; label: string; recommend: boolean; enabled: boolean }[]>([])
const on = (key: string) => !!fields.value.find((f) => f.key === key)?.enabled
const anyOn = computed(() => fields.value.some((f) => f.enabled))

const load = async () => {
  loading.value = true
  try {
    data.value = await ComplianceApi.getFundNotice()
    fields.value = (data.value.fields || []).map((f: any) => ({ ...f }))
  } finally {
    loading.value = false
  }
}
const save = async () => {
  saving.value = true
  try {
    await ComplianceApi.saveFundNotice(Object.fromEntries(fields.value.map((f) => [f.key, f.enabled])))
    message.success('公示设置已保存')
    load()
  } finally {
    saving.value = false
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
.preview {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  overflow: hidden;
}
.preview-hd {
  height: 44px;
  line-height: 44px;
  text-align: center;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #43a047, #2e7d32);
}
.step {
  flex: 1;
  text-align: center;
  font-size: 11px;
  padding: 9px 4px;
  border-radius: 8px;
  color: var(--el-color-primary-dark-2);
  background: var(--el-color-primary-light-9);
}
</style>
