<!-- 自管任务（farmer-saas 新增）：逾期提示、我已完成、委托管家代办（R-4、R-5） -->
<template>
  <s-layout title="自管任务">
    <demo-badge />
    <view class="f-page">
      <view class="f-card head" v-if="ad">
        <view class="f-between">
          <text class="h-title">地块 {{ ad.assetCode }} · {{ ad.productTitle }}</text>
          <text class="f-tag">{{ CARE_MODE_NAME[ad.careMode] }}</text>
        </view>
        <view class="f-sub" v-if="memberDuty.length">您负责：{{ memberDuty.join('、') }}</view>
      </view>

      <!-- 全托管：没有自管任务 -->
      <view v-if="ad && ad.careMode === 'full'" class="f-card f-empty">
        <view class="f-empty-title">全托管无需您动手</view>
        <view>浇水、除草、施肥都由管家完成，您可以在「生长档案」里看每次农事的照片。</view>
      </view>

      <template v-else-if="loaded">
        <view class="f-ok" v-if="lastEntrust">
          已委托管家代办「{{ lastEntrust.title }}」，工单号 {{ lastEntrust.no }}，管家接单后会上门处理。
        </view>
        <view class="f-warn" v-if="overdueCount > 0 && tab === 'todo'">
          有 {{ overdueCount }} 项任务已逾期，忙不过来可以委托管家代办。
        </view>

        <view class="f-tabs">
          <view class="f-tab" :class="{ active: tab === 'todo' }" @tap="tab = 'todo'">待办（{{ todo.length }}）</view>
          <view class="f-tab" :class="{ active: tab === 'done' }" @tap="tab = 'done'">已完成（{{ done.length }}）</view>
        </view>

        <view class="f-card task" :class="{ overdue: t.overdue }" v-for="t in shown" :key="t.id">
          <view class="f-between">
            <view class="f-row">
              <text class="t-title">{{ t.title }}</text>
              <text class="f-tag red" v-if="t.overdue">已逾期</text>
              <text class="f-tag" v-else-if="t.status === 10">已完成</text>
              <text class="f-tag orange" v-else-if="t.status === 20">已委托管家</text>
            </view>
            <text class="f-sub">{{ t.assetCode }}</text>
          </view>
          <view class="t-desc">{{ t.description }}</view>
          <view class="f-sub">
            <text v-if="t.status === 0">截止 {{ fmtTime(t.deadline, 'MM-DD HH:mm') }}</text>
            <text v-else-if="t.finishTime">完成于 {{ fmtTime(t.finishTime, 'MM-DD HH:mm') }}</text>
            <text v-else-if="t.status === 20">已生成代管工单，等待管家接单</text>
          </view>
          <view class="actions" v-if="t.status === 0">
            <button class="f-btn ghost small" @tap="markDone(t)">我已完成</button>
            <button class="f-btn small" :class="{ orange: t.overdue }" @tap="entrust(t)">
              委托管家代办 {{ yuan(t.entrustPrice) }}
            </button>
          </view>
        </view>
        <view v-if="!shown.length" class="f-empty">{{ tab === 'todo' ? '没有待办任务' : '还没有完成的任务' }}</view>
      </template>
    </view>
  </s-layout>
</template>

<script setup>
  import { computed, ref, watch } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { CARE_MODE_NAME, yuan, fmtTime, call, needLogin } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const adoptionId = ref(0);
  const ad = ref(null);
  const tasks = ref([]);
  const loaded = ref(false);
  const tab = ref('todo');
  const lastEntrust = ref(null);

  const todo = computed(() => tasks.value.filter((t) => t.status === 0));
  const done = computed(() => tasks.value.filter((t) => t.status !== 0));
  const shown = computed(() => (tab.value === 'todo' ? todo.value : done.value));
  const overdueCount = computed(() => todo.value.filter((t) => t.overdue).length);
  const memberDuty = computed(() =>
    (ad.value?.careModeInfo?.duty || []).filter((d) => d.owner === 'member').map((d) => d.item),
  );

  async function load() {
    if (needLogin()) return;
    if (adoptionId.value) ad.value = await call(FarmApi.getAdoption(adoptionId.value));
    tasks.value =
      (await call(FarmApi.getSelfTaskList(adoptionId.value ? { adoptionId: adoptionId.value } : {}))) || [];
    loaded.value = true;
  }

  function markDone(t) {
    uni.showModal({
      title: '确认完成',
      content: `确认「${t.title}」已经做完了吗？`,
      success: async (r) => {
        if (!r.confirm) return;
        const ok = await call(FarmApi.doneSelfTask(t.id));
        if (ok === null) return;
        sheep.$helper.toast('已标记完成');
        load();
      },
    });
  }

  function entrust(t) {
    uni.showModal({
      title: '委托管家代办',
      content: `由管家代您完成「${t.title}」，服务费 ${yuan(t.entrustPrice)}（演示环境不扣款）。确认委托吗？`,
      confirmText: '确认委托',
      success: async (r) => {
        if (!r.confirm) return;
        const data = await call(FarmApi.entrustSelfTask(t.id));
        if (!data) return;
        lastEntrust.value = { title: t.title, no: data.no };
        uni.showToast({ title: `已委托管家，工单号 ${data.no}`, icon: 'none', duration: 3000 });
        load();
      },
    });
  }

  onLoad((options) => {
    adoptionId.value = Number(options.adoptionId || 0);
    load();
  });
  watch(
    () => sheep.$store('user').isLogin,
    (v) => v && load(),
  );
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .h-title {
    font-size: 28rpx;
    font-weight: 600;
  }
  .head .f-sub {
    margin-top: 8rpx;
  }
  .task.overdue {
    border: 2rpx solid $fr;
    background: #fffafa;
  }
  .t-title {
    font-size: 30rpx;
    font-weight: 600;
    margin-right: 12rpx;
  }
  .t-desc {
    font-size: 26rpx;
    color: #374151;
    margin: 10rpx 0;
    line-height: 1.6;
  }
  .actions {
    display: flex;
    justify-content: flex-end;
    gap: 16rpx;
    margin-top: 16rpx;
  }
  .small {
    height: 60rpx;
    line-height: 60rpx;
    font-size: 24rpx;
    padding: 0 24rpx;
  }
</style>
