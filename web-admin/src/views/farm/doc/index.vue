<template>
  <div class="farm-doc">
    <!-- 左：按分类列出 docs/ 下的文档 -->
    <el-card shadow="never" class="farm-doc-side" body-class="farm-doc-side-body">
      <el-input v-model="keyword" clearable placeholder="搜索标题或文件名" class="mb-8px">
        <template #prefix><Icon icon="ep:search" /></template>
      </el-input>
      <div v-loading="listLoading" class="farm-doc-list">
        <div v-for="g in groups" :key="g.category" class="farm-doc-group">
          <div class="farm-doc-group-h"
            >{{ g.category }}<span>{{ g.items.length }}</span></div
          >
          <div
            v-for="d in g.items"
            :key="d.path"
            class="farm-doc-item"
            :class="{ active: d.path === currentPath }"
            :title="d.path"
            @click="openDoc(d.path)"
          >
            <Icon
              :icon="d.type === 'html' ? 'ep:monitor' : 'ep:document'"
              :size="14"
              class="mr-4px flex-shrink-0"
            />
            <span class="truncate">{{ d.title }}</span>
          </div>
        </div>
        <el-empty v-if="!listLoading && !groups.length" description="没有文档" :image-size="60" />
      </div>
    </el-card>

    <!-- 右：内容 -->
    <el-card shadow="never" class="farm-doc-main" body-class="farm-doc-main-body">
      <template v-if="doc">
        <div class="farm-doc-head">
          <div class="min-w-0">
            <div class="text-16px font-600 truncate">{{ doc.title }}</div>
            <div class="mt-2px text-12px text-[var(--el-text-color-secondary)]">
              {{ doc.category }} · docs/{{ doc.path }} · {{ (doc.size / 1024).toFixed(1) }} KB ·
              更新于 {{ formatDate(doc.updateTime) }}
            </div>
          </div>
        </div>
        <div v-loading="docLoading" class="farm-doc-body">
          <!-- HTML 原型：沙箱 iframe，只允许脚本，不和后台同源 -->
          <iframe
            v-if="doc.type === 'html'"
            :key="doc.path"
            class="farm-doc-iframe"
            sandbox="allow-scripts"
            :srcdoc="doc.content"
            :title="doc.title"
          ></iframe>
          <div v-else class="farm-doc-md-wrap">
            <div ref="mdRef" class="farm-doc-md" @click="onMdClick" v-html="html"></div>
            <div v-if="toc.length > 2" class="farm-doc-toc">
              <div class="farm-doc-toc-h">目录</div>
              <div
                v-for="t in toc"
                :key="t.id"
                class="farm-doc-toc-item"
                :style="{ paddingLeft: (t.level - minLevel) * 12 + 'px' }"
                :title="t.text"
                @click="scrollTo(t.id)"
              >
                {{ t.text }}
              </div>
            </div>
          </div>
        </div>
      </template>
      <el-empty v-else description="从左侧选一份文档" />
    </el-card>
  </div>
</template>

<script lang="ts" setup>
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.min.css'
import { formatDate } from '@/utils/formatTime'
import * as DocApi from '@/api/farm/doc'
import { DOC_CATEGORY_ORDER } from '../platform/constants'

defineOptions({ name: 'FarmDoc' })

const route = useRoute()

// ---------- 列表 ----------
const listLoading = ref(false)
const docs = ref<DocApi.DocItemVO[]>([])
const keyword = ref('')

const groups = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  const rows = docs.value.filter(
    (d) => !k || d.title.toLowerCase().includes(k) || d.path.toLowerCase().includes(k)
  )
  const cats = [
    ...DOC_CATEGORY_ORDER,
    ...new Set(rows.map((d) => d.category).filter((c) => !DOC_CATEGORY_ORDER.includes(c)))
  ]
  return cats
    .map((category) => ({ category, items: rows.filter((d) => d.category === category) }))
    .filter((g) => g.items.length)
})

