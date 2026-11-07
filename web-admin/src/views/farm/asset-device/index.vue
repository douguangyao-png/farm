<!-- 权限串：farm:assetdevice:query / create / update。设备由资产上的摄像头、传感器名称派生；状态、上报时间、固件为演示数据，未接入真实 MQTT -->
<template>
  <div class="flex items-start justify-between mb-12px">
    <div>
      <div class="text-18px font-600">
        IoT 设备与接入
        <el-tag type="warning" size="small" effect="plain" class="ml-6px">状态为演示数据</el-tag>
      </div>
      <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
        设备清单取自地块与畜禽上绑定的摄像头、传感器；在线状态、最近上报、固件版本为演示值
      </div>
    </div>
    <div class="flex gap-8px">
      <el-button @click="message.info('演示版暂未开放')">查看接入文档</el-button>
      <el-button type="primary" @click="openCreate">
        <Icon icon="ep:plus" class="mr-5px" />添加设备
      </el-button>
    </div>
  </div>

  <StatCards :items="statItems" />

  <ContentWrap>
    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="设备编号">
        <el-input v-model="queryParams.code" placeholder="CAM-..." clearable class="!w-170px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="queryParams.kind" clearable placeholder="全部" class="!w-130px">
          <el-option label="摄像头" value="camera" />
          <el-option label="传感器" value="sensor" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" clearable placeholder="全部" class="!w-110px">
          <el-option label="在线" value="online" />
          <el-option label="离线" value="offline" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="设备编号" prop="code" width="110">
        <template #default="{ row }"><b>{{ row.code }}</b></template>
      </el-table-column>
      <el-table-column label="类型" prop="typeName" width="110" />
      <el-table-column label="安装位置" prop="position" min-width="150" />
      <el-table-column label="绑定标的" prop="bindText" min-width="170" />
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'online' ? 'success' : 'danger'">{{ row.statusName }}</el-tag>
          <div v-if="row.dispatched" class="text-12px text-[var(--el-color-warning)]">已派单</div>
        </template>
      </el-table-column>
      <el-table-column label="最后上报" width="150">
        <template #default="{ row }">{{ fmtTime(row.lastReportTime, 'MM-DD HH:mm') }}</template>
      </el-table-column>
      <el-table-column label="固件" prop="firmware" width="90" />
      <el-table-column label="操作" width="170" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openConfig(row)">配置</el-button>
          <el-button link type="primary" @click="message.info('演示版暂未开放录像回放')">回放</el-button>
          <el-button
            v-if="row.status === 'offline' && !row.dispatched"
            link
            type="danger"
            @click="handleDispatch(row)"
            >派单</el-button
          >
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
      <ContentWrap>
        <div class="font-600 mb-8px">接入方式</div>
        <div v-for="x in ACCESS_ROWS" :key="x.k" class="flex mb-8px text-13px">
          <span class="w-110px text-[var(--el-text-color-secondary)] flex-shrink-0">{{ x.k }}</span>
          <span>{{ x.v }}</span>
        </div>
        <div class="text-12px text-[var(--el-text-color-secondary)]">
          以上为接入方案说明，本期不接入真实设备。
        </div>
      </ContentWrap>
    </el-col>
    <el-col :xs="24" :lg="12">
      <ContentWrap>
        <div class="font-600 mb-8px">异常告警规则</div>
        <div v-for="x in ALARM_RULES" :key="x.k" class="flex mb-8px text-13px">
          <span class="w-190px text-[var(--el-text-color-secondary)] flex-shrink-0">{{ x.k }}</span>
          <span>{{ x.v }}</span>
        </div>
        <el-alert
          type="success"
          :closable="false"
          show-icon
          title="「画面静止检测」是防止摆拍式透明度的内置机制。通知文案可在「系统设置 - 消息模板」中配置。"
        />
      </ContentWrap>
    </el-col>
  </el-row>

  <Dialog v-model="createVisible" title="添加设备" width="520px">
    <el-form ref="createRef" :model="createForm" :rules="createRules" label-width="90px" v-loading="formLoading">
      <el-form-item label="设备类型" prop="typeName">
        <el-select v-model="createForm.typeName" class="!w-full" @change="onTypeChange">
          <el-option v-for="t in DEVICE_TYPES" :key="t" :label="t" :value="t" />
        </el-select>
      </el-form-item>
      <el-form-item label="设备名称" prop="name"><el-input v-model="createForm.name" placeholder="如：B 区东侧枪机" /></el-form-item>
      <el-form-item label="安装位置"><el-input v-model="createForm.position" placeholder="不填同设备名称" /></el-form-item>
      <el-form-item label="绑定标的"><el-input v-model="createForm.bindText" placeholder="如：B-01 ~ B-10；不填为未绑定" /></el-form-item>
      <div class="pl-90px text-12px text-[var(--el-text-color-secondary)]">
        演示版只登记设备台账，不会真实连接设备；与地块的绑定关系请到「地块与畜禽」维护。
      </div>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submitCreate">确 定</el-button>
      <el-button @click="createVisible = false">取 消</el-button>
    </template>
  </Dialog>

  <Dialog v-model="configVisible" :title="`配置设备 ${configForm.code}`" width="480px">
    <el-form :model="configForm" label-width="90px" v-loading="formLoading">
      <el-form-item label="安装位置"><el-input v-model="configForm.position" /></el-form-item>
      <el-form-item label="固件版本"><el-input v-model="configForm.firmware" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submitConfig">保 存</el-button>
      <el-button @click="configVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus'
