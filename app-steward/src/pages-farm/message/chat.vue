<template>
  <view class="farm-page chat">
    <wd-navbar :title="info?.member.name || '聊天'" left-arrow placeholder safe-area-inset-top fixed @click-left="goBack" />
    <view v-if="info" class="farm-note">
      标的 {{ info.member.assetCode }} · 认养人 {{ info.member.name }} {{ info.member.mobile }}
    </view>
    <view class="msgs">
      <view v-if="info && info.list.length === 0" class="farm-empty">
        还没有消息，发一句问候吧
      </view>
      <view v-for="m in info?.list" :key="m.id" class="msg" :class="{ me: m.from === 'steward' }">
        <view class="bubble">
          {{ m.content }}
        </view>
        <view class="farm-t12 time">
          {{ fmtTime(m.createTime) }}
        </view>
      </view>
    </view>
    <view class="quick">
      <text v-for="t in QUICK" :key="t" class="chip" @click="text = t">
        {{ t }}
      </text>
    </view>
    <view class="farm-footbar">
      <input v-model="text" class="inp" placeholder="输入消息…" confirm-type="send" @confirm="send">
      <wd-button type="primary" size="small" :loading="sending" :disabled="!text.trim()" @click="send">
        发送
      </wd-button>
    </view>
  </view>
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import { getChat, sendChat } from '@/api/farm/steward'
import { fmtTime } from '../components/farm-utils'

definePage({ style: { navigationBarTitleText: '聊天', navigationStyle: 'custom' } })

const QUICK = ['地块今天已浇水', '这周会补苗', '周末可以去采摘']
const memberId = ref(0)
const info = ref<Awaited<ReturnType<typeof getChat>>>()
const text = ref('')
const sending = ref(false)

async function load() {
  try {
    info.value = await getChat(memberId.value)
  } catch {
    info.value = undefined
  }
}
onLoad((o) => {
  memberId.value = Number(o?.memberId)
})
onShow(() => load())

async function send() {
  const content = text.value.trim()
  if (!content || sending.value) {
    return
  }
  sending.value = true
  try {
    await sendChat(memberId.value, content)
    text.value = ''
    await load()
  } catch {
    // 错误提示由请求层弹出
  } finally {
    sending.value = false
  }
}
function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack()
  else uni.switchTab({ url: '/pages/farm-msg/index' })
}
</script>

<style lang="scss" scoped>
@import '../components/farm-extra.scss';

.chat {
  padding-bottom: 260rpx;
}
.msgs {
  padding: 20rpx 24rpx;
}
.msg {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-bottom: 24rpx;
  .bubble {
    max-width: 74%;
    padding: 18rpx 24rpx;
    font-size: 28rpx;
    line-height: 1.6;
    background: #fff;
    border-radius: 20rpx;
  }
  .time {
    margin-top: 6rpx;
  }
  &.me {
    align-items: flex-end;
    .bubble {
      color: #fff;
      background: $g;
    }
  }
}
.quick {
  position: fixed;
  right: 0;
  bottom: 140rpx;
  left: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  padding: 0 24rpx;
  .chip {
    padding: 8rpx 20rpx;
    font-size: 24rpx;
    color: $tx2;
    background: #fff;
    border-radius: 30rpx;
  }
}
</style>
