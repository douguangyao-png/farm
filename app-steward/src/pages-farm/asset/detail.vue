<template>
  <view class="farm-page">
    <wd-navbar title="标的详情" left-arrow placeholder safe-area-inset-top fixed @click-left="goBack" />

    <view v-if="!d" class="farm-empty">
      {{ loadError || '加载中…' }}
    </view>
    <template v-else>
      <!-- 资产 -->
      <view class="farm-card">
        <view class="farm-row">
          <text class="farm-t15">{{ d.asset.code }} {{ d.asset.name }}</text>
          <text class="farm-tag st-green">{{ ASSET_TYPE[d.asset.assetType] || d.asset.assetType }}</text>
        </view>
        <view class="farm-kv">
          <text class="k">基地</text>
          <text class="v">{{ d.asset.baseName }}</text>
        </view>
        <view class="farm-kv">
          <text class="k">作物 / 品种</text>
          <text class="v">{{ d.asset.specs.crop || d.asset.specs.breed || '—' }}</text>
        </view>
        <view v-if="d.asset.specs.areaM2" class="farm-kv">
          <text class="k">面积</text>
          <text class="v">{{ d.asset.specs.areaM2 }} ㎡</text>
        </view>
        <view v-if="d.asset.specs.count" class="farm-kv">
          <text class="k">数量</text>
          <text class="v">{{ d.asset.specs.count }}</text>
        </view>
        <view v-if="d.asset.sensorName || d.asset.cameraName" class="farm-kv">
          <text class="k">设备</text>
          <text class="v">{{ [d.asset.sensorName, d.asset.cameraName].filter(Boolean).join(' / ') }}</text>
        </view>
      </view>

      <!-- 认养 -->
      <view class="farm-hd">
        <text>认养</text>
      </view>
      <view v-if="!d.adoption" class="farm-card farm-t13">
        当前没有认养人
      </view>
      <view v-else class="farm-card">
        <view class="farm-kv">
          <text class="k">认养人</text>
          <text class="v">{{ d.member?.name || '—' }} {{ d.member?.mobile || '' }}</text>
        </view>
        <view class="farm-kv">
          <text class="k">托管方式</text>
          <text class="v">{{ d.adoption.careModeName }}</text>
        </view>
        <view class="farm-kv">
          <text class="k">认养周期</text>
          <text class="v">{{ fmtDate(d.adoption.startAt) }} ~ {{ fmtDate(d.adoption.endAt) }}</text>
        </view>
        <template v-if="d.adoption.promisedJin != null">
          <view class="farm-kv">
            <text class="k">约定交付</text>
            <text class="v">{{ d.adoption.promisedJin }} 斤，已收 {{ d.adoption.harvestedJin }} 斤</text>
          </view>
          <view class="progress">
            <view class="bar" :style="{ width: `${Math.min(100, d.adoption.harvestedJin / d.adoption.promisedJin * 100)}%` }" />
          </view>
          <view class="farm-t12 mt8">
            约定交付 {{ d.adoption.promisedJin }} 斤，不足部分按同等品质补发
          </view>
        </template>
      </view>

      <!-- 自管任务 -->
      <template v-if="d.adoption && d.adoption.careMode !== 'full'">
        <view class="farm-hd">
          <text>本周自管任务（认养人）</text>
        </view>
        <view class="farm-card">
          <view class="week">
            <view class="cell">
              <view class="num">
                {{ d.selfTaskWeek.total }}
              </view>
              <view class="farm-t12">
                共
              </view>
            </view>
            <view class="cell">
              <view class="num farm-green">
                {{ d.selfTaskWeek.done }}
              </view>
              <view class="farm-t12">
                已完成
              </view>
            </view>
            <view class="cell">
              <view class="num">
                {{ d.selfTaskWeek.entrusted }}
              </view>
              <view class="farm-t12">
                已委托
              </view>
            </view>
            <view class="cell">
              <view class="num" :class="{ 'farm-red': d.selfTaskWeek.overdue > 0 }">
                {{ d.selfTaskWeek.overdue }}
              </view>
              <view class="farm-t12">
                已逾期
              </view>
            </view>
          </view>
          <view v-if="d.selfTaskWeek.overdue > 0" class="farm-note warn">
            已逾期 {{ d.selfTaskWeek.overdue }} 项。可以提醒认养人在小程序里点「委托代办」，委托后这里会出现一张代管工单，由管家接单完成。
          </view>
        </view>
      </template>

      <!-- 环境 -->
      <view class="farm-hd">
        <text>实时环境</text>
        <text class="farm-t12">演示数据{{ d.env.sensorName ? ` · ${d.env.sensorName}` : '' }}</text>
      </view>
      <view class="farm-card">
        <EnvSnapshot :env="d.env.current" />
        <view class="chart-hd">
          <text class="farm-t12">近 24 小时</text>
          <view class="legend">
            <text class="dot soil" /><text class="farm-t12">土壤湿度 %</text>
            <text class="dot temp" /><text class="farm-t12">气温 °C</text>
            <text class="dash" /><text class="farm-t12">22% 浇水线</text>
          </view>
        </view>
        <image class="chart" :src="chartSrc" mode="widthFix" />
        <view class="farm-row farm-t12">
          <text>{{ seriesStart }}</text>
          <text>演示数据</text>
          <text>{{ seriesEnd }}</text>
        </view>
      </view>

      <!-- 最近记录 -->
      <view class="farm-hd">
        <text>最近记录</text>
        <text class="farm-t12">最近 5 条</text>
      </view>
      <view v-if="d.records.length === 0" class="farm-card farm-t13">
        还没有农事记录
      </view>
      <view v-for="r in d.records" :key="r.id" class="farm-card rec">
        <view class="farm-row">
          <text class="farm-t15">{{ r.title }}</text>
          <text class="farm-tag" :class="RECORD_STATUS[r.status]?.cls">{{ RECORD_STATUS[r.status]?.text }}</text>
        </view>
        <view class="farm-t13 mt8">
          {{ r.content }}
        </view>
        <view v-if="r.photos?.length" class="pics">
          <image v-for="(p, i) in r.photos.slice(0, 3)" :key="i" :src="p" mode="aspectFill" class="pic" />
        </view>
        <view class="farm-t12 mt8">
          {{ r.stewardName }} · {{ fmtTime(r.createTime) }}
        </view>
      </view>
    </template>
    <DemoBadge />
  </view>
