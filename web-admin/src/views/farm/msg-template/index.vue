<!-- 权限串：farm:msgtemplate:query / create / update（启停、测试发送归在 update）。测试发送不真实下发，只返回代入示例变量后的预览 -->
<template>
  <div class="flex items-start justify-between mb-12px">
    <div>
      <div class="text-18px font-600">消息模板</div>
      <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
        微信订阅消息 + 短信 + App 推送；触发时机由业务事件驱动（演示版只维护模板，不真实下发）
      </div>
    </div>
    <el-button type="primary" @click="openForm()">
      <Icon icon="ep:plus" class="mr-5px" />新建模板
    </el-button>
  </div>

  <ContentWrap>
    <el-form :model="queryParams" :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="模板名称">
        <el-input v-model="queryParams.name" clearable class="!w-200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" clearable placeholder="全部" class="!w-120px">
          <el-option label="启用中" :value="0" />
          <el-option label="已停用" :value="1" />
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
      <el-table-column label="模板名称" prop="name" width="140">
        <template #default="{ row }"><b>{{ row.name }}</b></template>
      </el-table-column>
      <el-table-column label="触发时机" prop="trigger" width="170" />
      <el-table-column label="渠道" width="190">
        <template #default="{ row }">
          <el-tag v-for="c in row.channels" :key="c" size="small" effect="plain" class="mr-4px">{{ c }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="示例内容" prop="content" min-width="280" show-overflow-tooltip />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '启用中' : '已停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)">编辑</el-button>
          <el-button link type="primary" :disabled="row.status !== 0" @click="testSend(row)">测试发送</el-button>
          <el-button link :type="row.status === 0 ? 'danger' : 'primary'" @click="toggleStatus(row)">
            {{ row.status === 0 ? '停用' : '启用' }}
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

  <el-alert
    type="warning"
    :closable="false"
    show-icon
    title="所有外发文案同样经过收益承诺类词语检测，命中风险的模板无法保存。"
  />

  <Dialog v-model="dialogVisible" :title="form.id ? '编辑模板' : '新建模板'" width="620px">
    <el-alert
      v-if="banned"
      class="mb-12px"
      type="error"
      show-icon
      :closable="false"
      :title="`文案含有违禁词：${banned.words.join('、')}`"
      :description="banned.suggestion"
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" v-loading="formLoading">
      <el-form-item label="模板名称" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="触发时机" prop="trigger">
        <el-input v-model="form.trigger" placeholder="如：订单支付完成、到期前 30/15/3 天" />
      </el-form-item>
      <el-form-item label="发送渠道" prop="channels">
        <el-checkbox-group v-model="form.channels">
          <el-checkbox v-for="c in channelOptions" :key="c" :value="c">{{ c }}</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="模板内容" prop="content">
        <el-input v-model="form.content" type="textarea" :rows="4" />
        <div class="text-12px text-[var(--el-text-color-secondary)]">
          变量用花括号，如 {地块编号}、{到期日}
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submitForm">保 存</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessageBox } from 'element-plus'
import * as MsgApi from '@/api/farm/msgTemplate'
import { BANNED_WORDS_CODE } from '@/api/farm/product'

defineOptions({ name: 'FarmMsgTemplate' })

const message = useMessage()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const channelOptions = ['订阅消息', '短信', 'App 推送']
const queryParams = reactive<any>({ pageNo: 1, pageSize: 10, name: undefined, status: undefined })

const getList = async () => {
  loading.value = true
  try {
    const res = await MsgApi.getMsgTemplatePage(queryParams)
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
  queryParams.name = undefined
  queryParams.status = undefined
  handleQuery()
}

const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const form = ref<any>({})
const banned = ref<{ words: string[]; suggestion: string } | null>(null)
const rules: FormRules = {
  name: [{ required: true, message: '请填写模板名称', trigger: 'blur' }],
  trigger: [{ required: true, message: '请填写触发时机', trigger: 'blur' }],
  channels: [{ type: 'array', required: true, min: 1, message: '至少选一个渠道', trigger: 'change' }],
  content: [{ required: true, message: '请填写模板内容', trigger: 'blur' }]
}
const openForm = (row?: any) => {
  banned.value = null
  form.value = row
    ? { id: row.id, name: row.name, trigger: row.trigger, channels: [...row.channels], content: row.content }
    : { name: '', trigger: '', channels: ['订阅消息'], content: '' }
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}
const submitForm = async () => {
  if (!(await formRef.value?.validate().catch(() => false))) return
  formLoading.value = true
  banned.value = null
  try {
    if (form.value.id) await MsgApi.updateMsgTemplate(form.value)
    else await MsgApi.createMsgTemplate(form.value)
    message.success('已保存')
    dialogVisible.value = false
    getList()
  } catch (e: any) {
    if (e?.code === BANNED_WORDS_CODE && e.data) {
      banned.value = { words: e.data.words || [], suggestion: e.data.suggestion || '' }
    } else {
      message.error(e?.msg || (typeof e === 'string' ? e : e?.message) || '保存失败')
    }
  } finally {
    formLoading.value = false
  }
}

const toggleStatus = async (row: any) => {
  await MsgApi.updateMsgTemplateStatus(row.id, row.status === 0 ? 1 : 0)
  message.success(row.status === 0 ? '已停用' : '已启用')
  getList()
}
const testSend = async (row: any) => {
  const res = await MsgApi.testSendMsgTemplate(row.id)
  ElMessageBox.alert(
    `<div class="mb-8px">演示环境不会真实下发，以下是代入示例变量后的预览（渠道：${res.channels.join('、')}）：</div><div style="padding:10px;background:#f5f7fa;border-radius:6px">${escapeHtml(res.preview)}</div>`,
    '测试发送',
    { dangerouslyUseHTMLString: true, confirmButtonText: '知道了' }
  )
}
const escapeHtml = (s: string) =>
  s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!)

onMounted(getList)
let mounted = false
onActivated(() => {
  if (mounted) getList()
  mounted = true
})
</script>
