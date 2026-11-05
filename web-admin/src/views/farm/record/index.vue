<template>
  <ContentWrap>
    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="状态">
        <el-radio-group v-model="queryParams.status" @change="handleQuery">
          <el-radio-button v-for="s in RECORD_STATUS" :key="s.value" :value="s.value">
            {{ s.label }}
          </el-radio-button>
          <el-radio-button :value="ALL">全部</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="地块编号">
        <el-input
          v-model="queryParams.assetCode"
          placeholder="如 A-01"
          clearable
          class="!w-140px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <div v-loading="loading">
      <el-empty v-if="!list.length" description="没有符合条件的农事记录" />
      <el-row :gutter="12">
        <el-col v-for="r in list" :key="r.id" :xs="24" :md="12" :xl="8">
          <el-card shadow="hover" class="mb-12px record-card">
            <div class="flex items-center justify-between mb-6px">
              <span class="font-700">
                <el-tag size="small" effect="plain" class="mr-6px">{{ r.assetCode }}</el-tag>
                {{ r.title }}
              </span>
              <el-tag :type="enumOf(RECORD_STATUS, r.status).type">
                {{ enumLabel(RECORD_STATUS, r.status) }}
              </el-tag>
            </div>
            <div class="text-12px text-[var(--el-text-color-secondary)] mb-6px">
              {{ enumLabel(RECORD_KIND, r.kind) }} · 管家 {{ r.stewardName || '-' }} · 认养人
              {{ r.memberName || '-' }} · {{ fmtTime(r.createTime) }}
            </div>
            <div class="text-13px mb-8px leading-20px">{{ r.content }}</div>
            <div class="flex gap-6px mb-8px">
              <el-image
                v-for="(p, i) in r.photos"
                :key="i"
                :src="p"
                :preview-src-list="r.photos"
                :initial-index="Number(i)"
                preview-teleported
                fit="cover"
                class="w-90px h-90px rounded-4px"
              />
              <span v-if="!r.photos?.length" class="text-12px text-[var(--el-color-danger)]"
                >没有照片</span
              >
            </div>
            <div class="text-12px mb-6px">
              <span class="mr-12px">气温 {{ r.env?.airTemp }}℃</span>
              <span class="mr-12px">空气湿度 {{ r.env?.airHumidity }}%</span>
              <span>土壤湿度 {{ r.env?.soilMoisture }}%</span>
              <span class="ml-4px text-[var(--el-text-color-secondary)]">（打卡时环境快照）</span>
            </div>
            <div class="flex flex-wrap gap-6px mb-8px">
              <el-tag
                v-for="c in CHECKS"
                :key="c.key"
                size="small"
                :type="r.checks?.[c.key] ? 'success' : 'danger'"
                :effect="r.checks?.[c.key] ? 'plain' : 'dark'"
              >
                <Icon
                  :icon="r.checks?.[c.key] ? 'ep:circle-check' : 'ep:circle-close'"
                  class="mr-2px"
                />
                {{ c.label }}：{{ r.checks?.[c.key] ? '通过' : '未通过' }}
              </el-tag>
            </div>
            <div v-if="r.status === 0" class="text-right">
              <el-button size="small" type="danger" plain @click="openReject(r)">驳回</el-button>
              <el-button size="small" type="primary" @click="handleApprove(r)">通过</el-button>
            </div>
            <div v-else class="text-12px text-[var(--el-text-color-secondary)]">
              {{ fmtTime(r.auditTime) }} 审核{{ r.auditRemark ? '：' + r.auditRemark : '' }}
            </div>
          </el-card>
        </el-col>
      </el-row>
      <Pagination
        :total="total"
        v-model:page="queryParams.pageNo"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </div>
  </ContentWrap>

  <Dialog v-model="rejectVisible" title="驳回农事记录" width="480px">
    <div class="mb-8px text-13px">
      {{ rejectRow?.assetCode }} · {{ rejectRow?.title }}（管家 {{ rejectRow?.stewardName }}）
    </div>
    <el-input
      v-model="rejectReason"
      type="textarea"
      :rows="3"
      placeholder="请填写驳回原因，管家会在管家端看到，例如：照片看不清作业内容，请补拍"
    />
    <template #footer>
      <el-button type="danger" :loading="auditing" @click="submitReject">驳 回</el-button>
      <el-button @click="rejectVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import * as RecordApi from '@/api/farm/record'
import { RECORD_KIND, RECORD_STATUS, enumLabel, enumOf, fmtTime } from '../constants'

defineOptions({ name: 'FarmRecord' })

const ALL = -1
const CHECKS = [
  { key: 'exifTime', label: '拍摄时间' },
  { key: 'gpsFence', label: '定位在基地内' },
  { key: 'duplicate', label: '非重复照片' }
] as const

const message = useMessage()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const queryParams = reactive<any>({ pageNo: 1, pageSize: 10, status: 0, assetCode: undefined })

const getList = async () => {
  loading.value = true
  try {
    const res = await RecordApi.getRecordPage({
      ...queryParams,
      status: queryParams.status === ALL ? undefined : queryParams.status
    })
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

const auditing = ref(false)
const handleApprove = async (r: any) => {
  const failed = CHECKS.filter((c) => !r.checks?.[c.key]).map((c) => c.label)
  try {
    await message.confirm(
      failed.length
        ? `机器校验有 ${failed.length} 项未通过（${failed.join('、')}），仍然通过吗？通过后认养人的生长档案里会看到这条记录。`
        : '通过后认养人的生长档案里会看到这条记录。'
    )
  } catch {
    return
  }
  await RecordApi.auditRecord({ id: r.id, pass: true })
  message.success('已通过')
  getList()
}

const rejectVisible = ref(false)
const rejectRow = ref<any>()
const rejectReason = ref('')
const openReject = (r: any) => {
  rejectRow.value = r
  rejectReason.value = ''
  rejectVisible.value = true
}
const submitReject = async () => {
  if (!rejectReason.value.trim()) {
    message.warning('驳回时请填写原因')
    return
  }
  auditing.value = true
  try {
    await RecordApi.auditRecord({
      id: rejectRow.value.id,
      pass: false,
      remark: rejectReason.value.trim()
    })
    message.success('已驳回')
    rejectVisible.value = false
    getList()
  } finally {
    auditing.value = false
  }
}

onMounted(getList)
// keep-alive 切回来时刷新（从工作台待办跳过来、或在别的页面改过数据）
let mounted = false
onActivated(() => {
  if (mounted) getList()
  mounted = true
})
</script>
<style scoped>
.record-card :deep(.el-card__body) {
  padding: 14px;
}
</style>