// ---------- Markdown 渲染 ----------
interface TocItem {
  id: string
  level: number
  text: string
}
const md: MarkdownIt = new MarkdownIt({
  html: false, // 文档里的原始 HTML 不执行，按文本显示
  linkify: true,
  highlight(str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs"><code>${hljs.highlight(str, { language: lang, ignoreIllegals: true }).value}</code></pre>`
      } catch {
        // 高亮失败就按纯文本
      }
    }
    return `<pre class="hljs"><code>${md.utils.escapeHtml(str)}</code></pre>`
  }
})
// 给标题加 id，用来生成目录
md.core.ruler.push('farm_heading_ids', (state) => {
  let n = 0
  for (const t of state.tokens) {
    if (t.type === 'heading_open') t.attrSet('id', `h-${++n}`)
  }
  return true
})

const doc = ref<DocApi.DocVO>()
const docLoading = ref(false)
const currentPath = ref('')
const mdRef = ref<HTMLElement>()

const html = computed(() => (doc.value?.type === 'md' ? md.render(doc.value.content) : ''))
const toc = computed<TocItem[]>(() => {
  if (doc.value?.type !== 'md') return []
  const tokens = md.parse(doc.value.content, {})
  const out: TocItem[] = []
  let n = 0
  tokens.forEach((t, i) => {
    if (t.type === 'heading_open') {
      n++
      const level = Number(t.tag.slice(1))
      if (level <= 3) out.push({ id: `h-${n}`, level, text: tokens[i + 1]?.content ?? '' })
    }
  })
  return out
})
const minLevel = computed(() => Math.min(...toc.value.map((t) => t.level), 6))

