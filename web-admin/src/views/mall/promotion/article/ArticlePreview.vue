<!-- farmer-saas 新增：文章预览（按手机阅读的样子展示：封面、标题、作者、时间、简介、正文） -->
<template>
  <Dialog v-model="visible" title="文章预览" width="460px">
    <div v-loading="loading" class="article-preview">
      <template v-if="article">
        <img v-if="article.picUrl" :src="article.picUrl" class="cover" alt="封面" />
        <h1 class="title">{{ article.title }}</h1>
        <div class="meta">
          <span>{{ article.author || '佚名' }}</span>
          <span v-if="categoryName">· {{ categoryName }}</span>
          <span v-if="article.createTime">· {{ formatDate(article.createTime, 'YYYY-MM-DD') }}</span>
          <el-tag v-if="article.status !== 0" size="small" type="info" class="ml-6px">未发布</el-tag>
        </div>
        <p v-if="article.introduction" class="intro">{{ article.introduction }}</p>
        <!-- 正文是后台富文本编辑器生成的 HTML，用 DOMPurify 过滤后再渲染 -->
        <div v-if="article.content" v-dompurify-html="article.content" class="content"></div>
        <el-empty v-else description="正文为空" :image-size="60" />
      </template>
    </div>
  </Dialog>
</template>

<script lang="ts" setup>
import { formatDate } from '@/utils/formatTime'
import * as ArticleApi from '@/api/mall/promotion/article'

defineOptions({ name: 'PromotionArticlePreview' })

const props = defineProps<{ categoryList: { id: number; name: string }[] }>()

const visible = ref(false)
const loading = ref(false)
const article = ref<ArticleApi.ArticleVO>()
const categoryName = computed(() => props.categoryList.find((c) => c.id === article.value?.categoryId)?.name ?? '')

/** 打开预览：按 id 取最新内容（列表里的数据可能不是最新的） */
const open = async (id: number) => {
  visible.value = true
  loading.value = true
  article.value = undefined
  try {
    article.value = await ArticleApi.getArticle(id)
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
</script>

<style lang="scss" scoped>
.article-preview {
  max-height: 70vh;
  min-height: 200px;
  padding: 4px 6px 12px;
  overflow-y: auto;

  .cover {
    width: 100%;
    max-height: 220px;
    margin-bottom: 12px;
    border-radius: 6px;
    object-fit: cover;
  }

  .title {
    margin: 0 0 8px;
    font-size: 20px;
    font-weight: 600;
    line-height: 1.4;
  }

  .meta {
    margin-bottom: 12px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .intro {
    padding: 8px 10px;
    margin: 0 0 12px;
    font-size: 13px;
    color: var(--el-text-color-regular);
    background: var(--el-fill-color-light);
    border-radius: 4px;
  }

  .content {
    font-size: 15px;
    line-height: 1.8;
    word-break: break-word;

    :deep(img) {
      max-width: 100%;
      height: auto;
    }

    :deep(p) {
      margin: 0 0 10px;
    }
  }
}
</style>
