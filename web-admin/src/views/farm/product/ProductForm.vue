<template>
  <Dialog
    v-model="visible"
    :title="form.id ? '编辑认养商品' : '发布认养商品'"
    width="980px"
    scroll
    max-height="65vh"
  >
    <div v-loading="formLoading">
      <!-- R-2：违禁词拦截结果 -->
      <el-alert
        v-if="bannedHit"
        type="error"
        :closable="false"
        show-icon
        class="mb-12px"
        title="文案含有金融类违禁词，未能提交"
      >
        <div class="leading-24px">
          命中的词：
          <el-tag
            v-for="w in bannedHit.words"
            :key="w"
            type="danger"
            effect="dark"
            size="small"
            class="mr-4px"
          >
            {{ w }}
          </el-tag>
        </div>
        <div class="leading-22px">{{ bannedHit.suggestion }}</div>
        <div class="text-12px leading-20px">
          认养是实物交付，不能写成理财。标红的输入框里含有上面的词，请改写后再提交；这次拦截会计入「风险自检」的文案合规项。
        </div>
      </el-alert>
      <el-alert
        v-else-if="errorMsg"
        type="error"
        :closable="false"
        show-icon
        class="mb-12px"
        :title="errorMsg"
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="基地" prop="baseId">
              <el-select v-model="form.baseId" placeholder="请选择基地" class="!w-full">
                <el-option v-for="b in baseList" :key="b.id" :label="b.name" :value="b.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="资产类型">
              <el-radio-group v-model="form.assetType">
                <el-radio-button v-for="t in ASSET_TYPES" :key="t.value" :value="t.value">
                  {{ t.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="商品标题" prop="title" :error="hitError(form.title)">
          <el-input
            v-model="form.title"
            maxlength="40"
            show-word-limit
            placeholder="如：一分菜地 · 春季认养"
          />
        </el-form-item>
        <el-form-item label="商品描述" :error="hitError(form.description)">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="地块面积、轮作安排、可看的设备等"
          />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="认养周期">
              <el-input-number
                v-model="form.cycleDays"
                :min="1"
                controls-position="right"
                class="!w-120px"
              />
              <span class="ml-6px">天</span>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item v-if="form.assetType === 'plot'" label="面积">
              <el-input-number
                v-model="form.areaM2"
                :min="1"
                controls-position="right"
                class="!w-120px"
              />
              <span class="ml-6px">㎡</span>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库存">
              <el-input-number
                v-model="form.stock"
                :min="0"
                controls-position="right"
                class="!w-120px"
              />
              <span class="ml-6px">份</span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="上架状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">发布后上架</el-radio>
            <el-radio :value="1">先放下架</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-divider content-position="left">三档托管方式</el-divider>
        <el-tabs v-model="activeMode" type="border-card">
          <el-tab-pane v-for="m in form.careModes" :key="m.mode" :name="m.mode">
            <template #label>
              <span :class="{ 'text-[var(--el-color-danger)]': modeHasHit(m) }">
                {{ enumLabel(CARE_MODES, m.mode) }}
                <el-tag size="small" :type="m.enabled ? 'success' : 'info'" class="ml-4px">
                  {{ m.enabled ? formatMoney(m.price) : '未开放' }}
                </el-tag>
              </span>
            </template>
            <el-form-item label="开放这一档">
              <el-switch v-model="m.enabled" />
              <span class="ml-8px text-12px text-[var(--el-text-color-secondary)]">
                关闭后 C 端不展示这一档，至少开放一档
              </span>
            </el-form-item>
            <template v-if="m.enabled">
              <el-row :gutter="12">
                <el-col :span="12">
                  <el-form-item label="价格（元）">
                    <el-input-number
                      :model-value="m.price / 100"
                      :min="0"
                      :precision="2"
                      :step="100"
                      controls-position="right"
                      class="!w-180px"
                      @update:model-value="
                        (v: number | undefined) => (m.price = Math.round((v || 0) * 100))
                      "
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="合同版本">
                    <el-input v-model="m.contractVersion" placeholder="如 v3.2" class="!w-180px" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item label="一句话卖点" :error="hitError(m.slogan)">
                <el-input v-model="m.slogan" placeholder="如：农场全包，您只管收货" />
              </el-form-item>
              <el-form-item label="产出说明" :error="hitError(m.outputPromise)">
                <el-input
                  v-model="m.outputPromise"
                  placeholder="如：约定交付 240 斤，不足部分按同等品质补发"
                />
              </el-form-item>
              <el-row :gutter="12">
                <el-col :span="12">
                  <el-form-item label="每周待办">
                    <el-input-number
                      v-model="m.weeklyTodo"
                      :min="0"
                      :max="20"
                      controls-position="right"
                      class="!w-120px"
                    />
                    <span class="ml-6px text-12px text-[var(--el-text-color-secondary)]"
                      >项 / 周（认养人自己做）</span
                    >
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="保险说明">
                    <el-input v-model="m.insurance" placeholder="如：含农业保险" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item label="责任矩阵">
                <div class="w-full">
                  <div class="mb-6px text-12px text-[var(--el-text-color-secondary)]">
                    农场负责 {{ m.duty.filter((d) => d.owner === 'farm').length }} 项，认养人负责
                    {{
                      m.duty.filter((d) => d.owner === 'member').length
                    }}
                    项。会写进这一档的认养合同。
                  </div>
                  <el-table :data="m.duty" size="small" border>
                    <el-table-column label="#" type="index" width="50" align="center" />
                    <el-table-column label="农事" prop="item" min-width="160" />
                    <el-table-column label="谁负责" min-width="220">
                      <template #default="{ row }">
                        <el-radio-group v-model="row.owner" size="small">
                          <el-radio-button value="farm">农场</el-radio-button>
                          <el-radio-button value="member">认养人</el-radio-button>
                        </el-radio-group>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </el-form-item>
            </template>
          </el-tab-pane>
        </el-tabs>
      </el-form>
    </div>
    <template #footer>
      <span v-if="bannedHit" class="mr-12px text-13px text-[var(--el-color-danger)]">
        已拦截：命中「{{ bannedHit.words.join('、') }}」，请改写后再提交
      </span>
      <el-button type="primary" :loading="formLoading" @click="submitForm">
        {{ form.id ? '保 存' : '发 布' }}
      </el-button>
      <el-button @click="visible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus'
import * as ProductApi from '@/api/farm/product'
import * as BaseApi from '@/api/farm/base'
import { ASSET_TYPES, CARE_MODES, DUTY_ITEMS, enumLabel, formatMoney } from '../constants'

defineOptions({ name: 'FarmProductForm' })

const emit = defineEmits(['success'])
const message = useMessage()

const visible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const baseList = ref<{ id: number; name: string }[]>([])
const activeMode = ref('full')
const bannedHit = ref<{ words: string[]; suggestion: string } | null>(null)
const errorMsg = ref('')

/** 没有可参考的商品时用的默认三档（数字与种子商品 1 一致，见 docs/demo/演示数据说明.md） */
const defaultModes = (): ProductApi.CareModeConfig[] => {
  const farmOnly = [
    '整地',
    '播种 / 育苗',
    '病虫害统防',
    '日常巡田',
    '采收',
    '分拣包装',
    '配送（12 次）',
    '监控与环境数据',
    '技术指导'
  ]
  const selfFarm = ['分拣包装', '配送（12 次）', '监控与环境数据', '技术指导']
  return [
    {
      mode: 'full',
      enabled: true,
      price: 128000,
      slogan: '农场全包，您只管收货',
      outputPromise: '约定交付 240 斤，不足部分按同等品质补发',
      contractVersion: 'v3.2',
      weeklyTodo: 0,
      insurance: '含农业保险',
      duty: DUTY_ITEMS.map((item) => ({ item, owner: 'farm' as const }))
    },
    {
      mode: 'semi',
      enabled: true,
      price: 86000,
      slogan: '关键农事管家做，日常您来',
      outputPromise: '按实际收成交付；整地、播种、统防、采收由农场负责',
      contractVersion: 'v2.1',
      weeklyTodo: 2,
      insurance: '统防部分含保险',
      duty: DUTY_ITEMS.map((item) => ({
        item,
        owner: farmOnly.includes(item) ? ('farm' as const) : ('member' as const)
      }))
    },
    {
      mode: 'self',
      enabled: true,
      price: 50000,
      slogan: '全程自己种，农场提供地和技术',
      outputPromise: '按实际收成交付，产量随自己的管理浮动',
      contractVersion: 'v1.4',
      weeklyTodo: 5,
      insurance: '建议自购农业保险',
      duty: DUTY_ITEMS.map((item) => ({
        item,
        owner: selfFarm.includes(item) ? ('farm' as const) : ('member' as const)
      }))
    }
  ]
}

const emptyForm = (): ProductApi.ProductVO => ({
  baseId: baseList.value[0]?.id,
  assetType: 'plot',
  title: '',
  cover: '',
  description: '',
  cycleDays: 365,
  areaM2: 66,
  stock: 10,
  status: 0,
  careModes: defaultModes()
})
const form = ref<ProductApi.ProductVO>(emptyForm())
const rules: FormRules = {
  baseId: [{ required: true, message: '请选择基地', trigger: 'change' }],
  title: [{ required: true, message: '标题不能为空', trigger: 'blur' }]
}

const clone = <T,>(v: T): T => JSON.parse(JSON.stringify(v))

/** 输入框里含有被拦截的词时标红 */
const hitError = (text?: string) => {
  const words = bannedHit.value?.words.filter((w) => text?.includes(w)) ?? []
  return words.length ? `含有违禁词：${words.join('、')}` : ''
}
const modeHasHit = (m: ProductApi.CareModeConfig) =>
  !!(hitError(m.slogan) || hitError(m.outputPromise))

const open = async (id?: number) => {
  visible.value = true
  bannedHit.value = null
  errorMsg.value = ''
  activeMode.value = 'full'
  formLoading.value = true
  try {
    if (!baseList.value.length) baseList.value = await BaseApi.getBaseList()
    if (id) {
      const p = await ProductApi.getProduct(id)
      form.value = clone({
        id: p.id,
        baseId: p.baseId,
        assetType: p.assetType,
        title: p.title,
        cover: p.cover,
        description: p.description,
        cycleDays: p.cycleDays,
        areaM2: p.areaM2,
        stock: p.stock,
        status: p.status,
        careModes: p.careModes
      })
    } else {
      // 新建：参照商品 1（「一分菜地 · 年度认养」）预填三档和描述，演示时只需改标题
      const f = emptyForm()
      const res = await ProductApi.getProductPage({ pageNo: 1, pageSize: 100 })
      const tpl: ProductApi.ProductVO | undefined =
        res.list.find((x: ProductApi.ProductVO) => x.id === 1) ?? res.list[res.list.length - 1]
      if (tpl) {
        Object.assign(f, {
          baseId: tpl.baseId,
          assetType: tpl.assetType,
          cover: tpl.cover,
          description: tpl.description,
          cycleDays: tpl.cycleDays,
          areaM2: tpl.areaM2,
          careModes: clone(tpl.careModes)
        })
      }
      form.value = f
    }
  } finally {
    formLoading.value = false
  }
  nextTick(() => formRef.value?.clearValidate())
}
defineExpose({ open })

const submitForm = async () => {
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!form.value.careModes.some((m) => m.enabled)) {
    errorMsg.value = '至少开放一种托管方式'
    bannedHit.value = null
    return
  }
  formLoading.value = true
  try {
    if (form.value.id) {
      await ProductApi.updateProduct(form.value)
      message.success('已保存')
    } else {
      await ProductApi.createProduct(form.value)
      message.success('认养商品已发布')
    }
    bannedHit.value = null
    errorMsg.value = ''
    visible.value = false
    emit('success')
  } catch (e: any) {
    if (e?.code === ProductApi.BANNED_WORDS_CODE && e.data) {
      bannedHit.value = { words: e.data.words || [], suggestion: e.data.suggestion || '' }
      errorMsg.value = ''
      // 命中的词在某一档的文案里时，切到那一档
      const m = form.value.careModes.find((x) => x.enabled && modeHasHit(x))
      if (m && !hitError(form.value.title) && !hitError(form.value.description))
        activeMode.value = m.mode
      message.error('文案含有违禁词：' + bannedHit.value.words.join('、'))
    } else {
      bannedHit.value = null
      errorMsg.value = e?.msg || (typeof e === 'string' ? e : e?.message) || '提交失败'
      message.error(errorMsg.value)
    }
  } finally {
    formLoading.value = false
  }
}
</script>
