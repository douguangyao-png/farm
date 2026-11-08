<!-- 权限串：farm:hostingconfig:query / farm:hostingconfig:update。三档价格、产出承诺、合同版本、责任矩阵与认养商品是同一份 careModes，保存走 PUT /farm/product/update，不另存一份 -->
<template>
  <div v-loading="loading">
    <!-- 平台管理员（平台运营中心租户）没有认养商品，不报错，提示切到经营者账号 -->
    <el-empty
      v-if="empty"
      description="当前租户还没有认养商品，没有可配置的托管方式。请用经营者账号（如 qingshan）查看，或先发布认养商品。"
    />
    <template v-else>
    <div class="flex items-start justify-between mb-12px">
      <div>
        <div class="text-18px font-600">托管模式配置</div>
        <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
          一个认养商品可同时开放三档托管方式：参与越多、认养费越低，责任边界同步收紧，全部写入对应版本的电子合同
        </div>
      </div>
      <div class="flex gap-8px items-center">
        <el-select v-model="productId" class="!w-260px" @change="load">
          <el-option v-for="p in data.products || []" :key="p.id" :label="p.title" :value="p.id" />
        </el-select>
        <el-button v-hasPermi="['farm:hostingconfig:query']" @click="message.info('演示版暂未开放')">查看 C 端对比页</el-button>
        <el-button v-hasPermi="['farm:hostingconfig:update']" type="primary" :loading="saving" @click="save">保存配置</el-button>
      </div>
    </div>

    <el-alert
      v-if="banned"
      class="mb-12px"
      type="error"
      show-icon
      :closable="false"
      :title="`文案含有违禁词：${banned.words.join('、')}`"
      :description="banned.suggestion"
    />

    <el-tabs v-model="activeMode" type="card">
      <el-tab-pane v-for="m in modes" :key="m.mode" :name="m.mode">
        <template #label>
          {{ m.name }}
          <el-tag v-if="!m.enabled" size="small" type="info" class="ml-4px">未开放</el-tag>
        </template>
      </el-tab-pane>
    </el-tabs>

    <el-row :gutter="12" v-if="cur">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="mb-12px">
          <template #header
            ><span class="font-700">定价与服务范围（{{ data.product?.title }}）</span></template
          >
          <el-alert
            v-if="!cur.configured"
            type="warning"
            :closable="false"
            show-icon
            class="mb-10px"
            title="该商品没有配置这一档。到「认养商品」里编辑商品，开放此档后再来调整。"
          />
          <el-form label-width="130px" :disabled="!cur.configured">
            <el-form-item label="开放该模式">
              <el-switch v-model="cur.enabled" />
              <span class="ml-8px text-12px text-[var(--el-text-color-secondary)]">
                至少保留一档开放
              </span>
            </el-form-item>
            <el-form-item label="认养费（元）">
              <el-input-number
                :model-value="cur.price / 100"
                :min="0"
                :step="10"
                :precision="2"
                @update:model-value="(v: any) => (cur.price = Math.round((v || 0) * 100))"
              />
              <span class="ml-8px text-12px text-[var(--el-text-color-secondary)]">
                {{ priceNote(cur) }}
              </span>
            </el-form-item>
            <el-form-item label="一句话口号">
              <el-input v-model="cur.slogan" />
            </el-form-item>
            <el-form-item label="产出承诺">
              <el-input v-model="cur.outputPromise" type="textarea" :rows="2" />
              <div class="hint">按实物交付表述，例如「约定交付 240 斤，不足部分按同等品质补发」；不得写成收益承诺</div>
            </el-form-item>
            <el-form-item label="责任边界">
              <span class="text-13px"
                >农场负责 {{ dutyCount(cur, 'farm') }} 项，认养人负责
                {{ dutyCount(cur, 'member') }} 项（由右侧责任矩阵决定）</span
              >
            </el-form-item>
            <el-form-item label="农业保险">
              <el-input v-model="cur.insurance" />
            </el-form-item>
            <el-form-item label="合同模板">
              <el-input v-model="cur.contractVersion" class="!w-120px" />
              <span class="ml-8px text-13px text-[var(--el-text-color-secondary)]">{{
                `《认养服务合同（${cur.name}）》${cur.contractVersion}`
              }}</span>
            </el-form-item>
            <el-form-item label="每周待办（C 端）">
              <el-input-number v-model="cur.weeklyTodo" :min="0" :max="30" />
              <span class="ml-8px text-12px text-[var(--el-text-color-secondary)]">项</span>
            </el-form-item>
            <el-form-item label="模式切换">
              <el-tag effect="plain">每周期可切换 1 次</el-tag>
              <div class="hint">{{ data.switchRule }}</div>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="mb-12px">
          <template #header><span class="font-700">农事责任矩阵（决定任务派给谁）</span></template>
          <el-table :data="matrix" size="small">
            <el-table-column label="农事环节" prop="item" min-width="130" />
            <el-table-column v-for="m in modes" :key="m.mode" :label="m.name" width="110" align="center">
              <template #default="{ row }">
                <el-tag
                  v-if="row[m.mode]"
                  :type="row[m.mode] === 'farm' ? 'success' : 'info'"
                  effect="plain"
                  :class="m.configured ? 'cursor-pointer' : ''"
                  @click="toggleDuty(m, row.item)"
                  >{{ row[m.mode] === 'farm' ? '农场' : '认养人' }}</el-tag
                >
                <span v-else class="text-[var(--el-text-color-placeholder)]">-</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="hint mt-8px">
            点击标签可切换执行方。保存后，标记「农场」的进管家 App 任务池，标记「认养人」的推送到 C 端自管任务列表。
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="mb-12px">
          <template #header
            ><span class="font-700">默认农资包（C 端下单页推荐）</span></template
          >
          <el-table :data="cur.supplyPackage" size="small" empty-text="全托管已含全部投入品，无需加购">
            <el-table-column label="农资 / 服务" prop="name" min-width="140" />
            <el-table-column label="售价" width="90" align="right">
              <template #default="{ row }"
                ><b>{{ formatMoney(row.price) }}</b></template
              >
            </el-table-column>
            <el-table-column label="推荐" width="70" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.recommended ? 'success' : 'info'" effect="plain">{{
                  row.recommended ? '推荐' : '可选'
                }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div class="hint mt-8px">
            取自「农资与服务商品」里适用该模式的上架商品；增减请到农资商品页调整。
          </div>
        </el-card>

        <el-card shadow="never" class="mb-12px">
          <template #header><span class="font-700">任务触发规则</span></template>
          <div class="kv"><span class="k">任务来源</span><span><el-tag v-for="s in trig.sources" :key="s" effect="plain" class="mr-4px">{{ s }}</el-tag></span></div>
          <div class="kv"><span class="k">触发示例</span><span>{{ trig.example }}</span></div>
          <div class="kv"><span class="k">提醒节奏</span><span>{{ trig.remind }}</span></div>
          <div class="kv"><span class="k">逾期兜底</span><span>{{ trig.overdue }}</span></div>
          <el-alert
            class="mt-10px"
            type="success"
            :closable="false"
            show-icon
            :title="`逾期未处理的自管任务会转为「可付费委托」。近 30 天由此完成 ${trig.overdueCount ?? 0} 单代管工单，收入 ${formatMoney(trig.overdueIncome ?? 0)}。`"
          />
        </el-card>

        <el-card shadow="never" class="mb-12px">
          <template #header><span class="font-700">合规要点（系统强制）</span></template>
          <div v-for="r in data.compliance?.rules || []" :key="r.k" class="kv">
            <span class="k">{{ r.k }}</span>
            <span>
              <el-tag size="small" :type="r.tag === '已拦截' ? 'danger' : 'warning'" effect="plain" class="mr-6px">{{ r.tag }}</el-tag>{{ r.v }}
            </span>
          </div>
          <div class="hint mt-6px">当前有 {{ data.compliance?.pesticideBlocked ?? 0 }} 个农药类商品被禁止零售。</div>
        </el-card>
      </el-col>
    </el-row>

    <el-alert
      type="success"
      :closable="false"
      show-icon
      title="三档配置共用同一份地块资产池，切换模式不会改变地块编号与摄像头绑定，认养人历史数据与生长档案连续保留。"
    />
    </template>
  </div>
