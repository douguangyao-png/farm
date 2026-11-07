<template>
  <div class="flex items-start justify-between mb-12px">
    <div>
      <div class="text-18px font-600">收成录入</div>
      <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
        一次录入同时产生「生长档案 + 累计产出 + 配送」，认养人在小程序里能看到
      </div>
    </div>
    <div class="flex gap-8px">
      <el-button @click="scrollToHistory">历史录入</el-button>
      <el-button type="primary" :loading="formLoading" @click="submitForm">
        <Icon icon="ep:check" class="mr-5px" />保存并推送
      </el-button>
    </div>
  </div>

  <el-row :gutter="12">
    <el-col :xs="24" :lg="16">
      <ContentWrap>
        <div class="font-600 mb-12px">录入表单</div>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="90px"
          v-loading="formLoading"
        >
          <el-form-item label="录入人">
            <el-avatar :size="24" class="mr-8px">{{ userName.slice(0, 1) }}</el-avatar>
            <span>{{ userName }}（经营者后台录入）</span>
          </el-form-item>
          <el-form-item label="标的编号" prop="adoptionId">
            <el-select
              v-model="form.adoptionId"
              placeholder="选择地块 / 认养人"
              filterable
              class="!w-360px"
            >
              <el-option v-for="a in adoptions" :key="a.id" :label="a.label" :value="a.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="农事类型">
            <el-radio-group v-model="form.farmType">
              <el-radio-button v-for="t in FARM_TYPES" :key="t" :value="t">{{ t }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="作业说明">
            <el-input v-model="form.remark" placeholder="如：第二茬首采，随下次配送发出" />
          </el-form-item>
          <el-form-item label="收获明细" prop="items">
            <div class="w-full">
              <el-table :data="form.items" size="small" border>
                <el-table-column label="品类">
                  <template #default="{ row }">
                    <el-input v-model="row.crop" placeholder="如：番茄" />
                  </template>
                </el-table-column>
                <el-table-column label="重量（斤）" width="170" align="right">
                  <template #default="{ row }">
                    <el-input-number
                      v-model="row.weightJin"
                      :min="0"
                      :precision="1"
                      :step="0.5"
                      controls-position="right"
                      class="!w-140px"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="70" align="center">
                  <template #default="{ $index }">
                    <el-button
                      link
                      type="danger"
                      :disabled="form.items.length <= 1"
                      @click="form.items.splice($index, 1)"
                    >
                      删除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div class="flex items-center justify-between mt-8px">
                <el-button size="small" @click="form.items.push({ crop: '', weightJin: undefined })">
                  <Icon icon="ep:plus" class="mr-4px" />添加品类
                </el-button>
                <span class="text-13px">合计 {{ totalWeight }} 斤</span>
              </div>
            </div>
          </el-form-item>
          <el-form-item label="采收日期" prop="harvestDate">
            <el-date-picker
              v-model="form.harvestDate"
              type="date"
              value-format="x"
              placeholder="选择日期"
            />
          </el-form-item>
          <el-form-item label="现场照片">
            <div>
              <UploadImgs v-model="form.photos" :limit="9" height="72px" width="72px" />
              <div class="text-12px text-[var(--el-text-color-secondary)]">
                照片会同步到认养人的生长档案
              </div>
            </div>
          </el-form-item>
          <el-form-item label="环境快照">
            <div class="text-12px text-[var(--el-text-color-secondary)]">
              气温、湿度、土壤墒情由标的设备自动带出，不能手动修改（后台录入暂不带环境数据）
            </div>
          </el-form-item>
        </el-form>
      </ContentWrap>
    </el-col>

    <el-col :xs="24" :lg="8">
      <ContentWrap>
        <div class="font-600 mb-8px">保存后自动执行</div>
        <el-timeline>
          <el-timeline-item
            v-for="x in LINKAGES"
            :key="x.title"
            :type="x.implemented ? 'success' : 'info'"
          >
            <div class="font-600">
              {{ x.title }}
              <el-tag v-if="!x.implemented" size="small" type="info" effect="plain">演示</el-tag>
            </div>
            <div class="text-12px text-[var(--el-text-color-secondary)]">{{ x.desc }}</div>
          </el-timeline-item>
        </el-timeline>
      </ContentWrap>
      <ContentWrap>
        <div class="font-600 mb-8px">C 端展示预览</div>
        <div class="border border-solid border-[var(--el-border-color)] rounded-8px p-12px">
          <div class="font-600 mb-4px">认养人收到的小程序消息</div>
          <div class="text-13px leading-22px">{{ previewText }}</div>
          <div v-if="form.photos?.length" class="flex gap-6px mt-8px">
            <el-image
              v-for="(p, i) in form.photos.slice(0, 4)"
              :key="i"
              :src="p"
              fit="cover"
              class="w-48px h-48px rounded-4px"
            />
          </div>
        </div>
        <div class="text-12px text-[var(--el-text-color-secondary)] mt-8px">
          这正是 C 端「生长档案 / 收获配送」页展示的内容；小程序消息推送为演示，暂未接入。
        </div>
      </ContentWrap>
    </el-col>
  </el-row>

  <div ref="historyRef"></div>
  <ContentWrap>
    <div class="flex items-center justify-between mb-8px">
      <div class="font-600">历史录入</div>
      <div class="text-13px text-[var(--el-text-color-secondary)]">
        累计 {{ stats.totalCount ?? 0 }} 批 / {{ stats.totalWeightJin ?? 0 }} 斤，本月
        {{ stats.monthWeightJin ?? 0 }} 斤，待发货 {{ stats.unshippedCount ?? 0 }} 批 /
        {{ stats.unshippedWeightJin ?? 0 }} 斤
      </div>
    </div>
    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="地块编号">
        <el-input
          v-model="queryParams.assetCode"
          placeholder="如 A-01"
          clearable
          class="!w-160px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="发货">
        <el-select v-model="queryParams.shipped" placeholder="全部" clearable class="!w-120px">
          <el-option label="未发货" value="false" />
          <el-option label="已发货" value="true" />
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
      <el-table-column label="采收日期" width="120">
        <template #default="{ row }">{{ fmtDay(row.harvestDate) }}</template>
      </el-table-column>
      <el-table-column label="地块" prop="assetCode" width="90" />
      <el-table-column label="认养人" prop="memberName" width="110" />
      <el-table-column label="品类" prop="crop" min-width="140" />
      <el-table-column label="重量" width="100" align="right">
        <template #default="{ row }">{{ row.weightJin }} 斤</template>
      </el-table-column>
      <el-table-column label="照片" width="100">
        <template #default="{ row }">
          <el-image
            v-for="(p, i) in row.photos"
            :key="i"
            :src="p"
            :preview-src-list="row.photos"
            preview-teleported
            fit="cover"
            class="w-40px h-40px rounded-4px mr-4px"
          />
        </template>
      </el-table-column>
      <el-table-column label="备注 / 明细" prop="remark" min-width="200" show-overflow-tooltip />
      <el-table-column label="发货" width="140" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.shipmentId" type="success">已发货</el-tag>
          <el-button v-else link type="primary" @click="router.push('/farm-order/shipment')">
            去发货
          </el-button>
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
import type { FormInstance, FormRules } from 'element-plus'
import * as HarvestApi from '@/api/farm/harvest'
import { useUserStore } from '@/store/modules/user'
import { fmtDay } from '../constants'
import { FARM_TYPES, LINKAGES } from './constants'

defineOptions({ name: 'FarmHarvest' })

const message = useMessage()
const router = useRouter()
const userStore = useUserStore()
const userName = computed(() => userStore.getUser?.nickname || '经营者')

const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const stats = ref<any>({})
const queryParams = reactive<any>({
  pageNo: 1,
  pageSize: 10,
  assetCode: undefined,
  shipped: undefined
})

const getList = async () => {
  loading.value = true
  try {
    const res = await HarvestApi.getHarvestPage(queryParams)
    list.value = res.list
    total.value = res.total
    stats.value = res.stats ?? {}
  } finally {
    loading.value = false
  }
}
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}
const resetQuery = () => {
  queryParams.assetCode = undefined
  queryParams.shipped = undefined
  handleQuery()
}
const historyRef = ref<HTMLElement>()
const scrollToHistory = () => historyRef.value?.scrollIntoView({ behavior: 'smooth' })

