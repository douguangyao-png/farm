<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :inline="true"
      :model="queryParams"
      class="-mb-15px"
      label-width="68px"
    >
      <el-form-item label="编号" prop="no">
        <el-input
          v-model="queryParams.no"
          class="!w-160px"
          clearable
          placeholder="如 FB-0001"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="类型" prop="type">
        <el-select v-model="queryParams.type" class="!w-140px" clearable placeholder="全部类型">
          <el-option v-for="t in FEEDBACK_TYPES" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" class="!w-140px" clearable placeholder="全部状态">
          <el-option
            v-for="s in FEEDBACK_STATUS"
            :key="s.value"
            :label="s.label"
            :value="s.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <div class="mb-8px text-12px text-[var(--el-text-color-secondary)]">
      {{
        platform
          ? '这里是所有租户提交的反馈，点开可以回复和改状态。'
          : '这里是本农场提交的反馈和平台的回复。任何页面右侧的「问题反馈」都能提交。'
      }}
    </div>
    <el-table
      v-loading="loading"
      :data="list"
      :stripe="true"
      class="cursor-pointer"
      @row-click="openDetail"
    >
      <el-table-column label="编号" prop="no" width="100" />
      <el-table-column v-if="platform" label="租户" prop="tenantName" width="120" />
      <el-table-column label="类型" width="80">
        <template #default="{ row }">
          <el-tag :type="findEnum(FEEDBACK_TYPES, row.type)?.tag ?? 'info'">
            {{ findEnum(FEEDBACK_TYPES, row.type)?.label ?? row.type }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="内容摘要" min-width="240">
        <template #default="{ row }">
          <span v-if="row.screenshots?.length" class="mr-4px text-[var(--el-color-primary)]"
            >[图{{ row.screenshots.length }}]</span
          >
          <span>{{ summary(row.content) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="页面" prop="pageTitle" width="130" show-overflow-tooltip />
      <el-table-column label="提交人" prop="creatorName" width="110" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="findEnum(FEEDBACK_STATUS, row.status)?.tag ?? 'info'">
            {{ findEnum(FEEDBACK_STATUS, row.status)?.label ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" prop="createTime" width="170" :formatter="dateFormatter" />
    </el-table>
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <el-drawer
    v-model="detailVisible"
    size="560px"
    :title="detail ? `${detail.no} · ${typeLabel(detail.type)}` : '反馈详情'"
  >
    <div v-if="detail" v-loading="detailLoading" class="farm-fb-detail">
      <div class="mb-8px">
        <el-tag :type="findEnum(FEEDBACK_STATUS, detail.status)?.tag ?? 'info'">
          {{ findEnum(FEEDBACK_STATUS, detail.status)?.label }}
        </el-tag>
        <span class="ml-8px text-13px text-[var(--el-text-color-secondary)]">
          {{ platform ? detail.tenantName + ' · ' : '' }}{{ detail.creatorName }} ·
          {{ formatDate(detail.createTime) }}
        </span>
      </div>
      <p class="farm-fb-text">{{ detail.content }}</p>

      <div v-if="detail.screenshots?.length" class="farm-fb-shots">
        <el-image
          v-for="(src, i) in detail.screenshots"
          :key="i"
          :src="src"
          :preview-src-list="detail.screenshots"
          :initial-index="i"
          fit="cover"
          preview-teleported
          class="farm-fb-shot"
        />
      </div>

      <el-descriptions :column="1" border size="small" class="mt-12px">
        <el-descriptions-item label="页面">
          {{ detail.pageTitle || '-' }}
          <span class="ml-4px font-mono text-12px text-[var(--el-text-color-secondary)]">{{
            detail.pagePath
          }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="浏览器">
          <span class="text-12px break-all">{{ detail.userAgent || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="最后更新">{{
          formatDate(detail.updateTime)
        }}</el-descriptions-item>
      </el-descriptions>

      <div class="mt-16px mb-8px font-600">往来回复</div>
      <div
        v-if="!detail.replies?.length"
        class="farm-fb-reply text-[var(--el-text-color-secondary)]"
        >还没有回复</div
      >
      <div v-for="r in detail.replies" :key="r.id" class="farm-fb-reply">
        <div class="text-12px text-[var(--el-text-color-secondary)]">
          <el-tag size="small" type="primary" class="mr-4px">平台</el-tag>{{ r.creatorName }} ·
          {{ formatDate(r.createTime) }}
        </div>
        <div class="mt-4px whitespace-pre-wrap">{{ r.content }}</div>
      </div>

      <template v-if="platform">
        <el-divider />
        <el-form label-width="68px">
          <el-form-item label="状态">
            <el-radio-group v-model="statusEdit" :disabled="saving">
              <el-radio-button v-for="s in FEEDBACK_STATUS" :key="s.value" :value="s.value">{{
                s.label
              }}</el-radio-button>
            </el-radio-group>
            <el-button
              v-hasPermi="['farm:feedback:update']"
              class="ml-8px"
              :disabled="statusEdit === detail.status"
              :loading="saving"
              @click="saveStatus"
            >
              改状态
            </el-button>
          </el-form-item>
          <el-form-item label="回复">
            <el-input
              v-model="replyText"
              type="textarea"
              :rows="3"
              maxlength="2000"
              placeholder="回复提交人：怎么处理的、什么时候修好"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              v-hasPermi="['farm:feedback:reply']"
              type="primary"
              :disabled="!replyText.trim()"
              :loading="saving"
              @click="sendReply"
              >回 复</el-button
            >
            <span class="ml-8px text-12px text-[var(--el-text-color-secondary)]"
              >待处理的反馈回复后会自动变成「处理中」</span
            >
          </el-form-item>
        </el-form>
      </template>
    </div>
  </el-drawer>
</template>

<script lang="ts" setup>
import type { FormInstance } from 'element-plus'
import { dateFormatter, formatDate } from '@/utils/formatTime'
import * as FeedbackApi from '@/api/farm/feedback'
import { FEEDBACK_STATUS, FEEDBACK_TYPES, findEnum, isPlatformUser } from '../platform/constants'

defineOptions({ name: 'FarmFeedback' })

const message = useMessage()
const platform = isPlatformUser()

const loading = ref(false)
const list = ref<FeedbackApi.FeedbackVO[]>([])
const total = ref(0)
const queryFormRef = ref<FormInstance>()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  no: undefined as string | undefined,
  type: undefined as string | undefined,
  status: undefined as number | undefined
})

const summary = (s: string) => (s.length > 60 ? s.slice(0, 60) + '…' : s)
const typeLabel = (t: string) => findEnum(FEEDBACK_TYPES, t)?.label ?? t

const getList = async () => {
  loading.value = true
  try {
    const data = await FeedbackApi.getFeedbackPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

// ---------- 详情、回复、改状态 ----------
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<FeedbackApi.FeedbackVO>()
const statusEdit = ref(0)
const replyText = ref('')
const saving = ref(false)

const loadDetail = async (id: number) => {
  detailLoading.value = true
  try {
    detail.value = await FeedbackApi.getFeedback(id)
    statusEdit.value = detail.value.status
  } finally {
    detailLoading.value = false
  }
}
const openDetail = (row: FeedbackApi.FeedbackVO) => {
  detail.value = row
  statusEdit.value = row.status
  replyText.value = ''
  detailVisible.value = true
  loadDetail(row.id)
}
const sendReply = async () => {
  if (!detail.value || !replyText.value.trim()) return
  saving.value = true
  try {
    await FeedbackApi.replyFeedback({ id: detail.value.id, content: replyText.value.trim() })
    message.success('已回复')
    replyText.value = ''
    await loadDetail(detail.value.id)
    getList()
  } finally {
    saving.value = false
  }
}
const saveStatus = async () => {
  if (!detail.value) return
  saving.value = true
  try {
    await FeedbackApi.updateFeedbackStatus({ id: detail.value.id, status: statusEdit.value })
    message.success(`已改为「${findEnum(FEEDBACK_STATUS, statusEdit.value)?.label}」`)
    await loadDetail(detail.value.id)
    getList()
  } finally {
    saving.value = false
  }
}

onMounted(getList)
</script>

<style lang="scss" scoped>
.farm-fb-text {
  margin: 0;
  line-height: 1.7;
  white-space: pre-wrap;
}

.farm-fb-shots {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.farm-fb-shot {
  width: 150px;
  height: 100px;
  cursor: zoom-in;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
}

.farm-fb-reply {
  padding: 8px 10px;
  margin-bottom: 8px;
  font-size: 13px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}
</style>
