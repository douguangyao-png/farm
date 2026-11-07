<!-- 权限串：farm:camera:query（本页只读；接入新设备跳转到 IoT 设备页，由 farm:assetdevice:create 控制）。画面为占位，真实直播已排除 -->
<template>
  <div v-loading="loading">
    <div class="flex items-start justify-between mb-12px">
      <div>
        <div class="text-18px font-600">
          视频监控
          <el-tag type="warning" size="small" effect="plain" class="ml-6px">状态与观看数据为演示</el-tag>
        </div>
        <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
          按「标的」精细化授权，非认养人无法查看对应画面；摄像头清单取自地块与畜禽上的绑定
        </div>
      </div>
      <div class="flex gap-8px">
        <el-button @click="message.info('演示版暂未开放')">批量配置</el-button>
        <el-button type="primary" @click="router.push('/farm-product/asset-device')">
          <Icon icon="ep:plus" class="mr-5px" />接入新设备
        </el-button>
      </div>
    </div>

    <StatCards :items="statItems" />

    <el-row :gutter="12">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="mb-12px">
          <template #header><span class="font-700">画面预览</span></template>
          <div class="screen" :class="{ off: current?.status === 'offline' }">
            <div class="live" :class="{ off: current?.status === 'offline' }">
              {{ current?.status === 'offline' ? 'OFFLINE' : 'LIVE' }} · {{ current?.code || '-' }}
            </div>
            <div class="center">{{ current ? current.position : '暂无摄像头' }}</div>
            <div class="stamp">{{ stamp }} · 演示画面</div>
          </div>
          <div class="flex flex-wrap gap-7px mt-10px">
            <el-check-tag
              v-for="c in cameras"
              :key="c.code"
              :checked="current?.code === c.code"
              @change="currentCode = c.code"
            >
              {{ c.code }} · {{ c.position }}{{ c.status === 'offline' ? '（离线）' : '' }}
            </el-check-tag>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="mb-12px">
          <template #header><span class="font-700">开放策略</span></template>
          <div v-for="x in data.policy || []" :key="x.k" class="flex py-6px text-13px">
            <span class="w-110px flex-shrink-0 text-[var(--el-text-color-secondary)]">{{ x.k }}</span>
            <span>{{ x.v }}</span>
          </div>
          <el-alert
            class="mt-10px"
            type="info"
            :closable="false"
            show-icon
            title="建议在基地显著位置安装，并在合同写明摄像头位置，避免纠纷。以上为策略说明，修改入口本期未开放。"
          />
        </el-card>
      </el-col>
    </el-row>

    <ContentWrap>
      <el-table :data="cameras">
        <el-table-column label="设备编号" prop="code" width="110">
          <template #default="{ row }"><b>{{ row.code }}</b></template>
        </el-table-column>
        <el-table-column label="安装位置" prop="position" min-width="150" />
        <el-table-column label="绑定标的" prop="bindText" min-width="170" />
        <el-table-column label="可见认养人" width="120">
          <template #default="{ row }">
            {{ row.status === 'offline' ? '-' : `${row.visibleMembers} 位认养人` }}
          </template>
        </el-table-column>
        <el-table-column label="今日观看" width="100">
          <template #default="{ row }">{{ row.status === 'offline' ? '-' : `${row.todayViews} 次` }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'online' ? 'success' : 'danger'">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="currentCode = row.code">预览</el-button>
            <el-button link type="primary" @click="message.info('演示版暂未开放录像回放')">回放</el-button>
            <el-button link type="primary" @click="message.info('演示版暂未开放')">设置</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="mt-8px text-12px text-[var(--el-text-color-secondary)]">
        「可见认养人」为真实数据（绑定标的上在养的认养人数）；今日观看、状态为演示值。
      </div>
    </ContentWrap>
  </div>
</template>
<script lang="ts" setup>
import * as DeviceApi from '@/api/farm/device'
import { fmtTime } from '../constants'
import StatCards from '../fulfill/StatCards.vue'

defineOptions({ name: 'FarmCamera' })

const router = useRouter()
const message = useMessage()
const loading = ref(false)
const data = ref<any>({})
const cameras = computed<any[]>(() => data.value.cameras || [])
const currentCode = ref('')
const current = computed(
  () => cameras.value.find((c) => c.code === currentCode.value) ?? cameras.value[0]
)
const stamp = computed(() => fmtTime(Date.now(), 'YYYY-MM-DD HH:mm'))

const statItems = computed(() => {
  const s = data.value.stats ?? {}
  return [
    {
      label: '在线摄像头',
      value: `${s.onlineCount ?? 0} / ${s.cameraCount ?? 0}`,
      sub: s.offlineCount ? `${s.offlineCount} 台离线待处理` : '全部在线',
      tone: s.offlineCount ? ('warn' as const) : ('up' as const)
    },
    { label: '今日观看人次', value: s.todayViews ?? 0, sub: `C 端观看时长合计 ${s.watchHours ?? 0}h（演示）` },
    { label: '录像留存', value: `${s.retainDays ?? 30} 天`, sub: '可延长至 90 天（付费，未开放）' },
    {
      label: '异常告警',
      value: s.alertCount ?? 0,
      sub: s.alertCount ? '摄像头离线' : '无',
      tone: s.alertCount ? ('warn' as const) : ('muted' as const)
    }
  ]
})

const load = async () => {
  loading.value = true
  try {
    data.value = await DeviceApi.getCameraOverview()
  } finally {
    loading.value = false
  }
}
onMounted(load)
let mounted = false
onActivated(() => {
  if (mounted) load()
  mounted = true
})
</script>
<style scoped>
.screen {
  position: relative;
  height: 220px;
  border-radius: 8px;
  background: linear-gradient(135deg, #2e7d32, #66bb6a);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}
.screen.off {
  background: #606266;
}
.center {
  font-size: 18px;
  font-weight: 600;
}
.live {
  position: absolute;
  left: 10px;
  top: 10px;
  background: rgba(229, 72, 77, 0.9);
  font-size: 11px;
  padding: 3px 9px;
  border-radius: 20px;
}
.live.off {
  background: rgba(0, 0, 0, 0.5);
}
.stamp {
  position: absolute;
  right: 10px;
  bottom: 10px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.85);
}
</style>