// ---------- 录入表单 ----------
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const adoptions = ref<Awaited<ReturnType<typeof HarvestApi.getAdoptionSimpleList>>>([])
const today = () => new Date(new Date().toDateString()).getTime()
const emptyForm = () => ({
  adoptionId: undefined as number | undefined,
  farmType: '采摘采收',
  remark: '',
  items: [{ crop: '', weightJin: undefined as number | undefined }],
  harvestDate: today(),
  photos: [] as string[]
})
const form = ref<any>(emptyForm())
const validateItems = (_r: any, _v: any, cb: (e?: Error) => void) => {
  const items: { crop: string; weightJin?: number }[] = form.value.items
  if (items.some((x) => !x.crop?.trim())) return cb(new Error('请填写每一行的品类'))
  if (items.some((x) => !(Number(x.weightJin) > 0))) return cb(new Error('每一行的重量要大于 0'))
  cb()
}
const rules: FormRules = {
  adoptionId: [{ required: true, message: '请选择认养关系', trigger: 'change' }],
  items: [{ validator: validateItems, trigger: 'change' }],
  harvestDate: [{ required: true, message: '请选择采收日期', trigger: 'change' }]
}
const totalWeight = computed(
  () =>
    Math.round(
      (form.value.items as { weightJin?: number }[]).reduce(
        (s, x) => s + (Number(x.weightJin) || 0),
        0
      ) * 10
    ) / 10
)
const selectedAdoption = computed(() => adoptions.value.find((a) => a.id === form.value.adoptionId))
const previewText = computed(() => {
  const a = selectedAdoption.value
  const done = (form.value.items as { crop: string; weightJin?: number }[])
    .filter((x) => x.crop?.trim() && Number(x.weightJin) > 0)
    .map((x) => `${x.crop.trim()} ${x.weightJin} 斤`)
    .join('、')
  return `${a ? a.memberName : '认养人'}您好，${a ? a.assetCode : '您的标的'} ${form.value.farmType}完成${
    done ? `，共采收${done}` : ''
  }，将随下次配送发出。${form.value.remark ? `（${form.value.remark}）` : ''}`
})

const loadAdoptions = async () => {
  adoptions.value = await HarvestApi.getAdoptionSimpleList()
}
const submitForm = async () => {
  if (!(await formRef.value?.validate().catch(() => false))) return
  formLoading.value = true
  try {
    const f = form.value
    await HarvestApi.createHarvest({
      adoptionId: f.adoptionId,
      farmType: f.farmType,
      remark: f.remark?.trim() || undefined,
      items: f.items.map((x: any) => ({ crop: x.crop.trim(), weightJin: Number(x.weightJin) })),
      harvestDate: Number(f.harvestDate),
      photos: f.photos
    })
    message.success(`收成已录入，合计 ${totalWeight.value} 斤，已写入生长档案并累加累计产出`)
    form.value = emptyForm()
    nextTick(() => formRef.value?.clearValidate())
    queryParams.pageNo = 1
    getList()
  } finally {
    formLoading.value = false
  }
}

onMounted(() => {
  getList()
  loadAdoptions()
})
// keep-alive 切回来时刷新（从工作台待办跳过来、或在别的页面改过数据）
let mounted = false
onActivated(() => {
  if (mounted) {
    getList()
    loadAdoptions()
  }
  mounted = true
})
</script>
