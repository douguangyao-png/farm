<template>
  <!-- 右侧竖着的「问题反馈」入口（R-12），交互参照 zhuji-admin 的 FeedbackDock -->
  <div v-show="!visible" class="farm-fb-fab" role="button" aria-label="问题反馈" @click="open">
    <Icon icon="ep:chat-line-square" :size="16" />
    <span>问题反馈</span>
  </div>

  <el-drawer
    v-model="visible"
    title="问题反馈"
    direction="rtl"
    size="440px"
    append-to-body
    :close-on-click-modal="false"
  >
    <div class="farm-fb-panel">
      <el-alert
        v-if="lastNo"
        type="success"
        show-icon
        :closable="true"
        class="mb-12px"
        :title="`已提交，编号 ${lastNo}`"
        description="处理结果可以在「系统管理 → 问题反馈」里查看。"
        @close="lastNo = ''"
      />

      <el-form label-position="top">
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio-button v-for="t in FEEDBACK_TYPES" :key="t.value" :value="t.value">
              {{ t.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="6"
            maxlength="2000"
            show-word-limit
            :placeholder="placeholder"
            aria-label="反馈内容"
          />
        </el-form-item>
        <el-form-item :label="`截图（${form.screenshots.length}/${MAX_IMAGES}）`">
          <div class="farm-fb-imgs">
            <div v-for="(src, i) in form.screenshots" :key="i" class="farm-fb-img">
              <el-image
                :src="src"
                :preview-src-list="form.screenshots"
                :initial-index="i"
                fit="cover"
                preview-teleported
              />
              <span class="farm-fb-del" title="删除截图" @click="removeImage(i)">
                <Icon icon="ep:close" :size="12" />
              </span>
            </div>
            <label v-if="form.screenshots.length < MAX_IMAGES" class="farm-fb-add" title="选择图片">
              <input type="file" accept="image/*" multiple hidden @change="onChoose" />
              <Icon icon="ep:plus" :size="18" />
              <span>截图</span>
            </label>
          </div>
          <div class="farm-fb-tip">
            可以直接按 Ctrl+V 粘贴截图（Win+Shift+S 截屏后粘贴），也可以点「截图」选文件，最多
            {{ MAX_IMAGES }} 张。
          </div>
        </el-form-item>
      </el-form>

      <div class="farm-fb-meta">
        <div class="farm-fb-meta-h">提交时会自动带上：</div>
        <dl>
          <dt>页面</dt>
          <dd
            >{{ pageTitle || '-' }} <span class="farm-fb-mono">{{ pagePath }}</span></dd
          >
          <dt>账号</dt>
          <dd>{{ nickname || '-' }}</dd>
          <dt>浏览器</dt>
          <dd class="farm-fb-ua">{{ userAgent }}</dd>
        </dl>
      </div>
    </div>
    <template #footer>
      <el-button @click="visible = false">关 闭</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="!form.content.trim()"
        @click="submit"
      >
        提 交
      </el-button>
    </template>
  </el-drawer>
</template>

<script lang="ts" setup>
import { useUserStore } from '@/store/modules/user'
import * as FeedbackApi from '@/api/farm/feedback'
import { FEEDBACK_TYPES } from '../../platform/constants'

defineOptions({ name: 'FarmFeedbackDock' })

const MAX_IMAGES = 3
/** 截图压到最宽 1280、JPEG，避免 data URL 太大 */
const MAX_WIDTH = 1280

const message = useMessage()
const route = useRoute()
const userStore = useUserStore()

const visible = ref(false)
const submitting = ref(false)
const lastNo = ref('')
const form = reactive({
  type: 'bug' as FeedbackApi.FeedbackVO['type'],
  content: '',
  screenshots: [] as string[]
})

const pageTitle = computed(() => String(route.meta?.title ?? document.title ?? ''))
const pagePath = computed(() => route.fullPath)
const nickname = computed(() => userStore.getUser?.nickname ?? '')
const userAgent = navigator.userAgent
const placeholder = computed(() =>
  form.type === 'bug'
    ? '做了什么操作、看到了什么、本来应该是什么样'
    : form.type === 'advice'
      ? '希望怎么改，为什么'
      : '想了解什么'
)

const open = () => {
  visible.value = true
}

/** 读图片并压缩成 JPEG data URL */
const shrinkImage = (file: Blob): Promise<string> =>
  new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      const k = Math.min(1, MAX_WIDTH / img.width)
      const canvas = document.createElement('canvas')
      canvas.width = Math.round(img.width * k)
      canvas.height = Math.round(img.height * k)
      const g = canvas.getContext('2d')!
      g.fillStyle = '#fff'
      g.fillRect(0, 0, canvas.width, canvas.height)
      g.drawImage(img, 0, 0, canvas.width, canvas.height)
      URL.revokeObjectURL(url)
      resolve(canvas.toDataURL('image/jpeg', 0.8))
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('图片读不出来'))
    }
    img.src = url
  })

