<template>
  <div v-loading="loading">
    <template v-if="order">
      <ContentWrap>
        <div class="flex items-start justify-between">
          <div>
            <div class="text-18px font-600">
              订单详情 · {{ order.no }}
              <el-tag class="ml-8px" :type="enumOf(ORDER_STATUS, order.status).type">
                {{ enumLabel(ORDER_STATUS, order.status) }}
              </el-tag>
            </div>
            <div class="text-13px text-[var(--el-text-color-secondary)] mt-4px">
              下单时间 {{ fmtTime(order.createTime) }}
            </div>
          </div>
          <div>
            <el-button @click="back">返回列表</el-button>
            <el-button @click="notImp"><Icon icon="ep:chat-dot-round" class="mr-5px" />联系客户</el-button>
            <el-button type="primary" @click="go('/farm-order/shipment')">
              <Icon icon="ep:van" class="mr-5px" />安排配送
            </el-button>
          </div>
        </div>
      </ContentWrap>

      <el-row :gutter="12">
        <el-col :xs="24" :lg="16">
          <ContentWrap>
            <el-descriptions title="订单信息" :column="2" border>
              <el-descriptions-item label="认养商品">{{ order.productTitle }}</el-descriptions-item>
              <el-descriptions-item label="托管方式">
                <el-tag :type="enumOf(CARE_MODES, order.careMode).type" effect="plain">
                  {{ enumLabel(CARE_MODES, order.careMode) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="来源渠道">
                {{ enumLabel(ORDER_CHANNELS, order.channel) }}
              </el-descriptions-item>
              <el-descriptions-item label="交付方式">
                {{ enumLabel(ORDER_DELIVER_WAYS, order.deliverWay) }}
              </el-descriptions-item>
              <el-descriptions-item label="所选套餐">{{ order.packageName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="优惠券">
                <template v-if="order.couponName">
                  {{ order.couponName }}
                  <span class="text-[var(--el-color-danger)] ml-4px">
                    -{{ formatMoney(order.couponAmount || 0) }}
                  </span>
                </template>
                <template v-else>未使用</template>
              </el-descriptions-item>
              <el-descriptions-item v-if="hasGps" label="下单位置" :span="2">
                {{ order.gpsText || '-' }}
                <span class="ml-6px text-12px text-[var(--el-text-color-secondary)]">
                  （{{ order.gpsLng }}, {{ order.gpsLat }}）
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="实付金额">
                <b class="text-[var(--el-color-danger)]">{{ formatMoney(order.amount) }}</b>
              </el-descriptions-item>
              <el-descriptions-item label="支付方式">
                <template v-if="order.payTime">
                  {{ order.payMethod }} · 交易号 {{ tradeNo }}
                  <el-tag size="small" type="info" class="ml-4px">演示数据</el-tag>
                </template>
                <template v-else>未支付</template>
              </el-descriptions-item>
              <el-descriptions-item label="标的编号">
                <template v-if="ad">
                  <b>{{ ad.assetCode }}</b>
                  <span class="ml-4px">{{ ad.baseName }}</span>
                </template>
                <template v-else>待分配（支付后自动分配）</template>
              </el-descriptions-item>
              <el-descriptions-item label="专属管家">
                <template v-if="order.steward">
                  {{ order.steward.name }} · {{ order.steward.mobile }}
                </template>
                <template v-else>-</template>
              </el-descriptions-item>
              <el-descriptions-item label="电子合同" :span="2">
                <el-tag v-if="signed" type="success" size="small">已签署</el-tag>
                <el-tag v-else type="warning" size="small">待签署</el-tag>
                <span class="ml-6px">{{ order.contract?.no || '-' }}</span>
                <span class="ml-6px">{{ order.contract?.title }}</span>
                <span v-if="signed" class="ml-6px"> · 签署于 {{ fmtTime(order.contract.signedAt) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="溯源码">
                <template v-if="ad">
                  {{ traceCode }}
                  <el-tag size="small" type="info" class="ml-4px">演示数据</el-tag>
                </template>
                <template v-else>-</template>
              </el-descriptions-item>
              <el-descriptions-item label="收货人">
                <template v-if="order.receiverName">
                  {{ order.receiverName }} {{ order.receiverMobile }} {{ order.address }}
                </template>
                <template v-else>默认用认养人资料</template>
              </el-descriptions-item>
              <el-descriptions-item label="认养周期" :span="2">
                <template v-if="ad">{{ fmtDay(ad.startAt) }} 至 {{ fmtDay(ad.endAt) }}</template>
                <template v-else>-</template>
              </el-descriptions-item>
              <el-descriptions-item label="交付进度" :span="2">
                <template v-if="!ad">订单还没有分配地块（未支付或已取消）</template>
                <template v-else-if="ad.promisedJin">
                  <div class="text-13px mb-4px">
                    约定交付 {{ ad.promisedJin }} 斤，不足部分按同等品质补发；已收
                    {{ ad.harvestedJin }} 斤
                  </div>
                  <el-progress :percentage="deliverPercent" :stroke-width="14" />
                </template>
                <template v-else>按实际收成交付，已收 {{ ad.harvestedJin }} 斤</template>
              </el-descriptions-item>
            </el-descriptions>
          </ContentWrap>

          <ContentWrap title="履约时间轴">
            <el-timeline>
              <el-timeline-item
                v-for="(n, i) in timeline"
                :key="i"
                :timestamp="n.desc"
                placement="top"
                :type="n.done ? 'success' : 'info'"
                :hollow="!n.done"
              >
                <span :class="n.done ? 'font-600' : 'text-[var(--el-text-color-secondary)]'">
                  {{ n.title }}
                </span>
              </el-timeline-item>
            </el-timeline>
          </ContentWrap>

          <ContentWrap title="最近的农事记录">
            <el-empty
              v-if="!order.records?.length"
              description="还没有已审核的农事记录"
              :image-size="60"
            />
            <el-timeline v-else>
              <el-timeline-item
                v-for="r in order.records"
                :key="r.id"
                :timestamp="fmtTime(r.createTime)"
                placement="top"
              >
                <div class="font-600">
                  {{ r.title }}
                  <span class="ml-6px text-12px font-400 text-[var(--el-text-color-secondary)]">
                    {{ r.stewardName }}
                  </span>
                </div>
                <div class="text-13px my-4px">{{ r.content }}</div>
                <div class="flex gap-6px">
                  <el-image
                    v-for="(p, i) in r.photos"
                    :key="i"
                    :src="p"
                    :preview-src-list="r.photos"
                    :initial-index="Number(i)"
                    preview-teleported
                    fit="cover"
                    class="w-80px h-80px rounded-4px"
                  />
                </div>
              </el-timeline-item>
            </el-timeline>
          </ContentWrap>

          <ContentWrap title="配送">
            <el-table :data="order.shipments || []" empty-text="还没有配送">
              <el-table-column label="配送单号" prop="no" width="160" />
              <el-table-column label="重量" width="90">
                <template #default="{ row }">{{ row.weightJin }} 斤</template>
              </el-table-column>
              <el-table-column label="承运" min-width="200">
                <template #default="{ row }">{{ row.carrier }} {{ row.logisticsNo }}</template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="enumOf(SHIPMENT_STATUS, row.status).type">
                    {{ enumLabel(SHIPMENT_STATUS, row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="最新轨迹" min-width="240">
                <template #default="{ row }">
                  <template v-if="row.tracks?.length">
                    {{ row.tracks[row.tracks.length - 1].content }}
                    <span class="text-12px text-[var(--el-text-color-secondary)]">
                      {{ fmtTime(row.tracks[row.tracks.length - 1].time) }}
                    </span>
                  </template>
                </template>
              </el-table-column>
            </el-table>
          </ContentWrap>
        </el-col>

        <el-col :xs="24" :lg="8">
          <ContentWrap title="客户信息">
            <div class="flex items-center gap-10px">
              <el-avatar :size="38">{{ (order.customer?.name || order.memberName || '客').slice(0, 1) }}</el-avatar>
              <div>
                <div class="font-600">{{ order.customer?.name || order.memberName }}</div>
                <div class="text-12px text-[var(--el-text-color-secondary)]">
                  {{ order.customer?.mobile || order.memberMobile }}
                </div>
              </div>
            </div>
            <div v-if="order.customerTags?.length" class="mt-10px flex flex-wrap gap-6px">
              <el-tag v-for="t in order.customerTags" :key="t" size="small" effect="plain">{{ t }}</el-tag>
            </div>
            <el-descriptions :column="1" class="mt-12px" border>
              <el-descriptions-item label="累计认养">
                {{ order.customer?.orderCount ?? 0 }} 单
              </el-descriptions-item>
              <el-descriptions-item label="累计消费">
                <b>{{ formatMoney(order.customer?.totalAmount ?? 0) }}</b>
              </el-descriptions-item>
            </el-descriptions>
          </ContentWrap>

          <ContentWrap title="资金与分账（演示数据）">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="订单金额">{{ formatMoney(order.amount) }}</el-descriptions-item>
              <el-descriptions-item label="平台服务费（3%）">
                -{{ formatMoney(fee) }}
              </el-descriptions-item>
              <el-descriptions-item label="经营者应收">
                <b>{{ formatMoney(order.amount - fee) }}</b>
              </el-descriptions-item>
              <el-descriptions-item label="结算状态">
                <el-tag size="small" :type="settleState === 'T+1 已结算' ? 'success' : 'info'">{{
                  settleState
                }}</el-tag>
              </el-descriptions-item>
            </el-descriptions>
            <div class="text-12px text-[var(--el-text-color-secondary)] mt-8px">
              结算由支付机构自动执行，演示阶段不真实分账。
            </div>
          </ContentWrap>

          <ContentWrap title="快捷操作">
            <div class="flex flex-wrap gap-8px">
              <el-button type="primary" size="small" @click="notImp">查看实时监控</el-button>
              <el-button size="small" @click="go('/farm-work/record')">发布农事记录</el-button>
              <el-button size="small" @click="go('/farm-order/harvest')">录入收成</el-button>
              <el-button size="small" @click="go('/farm-order/shipment')">安排配送</el-button>
              <el-button size="small" @click="notImp">处理退款</el-button>
              <el-button size="small" @click="notImp">打印标的铭牌</el-button>
            </div>
          </ContentWrap>
        </el-col>
      </el-row>
    </template>
  </div>
</template>
<script lang="ts" setup>
import * as OrderApi from '@/api/farm/order'
import {
  CARE_MODES,
  ORDER_CHANNELS,
  ORDER_DELIVER_WAYS,
  ORDER_STATUS,
  SHIPMENT_STATUS,
  enumLabel,
  enumOf,
  fmtDay,
  fmtTime,
  formatMoney
} from '../../constants'

defineOptions({ name: 'FarmOrderDetail' })

const route = useRoute()
const router = useRouter()
const message = useMessage()
const loading = ref(false)
const order = ref<any>()
const ad = computed(() => order.value?.adoption)
const hasGps = computed(() => !!(order.value?.gpsLng && order.value?.gpsLat))
const signed = computed(() => !!(order.value?.contract?.agreed && order.value?.contract?.signedAt))
const deliverPercent = computed(() => {
  const a = ad.value
  if (!a?.promisedJin) return 0
  return Math.min(100, Math.round((a.harvestedJin / a.promisedJin) * 100))
})

// ---- 演示数据：交易号、溯源码、服务费 3%、结算状态 ----
const digits = computed(() => String(order.value?.no ?? '').replace(/\D/g, ''))
const tradeNo = computed(() => 'T' + digits.value)
const traceCode = computed(() => 'SF-' + digits.value)
const fee = computed(() => Math.round((order.value?.amount ?? 0) * 0.03))
const settleState = computed(() => {
  const pt = order.value?.payTime
  if (!pt) return '未支付'
  return Date.now() - pt >= 86400_000 ? 'T+1 已结算' : 'T+1 待结算'
})

/** 履约时间轴：由订单、认养、配送的时间拼出来，没发生的节点灰显 */
const timeline = computed(() => {
  const o = order.value
  if (!o) return []
  const now = Date.now()
  const a = ad.value
  const items: { title: string; desc: string; done: boolean }[] = [
    {
      title: signed.value ? '订单创建并签署电子合同' : '订单创建，等待签署电子合同',
      desc: `${fmtTime(o.createTime)} · 客户`,
      done: true
    },
    {
      title: '支付成功，款项进入对公监管账户',
      desc: o.payTime ? `${fmtTime(o.payTime)} · ${o.payMethod}` : '尚未支付',
      done: !!o.payTime
    },
    {
      title: a ? `系统自动分配标的 ${a.assetCode} 并生成溯源码` : '支付后系统自动分配标的并生成溯源码',
      desc: a ? `${fmtTime(o.payTime)} · 系统` : '待分配',
      done: !!a
    }
  ]
  const ships: any[] = [...(o.shipments || [])].sort((x, y) => x.createTime - y.createTime)
  if (ships.length) {
    ships.forEach((s, i) => {
      items.push({
        title: `第 ${i + 1} 次配送 · ${s.weightJin} 斤`,
        desc: `${fmtTime(s.createTime)} · ${enumLabel(SHIPMENT_STATUS, s.status)}`,
        done: s.status === 20
      })
    })
  } else {
    items.push({ title: '第 1 次配送', desc: '尚未安排', done: false })
  }
  items.push({
    title: '认养到期并推送续费提醒',
    desc: a ? fmtDay(a.endAt) : '-',
    done: !!a && a.endAt < now
  })
  return items
})

const notImp = () => message.info('演示环境暂不支持该操作')
const go = (path: string) => router.push(path)
const back = () => router.push('/farm-order/order')

const load = async () => {
  const id = Number(route.params.id)
  if (!id) return
  loading.value = true
  try {
    order.value = await OrderApi.getOrder(id)
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch(
  () => route.params.id,
  (v, old) => {
    if (v && v !== old && route.name === 'FarmOrderDetail') load()
  }
)
</script>