import * as DeviceApi from '@/api/farm/device'
import { fmtTime } from '../constants'
import StatCards from '../fulfill/StatCards.vue'
import { ACCESS_ROWS, ALARM_RULES, DEVICE_TYPES } from './constants'

defineOptions({ name: 'FarmAssetDevice' })

const message = useMessage()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const stats = ref<any>({})
const queryParams = reactive<any>({ pageNo: 1, pageSize: 10, code: undefined, kind: undefined, status: undefined })

const statItems = computed(() => {
  const s = stats.value
  return [
    { label: '设备总数', value: s.total ?? 0, sub: `摄像头 ${s.cameraCount ?? 0} / 传感器 ${s.sensorCount ?? 0}` },
    {
      label: '在线',
      value: s.onlineCount ?? 0,
      sub: s.offlineCount ? `离线 ${s.offlineCount} 台待处理` : '全部在线',
      tone: s.offlineCount ? ('warn' as const) : ('up' as const)
    },
    { label: '今日数据上报', value: (s.todayReports ?? 0).toLocaleString(), sub: '频次 5 分钟/次（演示）' },
    {
      label: '云端录像',
      value: `${s.retainDays ?? 30} 天`,
      sub: `留存可用存储 ${s.storageUsedGb ?? 0}/${s.storageTotalGb ?? 0} GB（演示）`
    }
  ]
})

const getList = async () => {
  loading.value = true
  try {
    const res = await DeviceApi.getDevicePage(queryParams)
    list.value = res.list
    total.value = res.total
    stats.value = res.stats ?? {}
  } finally {
    loading.value = false
  }
}
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}
const resetQuery = () => {
  queryParams.code = undefined
  queryParams.kind = undefined
  queryParams.status = undefined
  handleQuery()
}

const formLoading = ref(false)
const createVisible = ref(false)
const createRef = ref<FormInstance>()
const createForm = ref<any>({})
const createRules: FormRules = {
  typeName: [{ required: true, message: '请选择设备类型', trigger: 'change' }],
  name: [{ required: true, message: '请填写设备名称', trigger: 'blur' }]
}
const openCreate = () => {
  createForm.value = { typeName: '摄像头', kind: 'camera', name: '', position: '', bindText: '' }
  createVisible.value = true
  nextTick(() => createRef.value?.clearValidate())
}
const onTypeChange = (v: string) => {
  createForm.value.kind = v === '摄像头' ? 'camera' : 'sensor'
}
const submitCreate = async () => {
  if (!(await createRef.value?.validate().catch(() => false))) return
  formLoading.value = true
  try {
    const code = await DeviceApi.createDevice(createForm.value)
    message.success(`设备已登记，编号 ${code}`)
    createVisible.value = false
    getList()
  } finally {
    formLoading.value = false
  }
}

const configVisible = ref(false)
const configForm = ref<any>({})
const openConfig = (row: any) => {
  configForm.value = { code: row.code, position: row.position, firmware: row.firmware }
  configVisible.value = true
}
const submitConfig = async () => {
  formLoading.value = true
  try {
    await DeviceApi.updateDevice(configForm.value)
    message.success('已保存')
    configVisible.value = false
    getList()
  } finally {
    formLoading.value = false
  }
}

const handleDispatch = async (row: any) => {
  try {
    await message.confirm(`设备 ${row.code}（${row.position}）离线，确认派单给运维处理？`)
  } catch {
    return
  }
  await DeviceApi.dispatchDevice(row.code)
  message.success('已派单')
  getList()
}

onMounted(getList)
let mounted = false
onActivated(() => {
  if (mounted) getList()
  mounted = true
})
</script>
