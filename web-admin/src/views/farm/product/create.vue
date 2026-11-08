<!-- 「发布认养商品」菜单：复用商品页，打开即弹出新建表单 -->
<template>
  <ProductIndex :auto-create="true" />
</template>
<script lang="ts" setup>
import ProductIndex from './index.vue'

defineOptions({ name: 'FarmProductCreate' })
</script>
