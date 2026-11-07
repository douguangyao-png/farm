// farmer-saas: 侧栏待办角标 + 顶栏租户名/告警数。数据来自现有接口：
//   经营者：/farm/dashboard/summary 的 todos（路径与菜单路径一致）
//   平台管理员：/farm/platform/overview 的 pendingAlertCount
import { defineStore } from 'pinia'
import { getTenantId } from '@/utils/auth'
import { getDashboardSummary } from '@/api/farm/dashboard'
import { getPlatformOverview } from '@/api/farm/platform'
import request from '@/config/axios'

const PLATFORM_TENANT_ID = 1

export const useFarmNavStore = defineStore('farm-nav', () => {
  const tenantName = ref('')
  const alertCount = ref(0)
  const alertTo = ref('/')
  const badges = ref<Record<string, number>>({}) // 菜单完整路径 -> 待办数
  let timer: ReturnType<typeof setInterval> | undefined

  const refresh = async () => {
    const tid = Number(getTenantId())
    if (!tid) return
    try {
      if (!tenantName.value) {
        const list: { id: number; name: string }[] = await request.get({ url: '/system/tenant/simple-list' })
        tenantName.value = list?.find((t) => t.id === tid)?.name || ''
      }
      if (tid === PLATFORM_TENANT_ID) {
        const d: any = await getPlatformOverview()
        alertCount.value = d?.pendingAlertCount ?? 0
        alertTo.value = '/platform/risk-alert'
        badges.value = { '/platform/risk-alert': alertCount.value }
      } else {
        const d: any = await getDashboardSummary()
        const m: Record<string, number> = {}
        let total = 0
        for (const t of d?.todos || []) {
          if (t.count > 0 && t.path) {
            m[t.path] = (m[t.path] || 0) + t.count
            total += t.count
          }
        }
        badges.value = m
        alertCount.value = total
        alertTo.value = '/farm-board/dashboard'
      }
    } catch {
      // 角标取不到不影响使用
    }
  }

  const start = () => {
    refresh()
    if (!timer) timer = setInterval(refresh, 60000)
  }

  return { tenantName, alertCount, alertTo, badges, refresh, start }
})