</template>
<script lang="ts" setup>
import * as HostingApi from '@/api/farm/hosting'
import * as ProductApi from '@/api/farm/product'
import { formatMoney } from '../constants'

defineOptions({ name: 'FarmHostingConfig' })

const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const data = ref<any>({})
const empty = ref(false)
const productId = ref<number>()
const activeMode = ref('semi')
const modes = ref<any[]>([])
const banned = ref<{ words: string[]; suggestion: string } | null>(null)

const cur = computed(() => modes.value.find((m) => m.mode === activeMode.value))
const trig = computed(() => data.value.trigger ?? { sources: [] })
const dutyCount = (m: any, owner: string) => m.duty.filter((d: any) => d.owner === owner).length

const priceNote = (m: any) => {
  if (m.mode === 'full') return '基准价'
  const full = modes.value.find((x) => x.mode === 'full')
  if (!full?.configured) return ''
  const delta = m.price - full.price
  return `较全托管 ${delta >= 0 ? '+' : '-'}${formatMoney(Math.abs(delta))}`
}

/** 矩阵：环节 × 三档，值取当前（可能已被修改的）modes */
const matrix = computed(() => {
  const items: string[] = []
  for (const m of modes.value) for (const d of m.duty) if (!items.includes(d.item)) items.push(d.item)
  return items.map((item) => ({
    item,
    ...Object.fromEntries(
      modes.value.map((m) => [m.mode, m.duty.find((d: any) => d.item === item)?.owner ?? null])
    )
  }))
})
const toggleDuty = (m: any, item: string) => {
  if (!m.configured) return
  const d = m.duty.find((x: any) => x.item === item)
  if (d) d.owner = d.owner === 'farm' ? 'member' : 'farm'
}

