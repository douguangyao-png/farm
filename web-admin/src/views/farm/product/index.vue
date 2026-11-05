<template>
  <ContentWrap>
    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="商品标题">
        <el-input
          v-model="queryParams.title"
          placeholder="请输入标题"
          clearable
          class="!w-220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部" clearable class="!w-120px">
          <el-option v-for="s in PRODUCT_STATUS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button type="primary" plain @click="formRef?.open()">
          <Icon icon="ep:plus" class="mr-5px" />发布认养商品
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="商品" min-width="260">
        <template #default="{ row }">
          <div class="flex items-center">
            <el-image
              v-if="row.cover"
              :src="row.cover"
              fit="cover"
              class="w-56px h-56px rounded-4px mr-10px flex-shrink-0"
            />
            <div>
              <div class="font-600">{{ row.title }}</div>
              <div class="text-12px text-[var(--el-text-color-secondary)]">
                {{ row.baseName }} · {{ enumLabel(ASSET_TYPES, row.assetType) }}
                <template v-if="row.areaM2"> · {{ row.areaM2 }}㎡</template>
                · {{ row.cycleDays }} 天
              </div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="三档托管价" min-width="260">
        <template #default="{ row }">
          <div v-for="m in row.careModes" :key="m.mode" class="text-13px leading-22px">
            <el-tag
              size="small"
              :type="m.enabled ? enumOf(CARE_MODES, m.mode).type : 'info'"
              effect="plain"
              class="mr-6px"
            >
              {{ enumLabel(CARE_MODES, m.mode) }}
            </el-tag>
            <span v-if="m.enabled">{{ formatMoney(m.price) }}</span>
            <span v-else class="text-[var(--el-text-color-placeholder)]">未开放</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="库存 / 已售" width="110" align="center">
        <template #default="{ row }">{{ row.stock }} / {{ row.sold }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="enumOf(PRODUCT_STATUS, row.status).type">
            {{ enumLabel(PRODUCT_STATUS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="160" :formatter="timeFormatter" />
      <el-table-column label="操作" width="140" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="formRef?.open(row.id)">编辑</el-button>
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
  </ContentWrap>

  <ProductForm ref="formRef" @success="getList" />
</template>
<script lang="ts" setup>
import * as ProductApi from '@/api/farm/product'
import ProductForm from './ProductForm.vue'
import {
  ASSET_TYPES,
  CARE_MODES,
  PRODUCT_STATUS,
  enumLabel,
  enumOf,
  formatMoney,
  timeFormatter
} from '../constants'

defineOptions({ name: 'FarmProduct' })

const message = useMessage()
const loading = ref(false)
const list = ref<ProductApi.ProductVO[]>([])
const total = ref(0)
const queryParams = reactive<any>({ pageNo: 1, pageSize: 10, title: undefined, status: undefined })
const formRef = ref<InstanceType<typeof ProductForm>>()

const getList = async () => {
  loading.value = true
  try {
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
const resetQuery = () => {
  queryParams.title = undefined
  queryParams.status = undefined
  handleQuery()
}

const toggleStatus = async (row: ProductApi.ProductVO) => {
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

onMounted(getList)
</script>
