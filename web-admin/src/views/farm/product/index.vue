<template>
  <div class="mb-12px">
    <div class="text-18px font-700">认养商品</div>
    <div class="text-13px text-[var(--el-text-color-secondary)]">
      共 {{ total }} 个认养项目；每个商品对应一类可核验的实体标的（地块 / 畜禽 / 果树）
    </div>
  </div>

  <ContentWrap>
    <el-tabs v-model="tab" class="-mt-8px" @tab-change="handleTab">
      <el-tab-pane v-for="t in PRODUCT_TABS" :key="t.name" :label="t.label" :name="t.name" />
    </el-tabs>
    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="商品名称">
        <el-input
          v-model="queryParams.title"
          placeholder="请输入商品名称"
          clearable
          class="!w-200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="认养分类">
        <el-select v-model="queryParams.assetType" placeholder="全部" clearable class="!w-130px">
          <el-option
            v-for="c in PRODUCT_CATEGORIES"
            :key="c.value"
            :label="c.label"
            :value="c.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="价格区间">
        <el-input-number
          v-model="priceMinYuan"
          :min="0"
          :controls="false"
          placeholder="最低"
          class="!w-100px"
        />
        <span class="mx-6px">-</span>
        <el-input-number
          v-model="priceMaxYuan"
          :min="0"
          :controls="false"
          placeholder="最高"
          class="!w-100px"
        />
        <span class="ml-6px text-12px text-[var(--el-text-color-secondary)]">元（全托管价）</span>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleQuery"
          ><Icon icon="ep:search" class="mr-5px" />查询</el-button
        >
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button type="primary" plain @click="formRef?.open()">
          <Icon icon="ep:plus" class="mr-5px" />发布认养商品
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="编号" prop="no" width="80" />
      <el-table-column label="商品名称" min-width="280">
        <template #default="{ row }">
          <div class="flex items-center">
            <el-image
              v-if="row.cover"
              :src="row.cover"
              fit="cover"
              class="w-40px h-40px rounded-4px mr-10px flex-shrink-0"
            />
            <span v-else class="icon-box">{{ CATEGORY_ICON[row.assetType] || '🌱' }}</span>
            <div>
              <div class="font-600">{{ row.title }}</div>
              <div class="text-12px text-[var(--el-text-color-secondary)]">
                预计产出 {{ row.expectedOutput || '-' }} · {{ cycleText(row.cycleDays) }}
                <template v-if="row.baseName"> · {{ row.baseName }}</template>
              </div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="分类" prop="categoryName" width="100" />
      <el-table-column label="价格" width="130" align="right">
        <template #default="{ row }">
          <el-tooltip placement="top">
            <template #content>
              <div v-for="m in row.careModes" :key="m.mode">
                {{ enumLabel(CARE_MODES, m.mode) }}：{{ m.enabled ? formatMoney(m.price) : '未开放' }}
              </div>
            </template>
            <span class="font-700">{{ formatMoney(row.minPrice) }} 起</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="认养进度" width="170">
        <template #default="{ row }">
          <el-progress
            :percentage="row.soldRate"
            :show-text="false"
            :status="row.soldRate > 90 ? 'exception' : row.soldRate > 70 ? 'warning' : undefined"
          />
          <div class="text-12px text-[var(--el-text-color-secondary)]">
            {{ row.sold }} / {{ row.totalSlots }}（{{ row.soldRate }}%）
          </div>
        </template>
      </el-table-column>
      <el-table-column label="已售" prop="sold" width="70" align="right" />
      <el-table-column label="保险" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.insured ? 'success' : 'danger'">
            {{ row.insured ? '已投保' : '未投保' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="enumOf(PRODUCT_SALE_STATUS, row.status).type">
            {{ enumLabel(PRODUCT_SALE_STATUS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="formRef?.open(row.id)">编辑</el-button>
          <el-button link type="primary" @click="openPreview(row)">预览</el-button>
          <el-button
            link
            :type="row.status === 0 ? 'warning' : 'success'"
            @click="toggleStatus(row)"
          >
            {{ row.status === 0 ? '下架' : '上架' }}
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
    <div class="mt-12px text-12px text-[var(--el-text-color-secondary)]">
      <Icon icon="ep:warning" class="mr-4px" />
      商品一旦产生订单，价格与产出承诺将被合同锁定，修改不会影响已签署合同的历史订单。
    </div>
  </ContentWrap>

  <Dialog v-model="previewVisible" :title="preview?.title || '商品预览'" width="560px">
    <div v-if="preview">
      <el-image
        v-if="preview.cover"
        :src="preview.cover"
        fit="cover"
        class="w-full h-180px rounded-4px mb-10px"
      />
      <div class="text-13px mb-8px leading-20px">{{ preview.description }}</div>
      <div class="text-12px mb-8px text-[var(--el-text-color-secondary)]">
        {{ preview.categoryName }} · {{ preview.baseName }} · 周期 {{ cycleText(preview.cycleDays) }}
        · 已认养 {{ preview.sold }} / {{ preview.totalSlots }}
      </div>
      <div v-for="m in preview.careModes" :key="m.mode" class="mb-8px">
        <el-tag
          size="small"
          :type="m.enabled ? enumOf(CARE_MODES, m.mode).type : 'info'"
          effect="plain"
          class="mr-6px"
        >
          {{ enumLabel(CARE_MODES, m.mode) }}
        </el-tag>
        <template v-if="m.enabled">
          <b>{{ formatMoney(m.price) }}</b>
          <span class="ml-8px text-12px">{{ m.outputPromise }}</span>
        </template>
        <span v-else class="text-[var(--el-text-color-placeholder)]">未开放</span>
      </div>
    </div>
  </Dialog>

  <ProductForm ref="formRef" @success="getList" />
</template>
<script lang="ts" setup>
import * as ProductApi from '@/api/farm/product'
import ProductForm from './ProductForm.vue'
import { CARE_MODES, enumLabel, enumOf, formatMoney } from '../constants'
import {
  CATEGORY_ICON,
  PRODUCT_CATEGORIES,
  PRODUCT_SALE_STATUS,
  PRODUCT_TABS,
  cycleText
} from './constants'

defineOptions({ name: 'FarmProduct' })

const message = useMessage()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const tab = ref('all')
const queryParams = reactive<any>({
  pageNo: 1,
  pageSize: 10,
  title: undefined,
  status: undefined,
  assetType: undefined,
  priceMin: undefined,
  priceMax: undefined
})
// 价格区间用元输入，传给接口时换成分
const priceMinYuan = ref<number | undefined>()
const priceMaxYuan = ref<number | undefined>()
const formRef = ref<InstanceType<typeof ProductForm>>()

const getList = async () => {
  loading.value = true
  try {
    queryParams.status = tab.value === 'all' ? undefined : Number(tab.value)
    queryParams.priceMin = priceMinYuan.value == null ? undefined : Math.round(priceMinYuan.value * 100)
    queryParams.priceMax = priceMaxYuan.value == null ? undefined : Math.round(priceMaxYuan.value * 100)
    const res = await ProductApi.getProductPage(queryParams)
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
const handleTab = () => handleQuery()
const resetQuery = () => {
  queryParams.title = undefined
  queryParams.assetType = undefined
  priceMinYuan.value = undefined
  priceMaxYuan.value = undefined
  handleQuery()
}

const toggleStatus = async (row: any) => {
  const next = row.status === 0 ? 1 : 0
  try {
    await message.confirm(`确定${next === 0 ? '上架' : '下架'}「${row.title}」吗？`)
  } catch {
    return
  }
  await ProductApi.updateProductStatus(row.id!, next)
  message.success(next === 0 ? '已上架' : '已下架')
  getList()
}

const previewVisible = ref(false)
const preview = ref<any>()
const openPreview = (row: any) => {
  preview.value = row
  previewVisible.value = true
}

onMounted(getList)
</script>
<style scoped>
.icon-box {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 40px;
  width: 40px;
  height: 40px;
  margin-right: 10px;
  font-size: 20px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}
</style>
