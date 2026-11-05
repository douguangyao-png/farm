<template>
  <div v-loading="loading">
    <template v-if="order">
      <ContentWrap>
        <el-descriptions title="订单信息" :column="3" border>
          <el-descriptions-item label="订单号">{{ order.no }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="enumOf(ORDER_STATUS, order.status).type">
              {{ enumLabel(ORDER_STATUS, order.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="金额">{{ formatMoney(order.amount) }}</el-descriptions-item>
          <el-descriptions-item label="认养商品">{{ order.productTitle }}</el-descriptions-item>
          <el-descriptions-item label="托管方式">
            <el-tag :type="enumOf(CARE_MODES, order.careMode).type" effect="plain">
              {{ enumLabel(CARE_MODES, order.careMode) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="认养人">
            {{ order.memberName }}（{{ order.memberMobile }}）
          </el-descriptions-item>
          <el-descriptions-item label="下单时间">{{
            fmtTime(order.createTime)
          }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ fmtTime(order.payTime) }}</el-descriptions-item>
          <el-descriptions-item label="收货人">
            <template v-if="order.receiverName">
              {{ order.receiverName }} {{ order.receiverMobile }} {{ order.address }}
            </template>
            <template v-else>默认用认养人资料</template>
          </el-descriptions-item>
        </el-descriptions>
      </ContentWrap>

      <el-row :gutter="12">
        <el-col :xs="24" :lg="10">
          <ContentWrap>
            <el-descriptions title="认养合同" :column="1" border>
              <el-descriptions-item label="合同编号">{{
                order.contract?.no || '-'
              }}</el-descriptions-item>
              <el-descriptions-item label="合同名称">{{
                order.contract?.title || '-'
              }}</el-descriptions-item>
              <el-descriptions-item label="版本">{{
                order.contract?.version || '-'
              }}</el-descriptions-item>
              <el-descriptions-item label="签署">
                <template v-if="order.contract?.agreed && order.contract?.signedAt">
                  <el-tag type="success" size="small">已签署</el-tag>
                  <span class="ml-6px">{{ fmtTime(order.contract.signedAt) }}</span>
                </template>
                <el-tag v-else type="warning" size="small">未签署</el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </ContentWrap>
        </el-col>
        <el-col :xs="24" :lg="14">
          <ContentWrap>
            <el-descriptions v-if="ad" title="认养关系" :column="2" border>
              <el-descriptions-item label="地块 / 资产">{{ ad.assetCode }}</el-descriptions-item>
              <el-descriptions-item label="基地">{{ ad.baseName }}</el-descriptions-item>
              <el-descriptions-item label="开始">{{ fmtDay(ad.startAt) }}</el-descriptions-item>
              <el-descriptions-item label="结束">{{ fmtDay(ad.endAt) }}</el-descriptions-item>
              <el-descriptions-item label="交付进度" :span="2">
                <template v-if="ad.promisedJin">
                  <div class="text-13px mb-4px">
                    约定交付 {{ ad.promisedJin }} 斤，不足部分按同等品质补发；已收
                    {{ ad.harvestedJin }} 斤
                  </div>
                  <el-progress :percentage="deliverPercent" :stroke-width="14" />
                </template>
                <template v-else>按实际收成交付，已收 {{ ad.harvestedJin }} 斤</template>
              </el-descriptions-item>
            </el-descriptions>
            <el-empty v-else description="订单还没有分配地块（未支付或已取消）" :image-size="60" />
          </ContentWrap>
        </el-col>
      </el-row>

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
    </template>
  </div>
</template>
<script lang="ts" setup>
import * as OrderApi from '@/api/farm/order'
import {
  CARE_MODES,
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
const loading = ref(false)
const order = ref<any>()
const ad = computed(() => order.value?.adoption)
const deliverPercent = computed(() => {
  const a = ad.value
  if (!a?.promisedJin) return 0
  return Math.min(100, Math.round((a.harvestedJin / a.promisedJin) * 100))
})

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