const scrollTo = (id: string) => {
  mdRef.value?.querySelector(`#${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

/** 文档里的相对链接（如 ../plans/xx.md）在这里打开；外链新窗口 */
const onMdClick = (e: MouseEvent) => {
  const a = (e.target as HTMLElement).closest('a')
  if (!a) return
  const href = a.getAttribute('href') ?? ''
  if (/^https?:\/\//.test(href)) {
    e.preventDefault()
    window.open(href, '_blank', 'noopener')
    return
  }
  if (href.startsWith('#')) return
  e.preventDefault()
  const target = resolvePath(currentPath.value, decodeURIComponent(href.split('#')[0]))
  if (docs.value.some((d) => d.path === target)) openDoc(target)
}
const resolvePath = (from: string, rel: string) => {
  const parts = from.split('/').slice(0, -1)
  for (const p of rel.replace(/^docs\//, '').split('/')) {
    if (p === '..') parts.pop()
    else if (p && p !== '.') parts.push(p)
  }
  return rel.startsWith('docs/') ? rel.slice(5) : parts.join('/')
}

const openDoc = async (path: string) => {
  if (path === currentPath.value && doc.value) return
  currentPath.value = path
  docLoading.value = true
  try {
    doc.value = await DocApi.getDoc(path)
    nextTick(() => mdRef.value?.parentElement?.scrollTo?.({ top: 0 }))
  } finally {
    docLoading.value = false
  }
}

onMounted(async () => {
  listLoading.value = true
  try {
    docs.value = await DocApi.getDocList()
  } finally {
    listLoading.value = false
  }
  const first = String(route.query.path ?? '') || groups.value[0]?.items[0]?.path
  if (first) openDoc(first)
})
</script>

<style lang="scss" scoped>
.farm-doc {
  display: flex;
  gap: 12px;
  height: calc(
    100vh - var(--top-tool-height) - var(--tags-view-height) - var(--app-footer-height) - 40px
  );
  min-height: 480px;
}

.farm-doc-side {
  display: flex;
  width: 300px;
  flex-direction: column;
  flex-shrink: 0;

  :deep(.farm-doc-side-body) {
    display: flex;
    height: 100%;
    padding: 12px;
    flex-direction: column;
    box-sizing: border-box;
  }
}

.farm-doc-list {
  overflow-y: auto;
  flex: 1;
}

.farm-doc-group {
  margin-bottom: 10px;
}

.farm-doc-group-h {
  display: flex;
  justify-content: space-between;
  padding: 4px 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);

  span {
    font-weight: 400;
  }
}

.farm-doc-item {
  display: flex;
  align-items: center;
  padding: 6px 8px;
  font-size: 13px;
  cursor: pointer;
  border-radius: 4px;

  &:hover {
    background: var(--el-fill-color-light);
  }

  &.active {
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }
}

.farm-doc-main {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;

  :deep(.farm-doc-main-body) {
    display: flex;
    height: 100%;
    padding: 0;
    flex-direction: column;
    box-sizing: border-box;
  }
}

.farm-doc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.farm-doc-body {
  min-height: 0;
  flex: 1;
}

.farm-doc-iframe {
  width: 100%;
  height: 100%;
  background: #fff;
  border: 0;
}

.farm-doc-md-wrap {
  display: flex;
  height: 100%;
  overflow-y: auto;
}

.farm-doc-toc {
  position: sticky;
  top: 0;
  width: 200px;
  max-height: 100%;
  padding: 16px 12px;
  overflow-y: auto;
  font-size: 12px;
  flex-shrink: 0;
  align-self: flex-start;
  border-left: 1px solid var(--el-border-color-lighter);
  box-sizing: border-box;
}

.farm-doc-toc-h {
  margin-bottom: 6px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.farm-doc-toc-item {
  padding: 3px 0;
  overflow: hidden;
  color: var(--el-text-color-regular);
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;

  &:hover {
    color: var(--el-color-primary);
  }
}

/* Markdown 排版：表格加边框，代码块、引用、列表 */
.farm-doc-md {
  min-width: 0;
  padding: 8px 24px 40px;
  font-size: 14px;
  line-height: 1.75;
  color: var(--el-text-color-primary);
  flex: 1;

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4) {
    margin: 1.4em 0 0.6em;
    font-weight: 600;
    line-height: 1.4;
    scroll-margin-top: 8px;
  }

  :deep(h1) {
    padding-bottom: 0.3em;
    font-size: 1.7em;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  :deep(h2) {
    padding-bottom: 0.25em;
    font-size: 1.4em;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  :deep(h3) {
    font-size: 1.18em;
  }

  :deep(p) {
    margin: 0.6em 0;
  }

  :deep(ul),
  :deep(ol) {
    padding-left: 2em;
    margin: 0.5em 0;
  }

  :deep(li) {
    margin: 0.2em 0;
  }

  :deep(ul) {
    list-style: disc;
  }

  :deep(ol) {
    list-style: decimal;
  }

  :deep(table) {
    display: block;
    max-width: 100%;
    margin: 0.8em 0;
    overflow-x: auto;
    border-collapse: collapse;
  }

  :deep(th),
  :deep(td) {
    padding: 6px 12px;
    text-align: left;
    vertical-align: top;
    border: 1px solid var(--el-border-color);
  }

  :deep(th) {
    font-weight: 600;
    white-space: nowrap;
    background: var(--el-fill-color-light);
  }

  :deep(tr:nth-child(2n) td) {
    background: var(--el-fill-color-lighter);
  }

  :deep(code) {
    padding: 0.15em 0.4em;
    font-family: Consolas, Monaco, monospace;
    font-size: 0.9em;
    background: var(--el-fill-color);
    border-radius: 3px;
  }

  :deep(pre) {
    padding: 12px 16px;
    margin: 0.8em 0;
    overflow-x: auto;
    font-size: 13px;
    line-height: 1.55;
    background: #f6f8fa;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;

    code {
      padding: 0;
      background: transparent;
    }
  }

  :deep(blockquote) {
    padding: 0.2em 1em;
    margin: 0.8em 0;
    color: var(--el-text-color-secondary);
    border-left: 4px solid var(--el-border-color);
  }

  :deep(a) {
    color: var(--el-color-primary);
    text-decoration: none;
  }

  :deep(hr) {
    margin: 1.5em 0;
    border: 0;
    border-top: 1px solid var(--el-border-color-lighter);
  }

  :deep(img) {
    max-width: 100%;
  }
}
</style>