</template>

<script lang="ts" setup>
import type { StewardAssetDetail } from '@/api/farm/steward'
import { computed, ref } from 'vue'
import { getStewardAsset } from '@/api/farm/steward'
import { navigateBackPlus } from '@/utils'
import DemoBadge from '../components/demo-badge.vue'
import EnvSnapshot from '../components/env-snapshot.vue'
import { fmtDate, fmtTime, RECORD_STATUS } from '../components/farm-utils'

definePage({
  style: {
    navigationBarTitleText: '标的详情',
    navigationStyle: 'custom',
  },
})

const ASSET_TYPE: Record<string, string> = { plot: '地块', animal: '畜禽', tree: '果树' }

const d = ref<StewardAssetDetail>()
const loadError = ref('')

onLoad(async (o) => {
  const id = Number(o?.id || 0)
  try {
    d.value = await getStewardAsset(id)
  } catch (e: any) {
    loadError.value = e?.msg || '加载失败'
  }
})

const seriesStart = computed(() => fmtTime(d.value?.env.series[0]?.ts).slice(6))
const seriesEnd = computed(() => fmtTime(d.value?.env.series[d.value.env.series.length - 1]?.ts).slice(6))

/** 24 小时折线图：拼成 SVG data URL，用 image 显示（各端都能用） */
const chartSrc = computed(() => {
  const s = d.value?.env.series || []
  const W = 600
  const H = 200
  const pad = 10
  const vals = s.flatMap(p => [p.soilMoisture, p.airTemp]).concat([22])
  const min = Math.floor(Math.min(...vals) - 2)
  const max = Math.ceil(Math.max(...vals) + 2)
  const x = (i: number) => pad + (i * (W - 2 * pad)) / Math.max(1, s.length - 1)
  const y = (v: number) => H - pad - ((v - min) * (H - 2 * pad)) / Math.max(1, max - min)
  const line = (key: 'soilMoisture' | 'airTemp') => s.map((p, i) => `${x(i).toFixed(1)},${y(p[key]).toFixed(1)}`).join(' ')
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">`
    + `<rect width="${W}" height="${H}" fill="#fafbfc"/>`
    + `<line x1="${pad}" x2="${W - pad}" y1="${y(22)}" y2="${y(22)}" stroke="#e5484d" stroke-width="1.5" stroke-dasharray="6 5"/>`
    + `<polyline fill="none" stroke="#ff8f1f" stroke-width="2.5" points="${line('airTemp')}"/>`
    + `<polyline fill="none" stroke="#2b6cb8" stroke-width="3" points="${line('soilMoisture')}"/>`
    + `<text x="${W - pad}" y="18" font-size="16" fill="#98a0aa" text-anchor="end">演示数据</text>`
    + `</svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
})

function goBack() {
  navigateBackPlus('/pages/index/index')
}
</script>

<style lang="scss" scoped>
@import '../components/farm.scss';

.mt8 {
  margin-top: 8rpx;
}
.progress {
  height: 14rpx;
  margin-top: 8rpx;
  overflow: hidden;
  background: #eef0f3;
  border-radius: 999rpx;
  .bar {
    height: 100%;
    background: $g;
  }
}
.week {
  display: flex;
  .cell {
    flex: 1;
    text-align: center;
    .num {
      font-size: 36rpx;
      font-weight: 700;
    }
  }
}
.week + .farm-note {
  margin: 16rpx 0 0;
}
.chart-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 24rpx;
  .legend {
    display: flex;
    align-items: center;
    gap: 8rpx;
  }
  .dot {
    display: inline-block;
    width: 16rpx;
    height: 16rpx;
    margin-left: 8rpx;
    border-radius: 50%;
    &.soil {
      background: $b;
    }
    &.temp {
      background: $o;
    }
  }
  .dash {
    display: inline-block;
    width: 24rpx;
    margin-left: 8rpx;
    border-top: 3rpx dashed $r;
  }
}
.chart {
  width: 100%;
  margin: 12rpx 0 4rpx;
  border-radius: 10rpx;
}
.pics {
  display: flex;
  gap: 12rpx;
  margin-top: 12rpx;
  .pic {
    width: 160rpx;
    height: 160rpx;
    border-radius: 10rpx;
  }
}
</style>
