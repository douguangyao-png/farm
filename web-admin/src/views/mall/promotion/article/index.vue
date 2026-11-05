<template>
  <doc-alert title="【营销】内容管理" url="https://doc.iocoder.cn/mall/promotion-content/" />

  <ContentWrap>
    <!-- 搜索工作栏 -->
    <el-form
      ref="queryFormRef"
      :inline="true"
      :model="queryParams"
      class="-mb-15px"
      label-width="80px"
    >
      <el-form-item label="文章分类" prop="categoryId">
        <el-select
          v-model="queryParams.categoryId"
          class="!w-240px"
          placeholder="全部"
          @keyup.enter="handleQuery"
        >
          <el-option
            v-for="item in categoryList"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="文章标题" prop="title">
        <el-input
          v-model="queryParams.title"
          class="!w-240px"
          clearable
          placeholder="请输入文章标题"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" class="!w-240px" clearable placeholder="请选择状态">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间" prop="createTime">
        <el-date-picker
          v-model="queryParams.createTime"
          :default-time="[new Date('1 00:00:00'), new Date('1 23:59:59')]"
          class="!w-240px"
          end-placeholder="结束日期"
          start-placeholder="开始日期"
          type="daterange"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery">
          <Icon class="mr-5px" icon="ep:search" />
          搜索
        </el-button>
        <el-button @click="resetQuery">
          <Icon class="mr-5px" icon="ep:refresh" />
          重置
        </el-button>
        <el-button
          v-hasPermi="['promotion:article:create']"
          plain
          type="primary"
          @click="openForm('create')"
        >
          <Icon class="mr-5px" icon="ep:plus" />
          新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list" :show-overflow-tooltip="true" :stripe="true">
      <el-table-column align="center" label="ID" min-width="180" prop="id" />
      <el-table-column align="center" label="封面" min-width="80" prop="picUrl">
        <template #default="{ row }">
          <el-image :src="row.picUrl" class="h-30px w-30px" @click="imagePreview(row.picUrl)" />
        </template>
      </el-table-column>
      <el-table-column align="center" label="标题" min-width="180" prop="title" />
      <el-table-column align="center" label="分类" min-width="180" prop="categoryId">
        <template #default="scope">
          {{ categoryList.find((item) => item.id === scope.row.categoryId)?.name }}
        </template>
      </el-table-column>
      <el-table-column align="center" label="浏览量" min-width="180" prop="browseCount" />
      <el-table-column align="center" label="作者" min-width="180" prop="author" />
      <el-table-column align="center" label="文章简介" min-width="250" prop="introduction" />
      <el-table-column align="center" label="排序" min-width="60" prop="sort" />
      <el-table-column align="center" label="发布状态" min-width="70" prop="status">
        <template #default="scope">
          <!-- farmer-saas: 文章用「已发布 / 未发布」，比字典的「开启 / 关闭」好懂 -->
          <el-tag :type="scope.row.status === 0 ? 'success' : 'info'">{{ scope.row.status === 0 ? '已发布' : '未发布' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        :formatter="dateFormatter"
        align="center"
        label="发布时间"
        prop="createTime"
        width="180px"
      />
      <el-table-column align="center" fixed="right" label="操作" width="220">
        <template #default="scope">
          <!-- farmer-saas: 预览、发布 / 撤回（status 0 = 已发布，1 = 未发布；发布后农场文章出现在 C 端「农场资讯」，平台文章出现在经营者工作台「平台公告」） -->
          <el-button link type="primary" @click="previewRef.open(scope.row.id)">预览</el-button>
          <el-button
            v-hasPermi="['promotion:article:update']"
            link
            :type="scope.row.status === 0 ? 'warning' : 'success'"
            @click="togglePublish(scope.row)"
          >
            {{ scope.row.status === 0 ? '撤回' : '发布' }}
          </el-button>
          <el-button
            v-hasPermi="['promotion:article:update']"
            link
            type="primary"
            @click="openForm('update', scope.row.id)"
          >
            编辑
          </el-button>
          <el-button
            v-hasPermi="['promotion:article:delete']"
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <!-- 分页 -->
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <!-- 表单弹窗：添加/修改 -->
  <ArticleForm ref="formRef" @success="getList" />
  <!-- farmer-saas: 文章预览 -->
  <ArticlePreview ref="previewRef" :category-list="categoryList" />
</template>

<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import * as ArticleApi from '@/api/mall/promotion/article'
import ArticleForm from './ArticleForm.vue'
import ArticlePreview from './ArticlePreview.vue'
import * as ArticleCategoryApi from '@/api/mall/promotion/articleCategory'
import * as ProductSpuApi from '@/api/mall/product/spu'
import { createImageViewer } from '@/components/ImageViewer'
import { getTenantId } from '@/utils/auth'

defineOptions({ name: 'PromotionArticle' })

const message = useMessage() // 消息弹窗
const { t } = useI18n() // 国际化

const loading = ref(true) // 列表的加载中
const total = ref(0) // 列表的总页数
const list = ref([]) // 列表的数据
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  categoryId: undefined,
  title: null,
  status: undefined,
  spuId: undefined,
  createTime: []
})
const queryFormRef = ref() // 搜索的表单
/** 文章封面预览 */
const imagePreview = (imgUrl: string) => {
  createImageViewer({
    urlList: [imgUrl]
  })
}
/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const data = await ArticleApi.getArticlePage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value.resetFields()
  handleQuery()
}

/** 添加/修改操作 */
const formRef = ref()
const openForm = (type: string, id?: number) => {
  formRef.value.open(type, id)
}

// farmer-saas: 预览、发布 / 撤回
const previewRef = ref()
const togglePublish = async (row: ArticleApi.ArticleVO) => {
  const publish = row.status !== 0
  try {
    await message.confirm(
      publish
        ? `发布「${row.title}」？发布后${isPlatformTenant() ? '所有经营者会在工作台「平台公告」里看到' : '会出现在 C 端首页「农场资讯」'}。`
        : `撤回「${row.title}」？撤回后外部就看不到了。`
    )
    const full = await ArticleApi.getArticle(row.id!)
    await ArticleApi.updateArticle({ ...full, status: publish ? 0 : 1 })
    message.success(publish ? '已发布' : '已撤回')
    await getList()
  } catch {}
}
const isPlatformTenant = () => Number(getTenantId()) === 1

/** 删除按钮操作 */
const handleDelete = async (id: number) => {
  try {
    // 删除的二次确认
    await message.delConfirm()
    // 发起删除
    await ArticleApi.deleteArticle(id)
    message.success(t('common.delSuccess'))
    // 刷新列表
    await getList()
  } catch {}
}

const categoryList = ref<ArticleCategoryApi.ArticleCategoryVO[]>([])
const spuList = ref<ProductSpuApi.Spu[]>([])
onMounted(async () => {
  await getList()
  // 加载分类、商品列表
  categoryList.value =
    (await ArticleCategoryApi.getSimpleArticleCategoryList()) as ArticleCategoryApi.ArticleCategoryVO[]
  spuList.value = (await ProductSpuApi.getSpuSimpleList()) as ProductSpuApi.Spu[]
})
</script>