const addImages = async (files: Blob[]) => {
  const images = files.filter((f) => f.type.startsWith('image/'))
  if (!images.length) return
  const room = MAX_IMAGES - form.screenshots.length
  if (images.length > room) message.warning(`最多上传 ${MAX_IMAGES} 张截图`)
  if (room <= 0) return
  try {
    const out = await Promise.all(images.slice(0, room).map(shrinkImage))
    form.screenshots.push(...out.slice(0, MAX_IMAGES - form.screenshots.length))
  } catch (e) {
    message.error((e as Error).message)
  }
}

const onChoose = (e: Event) => {
  const input = e.target as HTMLInputElement
  addImages(Array.from(input.files ?? []))
  input.value = ''
}

const removeImage = (i: number) => {
  form.screenshots.splice(i, 1)
}

/** 面板打开时，整个页面的粘贴都收进来（输入框里粘文字照常） */
const onPaste = (e: ClipboardEvent) => {
  if (!visible.value || !e.clipboardData) return
  const files = Array.from(e.clipboardData.items)
    .filter((i) => i.kind === 'file' && i.type.startsWith('image/'))
    .map((i) => i.getAsFile())
    .filter((f): f is File => !!f)
  if (files.length) {
    e.preventDefault()
    addImages(files)
  }
}
onMounted(() => document.addEventListener('paste', onPaste))
onBeforeUnmount(() => document.removeEventListener('paste', onPaste))

const submit = async () => {
  if (!form.content.trim()) return
  submitting.value = true
  try {
    const res = await FeedbackApi.createFeedback({
      type: form.type,
      content: form.content.trim(),
      screenshots: [...form.screenshots],
      pageTitle: pageTitle.value,
      pagePath: pagePath.value,
      userAgent
    })
    lastNo.value = res.no
    message.success(`已提交，编号 ${res.no}`)
    form.content = ''
    form.screenshots = []
    form.type = 'bug'
  } catch {
    // 失败时保留内容和截图，方便重试；错误提示由请求拦截器弹出
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.farm-fb-fab {
  position: fixed;
  right: 0;
  top: 50%;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 6px;
  transform: translateY(-50%);
  font-size: 13px;
  line-height: 1.2;
  color: #fff;
  cursor: pointer;
  background: var(--el-color-primary);
  border-radius: 6px 0 0 6px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 18%);
  writing-mode: vertical-rl;
  letter-spacing: 2px;
  user-select: none;

  &:hover {
    background: var(--el-color-primary-light-3);
  }
}

.farm-fb-imgs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.farm-fb-img,
.farm-fb-add {
  position: relative;
  width: 96px;
  height: 72px;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
}

.farm-fb-img .el-image {
  width: 100%;
  height: 100%;
}

.farm-fb-del {
  position: absolute;
  top: 2px;
  right: 2px;
  display: flex;
  width: 18px;
  height: 18px;
  color: #fff;
  cursor: pointer;
  background: rgb(0 0 0 / 55%);
  border-radius: 50%;
  align-items: center;
  justify-content: center;
}

.farm-fb-add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  border-style: dashed;

  &:hover {
    color: var(--el-color-primary);
    border-color: var(--el-color-primary);
  }
}

.farm-fb-tip {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.farm-fb-meta {
  padding: 10px 12px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border-radius: 4px;

  dl {
    display: grid;
    margin: 6px 0 0;
    grid-template-columns: 52px 1fr;
    row-gap: 4px;
  }

  dt {
    color: var(--el-text-color-secondary);
  }

  dd {
    margin: 0;
    word-break: break-all;
  }
}

.farm-fb-mono {
  font-family: monospace;
  color: var(--el-text-color-secondary);
}

.farm-fb-ua {
  color: var(--el-text-color-secondary);
}
</style>
