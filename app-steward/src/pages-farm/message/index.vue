<template>
  <view class="farm-page">
    <wd-navbar title="消息" left-arrow placeholder safe-area-inset-top fixed @click-left="goBack" />
    <view class="farm-note">
      这里是你负责的认养人。点进去可以直接发消息，自管提醒也会出现在对话里。
    </view>
    <view v-if="loaded && list.length === 0" class="farm-empty">
      还没有负责的认养人
    </view>
    <view v-for="c in list" :key="c.memberId" class="farm-card item" @click="open(c.memberId)">
      <view class="avatar">
        {{ c.memberName.charAt(0) }}
      </view>
      <view class="body">
        <view class="farm-row">
          <text class="farm-t15">
            {{ c.memberName }} <text class="farm-t12">
              {{ c.assetCode }}
            </text>
          </text>
          <text class="farm-t12">
            {{ c.lastTime ? fmtTime(c.lastTime) : '' }}
          </text>
        </view>
        <view class="farm-t12 mt8 ofh">
          {{ c.last || '暂无消息，点击发起' }}
        </view>
      </view>
    </view>
    <DemoBadge />
  </view>
</template>

<script lang="ts" setup>
import type { Conversation } from '@/api/farm/steward'
import { ref } from 'vue'
import { getConversations } from '@/api/farm/steward'
import { navigateBackPlus } from '@/utils'
import DemoBadge from '../components/demo-badge.vue'
import { fmtTime } from '../components/farm-utils'

definePage({ style: { navigationBarTitleText: '消息', navigationStyle: 'custom' } })

const list = ref<Conversation[]>([])
const loaded = ref(false)

onShow(async () => {
  try {
    list.value = (await getConversations()).list
  } catch {
    list.value = []
  } finally {
    loaded.value = true
  }
})

function open(memberId: number) {
  uni.navigateTo({ url: `/pages-farm/message/chat?memberId=${memberId}` })
}
function goBack() {
  navigateBackPlus('/pages/index/index')
}
</script>

<style lang="scss" scoped>
@import '../components/farm-extra.scss';

.item {
  display: flex;
  gap: 20rpx;
  align-items: center;
}
.avatar {
  flex: 0 0 84rpx;
  width: 84rpx;
  height: 84rpx;
  font-size: 32rpx;
  line-height: 84rpx;
  color: #fff;
  text-align: center;
  background: $g;
  border-radius: 50%;
}
.body {
  flex: 1;
  min-width: 0;
}
.ofh {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
