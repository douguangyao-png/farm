<template>
  <ContentWrap>
    <div class="flex items-start justify-between mb-12px">
      <div>
        <div class="text-18px font-600">管家管理</div>
        <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
          管家是履约的关键人力节点：App 端打卡产出 C 端看到的一切透明内容
        </div>
      </div>
      <div>
        <el-button @click="notImp">派单</el-button>
        <el-button type="primary" @click="notImp"
          ><Icon icon="ep:plus" class="mr-5px" />添加管家</el-button
        >
      </div>
    </div>

    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="姓名/手机">
        <el-input
          v-model="queryParams.keyword"
          placeholder="姓名或手机号"
          clearable
          class="!w-180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="管家" min-width="150">
        <template #default="{ row }">
          <div class="flex items-center gap-8px">
            <el-avatar :size="32">{{ (row.name || '?').charAt(0) }}</el-avatar>
            <div>
              <div class="font-600">{{ row.name }}</div>
              <div class="text-12px text-[var(--el-text-color-secondary)]">{{ row.mobile }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="角色" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small">{{ row.roleName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="负责标的" min-width="190">
        <template #default="{ row }">
          <div>{{ row.baseName || '-' }}</div>
          <div class="text-12px text-[var(--el-text-color-secondary)]">
            共 {{ row.assetTotal }} 个，已认养 {{ row.assetAdopted }} 个
          </div>
        </template>
      </el-table-column>
      <el-table-column label="服务认养人" width="110" align="right">
        <template #default="{ row }">{{ row.memberCount || '—' }}</template>
      </el-table-column>
      <el-table-column label="月度任务完成" width="150">
        <template #default="{ row }">
          <template v-if="row.monthTaskRate != null">
            <el-progress
              :percentage="row.monthTaskRate"
              :show-text="false"
              :status="row.monthTaskRate >= 100 ? 'success' : undefined"
            />
            <div class="text-11px text-[var(--el-text-color-secondary)]">
              {{ row.monthTaskDone }}/{{ row.monthTaskTotal }}
            </div>
          </template>
          <span v-else class="text-[var(--el-text-color-secondary)]">本月无任务</span>
        </template>
      </el-table-column>
      <el-table-column label="平均响应" width="100" align="center">
        <template #default="{ row }">
          {{ row.avgResponseMinutes != null ? `${row.avgResponseMinutes} 分钟` : '—' }}
        </template>
      </el-table-column>
      <el-table-column label="满意度" width="90" align="center">
        <template #default>
          <span class="text-[var(--el-text-color-secondary)]">暂无评价</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="enumOf(STEWARD_STATUS, row.status).type">
            {{ enumLabel(STEWARD_STATUS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" align="center" fixed="right">
        <template #default>
          <el-button link type="primary" @click="notImp">绩效</el-button>
          <el-button link type="primary" @click="notImp">调整</el-button>
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
      <ContentWrap title="管家 App 工作台">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="今日待办任务">
            {{ overview.today?.pendingTasks ?? '-' }} 项（含已逾期）
          </el-descriptions-item>
          <el-descriptions-item label="待审核打卡记录">
            {{ overview.today?.pendingRecords ?? '-' }} 条
          </el-descriptions-item>
          <el-descriptions-item label="待接单代管工单">
            {{ overview.today?.pendingServiceOrders ?? '-' }} 单
          </el-descriptions-item>
          <el-descriptions-item v-for="n in APP_NOTES" :key="n.k" :label="n.k">
            {{ n.v }}
          </el-descriptions-item>
        </el-descriptions>
      </ContentWrap>
    </el-col>
    <el-col :xs="24" :lg="12">
      <ContentWrap title="本月绩效排行（按任务完成率）">
        <div v-if="!(overview.ranking ?? []).length" class="text-[var(--el-text-color-secondary)]">
          本月还没有管家任务数据
        </div>
        <div v-for="r in overview.ranking ?? []" :key="r.id" class="flex items-center mb-10px">
          <span class="w-64px">{{ r.name }}</span>
          <el-progress class="flex-1" :percentage="r.score" :stroke-width="10" :show-text="false" />
          <span class="w-48px text-right font-600">{{ r.score }}</span>
        </div>
        <div class="text-12px text-[var(--el-text-color-secondary)] mt-8px">
          绩效与客户续费率挂钩，用于月度奖金分配（原型口径；本期仅展示任务完成率）。
        </div>
      </ContentWrap>
    </el-col>
  </el-row>
</template>
<script lang="ts" setup>
import * as StewardApi from '@/api/farm/stewardManage'
import { enumLabel, enumOf } from '../constants'
import { APP_NOTES, STEWARD_STATUS } from './constants'

defineOptions({ name: 'FarmStewardManage' })

const message = useMessage()
const loading = ref(false)
const list = ref<StewardApi.StewardManageVO[]>([])
const total = ref(0)
const overview = ref<Partial<StewardApi.StewardOverviewVO>>({})
const queryParams = reactive<any>({ pageNo: 1, pageSize: 10, keyword: undefined })

const notImp = () => message.info('演示环境暂不支持该操作')

const getList = async () => {
  loading.value = true
  try {
    const res = await StewardApi.getStewardManagePage(queryParams)
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
  queryParams.keyword = undefined
  handleQuery()
}

onMounted(async () => {
  getList()
  overview.value = await StewardApi.getStewardOverview()
})
let mounted = false
onActivated(() => {
  if (mounted) getList()
  mounted = true
})
</script>
