<!-- farmer-saas: 首页。登录后按账号进入对应平台：平台管理员看「平台总览」，经营者看「工作台」（取代芋道示例首页） -->
<template>
  <PlatformOverview v-if="isPlatform" />
  <FarmDashboard v-else />
</template>

<script lang="ts" setup>
import { getTenantId } from '@/utils/auth'
import PlatformOverview from '@/views/farm/platform/overview/index.vue'
import FarmDashboard from '@/views/farm/dashboard/index.vue'

defineOptions({ name: 'Index' })

const isPlatform = Number(getTenantId()) === 1
</script>
