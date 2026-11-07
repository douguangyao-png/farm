<!-- farmer-saas: 顶栏右侧，对齐原型 08：租户名 + 告警数 -->
<template>
  <div class="farm-header-chips">
    <span class="chip chip-green">{{ nav.tenantName || '—' }}</span>
    <span class="chip chip-orange" :title="`${nav.alertCount} 项待处理`" @click="router.push(nav.alertTo)">
      <Icon icon="ep:bell" :size="13" /> {{ nav.alertCount }}
    </span>
  </div>
</template>

<script lang="ts" setup>
import { Icon } from '@/components/Icon'
import { useFarmNavStore } from '@/store/modules/farmNav'

defineOptions({ name: 'FarmHeaderChips' })

const router = useRouter()
const nav = useFarmNavStore()
onMounted(() => nav.start())
</script>

<style lang="scss" scoped>
.farm-header-chips {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: 8px;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  font-size: 12px;
  border-radius: 6px;
  white-space: nowrap;
}
.chip-green {
  color: #17885a;
  background: #eaf7f0;
}
.chip-orange {
  color: #a8620d;
  cursor: pointer;
  background: #fff5e8;
}
</style>
