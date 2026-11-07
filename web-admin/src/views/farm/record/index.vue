<template>
  <!-- 统计卡（原型 archivePub：今日提交 / 自动过检 / 需人工复核 / 本月累计） -->
  <el-row :gutter="12" class="mb-12px">
    <el-col v-for="k in statCards" :key="k.label" :xs="12" :sm="6">
      <el-card shadow="never">
        <div class="text-13px text-[var(--el-text-color-secondary)]">{{ k.label }}</div>
        <div class="mt-6px text-24px font-700">{{ k.value }}</div>
        <div class="mt-4px text-12px text-[var(--el-text-color-secondary)]">{{ k.sub }}</div>
      </el-card>
    </el-col>
  </el-row>

  <ContentWrap>
    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="状态">
        <el-radio-group v-model="queryParams.status" @change="handleQuery">
          <el-radio-button v-for="s in RECORD_PUBLISH_STATUS" :key="s.value" :value="s.value">
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
        <el-button
          type="primary"
          plain
          :disabled="!selectedPending.length"
          @click="handleBatchApprove"
        >
          <Icon icon="ep:select" class="mr-5px" />批量通过{{
            selectedPending.length ? `（${selectedPending.length}）` : ''
          }}
        </el-button>
        <el-button @click="message.info('演示版暂未开放：代管家发布')">
          <Icon icon="ep:edit-pen" class="mr-5px" />代管家发布
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" row-key="id" @selection-change="onSelectionChange">
      <el-table-column type="selection" width="44" :selectable="isPending" />
      <el-table-column type="expand">
        <template #default="{ row: r }">
          <div class="px-20px py-8px">
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
              <span class="ml-12px text-[var(--el-text-color-secondary)]"
                >认养人 {{ r.memberName || '-' }}</span
              >
            </div>
            <div class="flex flex-wrap gap-6px">
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
            <div
              v-if="r.status !== 0"
              class="mt-8px text-12px text-[var(--el-text-color-secondary)]"
            >
              {{ fmtTime(r.auditTime) }} 审核{{ r.auditRemark ? '：' + r.auditRemark : '' }}
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="标的" prop="assetCode" width="110">
        <template #default="{ row }">
          <span class="font-700">{{ row.assetCode }}</span>
        </template>
      </el-table-column>
      <el-table-column label="提交人" prop="stewardName" width="100" />
      <el-table-column label="提交时间" width="150">
        <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="类型与摘要" prop="summary" min-width="220" show-overflow-tooltip />
      <el-table-column label="照片" width="80" align="center">
        <template #default="{ row }">{{ row.photoCount }} 张</template>
      </el-table-column>
      <el-table-column label="机器校验" width="200">
        <template #default="{ row }">
          <el-tag :type="enumOf(MACHINE_CHECK, row.machineCheck).type">
            {{ enumLabel(MACHINE_CHECK, row.machineCheck) }}
          </el-tag>
          <span
            v-if="row.machineCheck === 'suspect'"
            class="ml-6px text-12px text-[var(--el-text-color-secondary)]"
            >{{ row.machineCheckNote }}</span
          >
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="enumOf(RECORD_PUBLISH_STATUS, row.status).type">
            {{ enumLabel(RECORD_PUBLISH_STATUS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" align="center" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button link type="primary" @click="handleApprove(row)">通过</el-button>
            <el-button link type="danger" @click="openReject(row)">驳回</el-button>
          </template>
          <el-button v-else link type="primary" @click="message.info('演示版暂未开放：预览 C 端')">
            预览 C 端
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

  <el-row :gutter="12">
    <el-col :xs="24" :lg="12">
      <el-card shadow="never" class="mb-12px">
        <template #header><span class="font-700">机器校验规则（防止「摆拍式透明」）</span></template>
        <div v-for="x in RULES" :key="x[0]" class="rule-row">
          <span class="text-[var(--el-text-color-secondary)]">{{ x[0] }}</span>
          <span>{{ x[1] }}</span>
        </div>
      </el-card>
    </el-col>
    <el-col :xs="24" :lg="12">
      <el-card shadow="never" class="mb-12px">
        <template #header><span class="font-700">发布联动</span></template>
        <div v-for="x in LINKS" :key="x[0]" class="rule-row !block">
          <div>{{ x[0] }}</div>
          <div class="text-12px text-[var(--el-text-color-secondary)]">{{ x[1] }}</div>
        </div>
      </el-card>
    </el-col>
  </el-row>

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
import { enumLabel, enumOf, fmtTime } from '../constants'
import { MACHINE_CHECK, RECORD_PUBLISH_STATUS } from './constants'

defineOptions({ name: 'FarmRecord' })

const ALL = -1
const CHECKS = [
  { key: 'exifTime', label: '拍摄时间' },
  { key: 'gpsFence', label: '定位在基地内' },
  { key: 'duplicate', label: '非重复照片' }
] as const
const RULES = [
  ['EXIF 时间校验', '与提交时间偏差 < 30 分钟'],
  ['GPS 地理围栏', '拍摄点须在基地围栏范围内'],
  ['重复画面检测', '与历史图片相似度 < 85%'],
  ['设备指纹', '须为已备案的管家工作机'],
  ['异常处置', '标记为「存疑」并通知基地负责人复核']
]
const LINKS = [
  ['写入 C 端生长档案时间轴', '认养人可在小程序查看'],
  ['推送订阅消息', '您家地块有新动态'],
  ['生成溯源履历节点', '记录存证，生成新的存证哈希（演示）'],
  ['管家绩效 +1', '计入月度打卡完成度']
]

const message = useMessage()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const queryParams = reactive<any>({ pageNo: 1, pageSize: 10, status: 0, assetCode: undefined })

// 统计卡
const stats = ref<any>()
const statCards = computed(() => {
  const d = stats.value || {}
  return [
    { label: '今日提交', value: d.todayCount ?? '-', sub: `其中待审核 ${d.pendingCount ?? '-'}` },
    { label: '自动过检', value: d.autoPassCount ?? '-', sub: '本月 · 拍摄时间 / 定位 / 重复画面校验通过' },
    { label: '需人工复核', value: d.manualReviewCount ?? '-', sub: '本月 · 画面存疑 / 地点偏移' },
    { label: '本月累计', value: d.monthCount ?? '-', sub: `平均每块地 ${d.avgPerAsset ?? '-'} 条` }
  ]
})
const getStats = async () => {
  stats.value = await RecordApi.getRecordStats()
}

// 批量通过：只有待审核的行可选
const selected = ref<any[]>([])
const selectedPending = computed(() => selected.value.filter((r) => r.status === 0))
const isPending = (r: any) => r.status === 0
const onSelectionChange = (rows: any[]) => {
  selected.value = rows
}

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
  getStats()
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

const handleBatchApprove = async () => {
  const rows = selectedPending.value
  const suspect = rows.filter((r) => r.machineCheck === 'suspect').length
  try {
    await message.confirm(
      `确定批量通过选中的 ${rows.length} 条待审核记录吗？${suspect ? `其中 ${suspect} 条机器校验存疑。` : ''}通过后认养人的生长档案里会看到这些记录。`
    )
  } catch {
    return
  }
  for (const r of rows) await RecordApi.auditRecord({ id: r.id, pass: true })
  message.success(`已通过 ${rows.length} 条`)
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
.rule-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
}
</style>