const load = async () => {
  loading.value = true
  banned.value = null
  try {
    data.value = await HostingApi.getHostingConfig(productId.value)
    empty.value = !!data.value.empty
    if (empty.value) {
      modes.value = []
      return
    }
    productId.value = data.value.product.id
    // 深拷贝一份给表单编辑，保存前不影响服务端数据
    modes.value = JSON.parse(JSON.stringify(data.value.modes))
    if (!modes.value.find((m) => m.mode === activeMode.value)) activeMode.value = 'full'
  } finally {
    loading.value = false
  }
}

const save = async () => {
  const configured = modes.value.filter((m) => m.configured)
  if (!configured.some((m) => m.enabled)) return message.error('至少开放一种托管方式')
  saving.value = true
  banned.value = null
  try {
    await ProductApi.updateProduct({
      id: productId.value,
      careModes: configured.map((m) => ({
        mode: m.mode,
        enabled: m.enabled,
        price: m.price,
        slogan: m.slogan,
        outputPromise: m.outputPromise,
        contractVersion: m.contractVersion,
        weeklyTodo: m.weeklyTodo,
        insurance: m.insurance,
        duty: m.duty
      }))
    } as any)
    message.success('托管模式配置已保存')
    load()
  } catch (e: any) {
    if (e?.code === ProductApi.BANNED_WORDS_CODE && e.data) {
      banned.value = { words: e.data.words || [], suggestion: e.data.suggestion || '' }
      message.error('文案含有违禁词：' + banned.value.words.join('、'))
    } else {
      message.error(e?.msg || (typeof e === 'string' ? e : e?.message) || '保存失败')
    }
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
.hint {
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}
.kv {
  display: flex;
  padding: 6px 0;
  font-size: 13px;
}
.kv .k {
  width: 80px;
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
}
</style>
