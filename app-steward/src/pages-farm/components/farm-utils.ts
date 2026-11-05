import type { TaskSource } from '@/api/farm/steward'

/** 是否显示「演示数据」角标（R-7） */
export const showDemoBadge = import.meta.env.VITE_MOCK_BADGE === 'true'

export const SOURCE_META: Record<TaskSource, { text: string, cls: string }> = {
  iot: { text: '墒情触发', cls: 'tag-iot' },
  calendar: { text: '作物日历', cls: 'tag-cal' },
  manual: { text: '手动', cls: 'tag-manual' },
  service: { text: '代管工单', cls: 'tag-service' },
}

export const SERVICE_ORDER_STATUS: Record<number, { text: string, cls: string }> = {
  0: { text: '待接单', cls: 'st-red' },
  10: { text: '已接单', cls: 'st-orange' },
  20: { text: '已完成', cls: 'st-green' },
  30: { text: '已取消', cls: 'st-gray' },
}

export const RECORD_STATUS: Record<number, { text: string, cls: string }> = {
  0: { text: '待审核', cls: 'st-orange' },
  10: { text: '已通过', cls: 'st-green' },
  20: { text: '已驳回', cls: 'st-red' },
}

const pad = (n: number) => String(n).padStart(2, '0')

/** 毫秒时间戳 → MM-DD HH:mm */
export function fmtTime(ms?: number | null) {
  if (!ms) {
    return '-'
  }
  const d = new Date(ms)
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 毫秒时间戳 → YYYY-MM-DD */
export function fmtDate(ms?: number | null) {
  if (!ms) {
    return '-'
  }
  const d = new Date(ms)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 截止时间的口语说法：今天 18:00 / 明天 10:00 / 已逾期 3 小时 */
export function fmtDeadline(ms: number) {
  const now = Date.now()
  const d = new Date(ms)
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  if (ms < now) {
    const h = Math.floor((now - ms) / 3600_000)
    return h >= 24 ? `已逾期 ${Math.floor(h / 24)} 天` : `已逾期 ${Math.max(h, 1)} 小时`
  }
  const startToday = new Date(new Date().toDateString()).getTime()
  const day = Math.floor((ms - startToday) / 86400_000)
  if (day === 0) {
    return `今天 ${hm} 前`
  }
  if (day === 1) {
    return `明天 ${hm} 前`
  }
  return `${fmtTime(ms)} 前`
}

/** 金额：分 → ¥45.00 */
export function fmtMoney(fen?: number | null) {
  return `¥${((fen ?? 0) / 100).toFixed(2)}`
}

/** 问候语 */
export function greeting() {
  const h = new Date().getHours()
  if (h < 11) {
    return '早上好'
  }
  if (h < 13) {
    return '中午好'
  }
  if (h < 18) {
    return '下午好'
  }
  return '晚上好'
}

/**
 * 把选中的图片压缩成 JPEG data URL（长边 ≤ 1280px）
 * H5 用 canvas；其他端演示阶段不用，原样返回路径
 */
export function compressImage(src: string, maxSide = 1280, quality = 0.8): Promise<string> {
  // #ifdef H5
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => {
      const scale = Math.min(1, maxSide / Math.max(img.width, img.height))
      const w = Math.round(img.width * scale)
      const h = Math.round(img.height * scale)
      const canvas = document.createElement('canvas')
      canvas.width = w
      canvas.height = h
      const ctx = canvas.getContext('2d')
      if (!ctx) {
        reject(new Error('浏览器不支持 canvas'))
        return
      }
      ctx.fillStyle = '#fff'
      ctx.fillRect(0, 0, w, h)
      ctx.drawImage(img, 0, 0, w, h)
      resolve(canvas.toDataURL('image/jpeg', quality))
    }
    img.onerror = () => reject(new Error('图片读取失败'))
    img.src = src
  })
  // #endif
  // #ifndef H5
  // eslint-disable-next-line no-unreachable
  return Promise.resolve(src)
  // #endif
}

/** 生成一张演示用的现场照片（仅演示模式下的「使用演示照片」按钮用） */
export function makeDemoPhoto(label: string): string {
  // #ifdef H5
  const canvas = document.createElement('canvas')
  canvas.width = 960
  canvas.height = 720
  const ctx = canvas.getContext('2d')!
  const g = ctx.createLinearGradient(0, 0, 960, 720)
  g.addColorStop(0, '#5fb878')
  g.addColorStop(1, '#2e7d4f')
  ctx.fillStyle = g
  ctx.fillRect(0, 0, 960, 720)
  ctx.fillStyle = 'rgba(255,255,255,0.18)'
  for (let i = 0; i < 8; i++) {
    ctx.fillRect(0, 380 + i * 42, 960, 18)
  }
  ctx.fillStyle = '#fff'
  ctx.textAlign = 'center'
  ctx.font = 'bold 64px sans-serif'
  ctx.fillText(label, 480, 300)
  ctx.font = '32px sans-serif'
  ctx.fillText(`演示照片 · ${fmtDate(Date.now())} ${fmtTime(Date.now()).slice(6)}`, 480, 360)
  return canvas.toDataURL('image/jpeg', 0.8)
  // #endif
  // #ifndef H5
  // eslint-disable-next-line no-unreachable
  return ''
  // #endif
}

/** 定位：5 秒内拿不到就返回 null（调用方按基地坐标处理） */
export function tryGetLocation(timeoutMs = 5000): Promise<{ lng: number, lat: number } | null> {
  return new Promise((resolve) => {
    let done = false
    const timer = setTimeout(() => {
      if (!done) {
        done = true
        resolve(null)
      }
    }, timeoutMs)
    try {
      uni.getLocation({
        type: 'wgs84',
        success: (res) => {
          if (!done) {
            done = true
            clearTimeout(timer)
            resolve({ lng: res.longitude, lat: res.latitude })
          }
        },
        fail: () => {
          if (!done) {
            done = true
            clearTimeout(timer)
            resolve(null)
          }
        },
      })
    } catch {
      done = true
      clearTimeout(timer)
      resolve(null)
    }
  })
}
