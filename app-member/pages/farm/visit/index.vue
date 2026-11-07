<!-- 预约到访（原型 07 visit）：基地、日期、人数、用餐、体验项目 -->
<template>
  <s-layout title="预约到访">
    <demo-badge />
    <view class="f-page">
      <view class="f-card">
        <view class="row">
          <text class="lb">到访基地</text>
          <picker :range="bases" range-key="name" @change="onBase">
            <text class="v">{{ baseName || '请选择' }} ›</text>
          </picker>
        </view>
        <view class="row">
          <text class="lb">到访日期</text>
          <picker mode="date" :start="today" @change="form.date = $event.detail.value">
            <text class="v">{{ form.date || '请选择' }} ›</text>
          </picker>
        </view>
        <view class="row">
          <text class="lb">人数</text>
          <view class="f-row">
            <text class="step" @tap="form.people = Math.max(1, form.people - 1)">-</text>
            <text class="num">{{ form.people }}</text>
            <text class="step" @tap="form.people = Math.min(20, form.people + 1)">+</text>
          </view>
        </view>
        <view class="row">
          <text class="lb">用餐</text>
          <view class="f-row">
            <text v-for="m in MEALS" :key="m.k" class="f-tag opt" :class="form.meal === m.k ? 'solid' : 'gray'" @tap="form.meal = m.k">{{ m.label }}</text>
          </view>
        </view>
        <view class="row col">
          <text class="lb">体验项目</text>
          <view class="f-row wrap">
            <text v-for="i in ITEMS" :key="i" class="f-tag opt" :class="form.items.includes(i) ? 'solid' : 'gray'" @tap="toggleItem(i)">{{ i }}</text>
          </view>
        </view>
        <view class="row"><text class="lb">备注</text><input class="inp" v-model="form.remark" placeholder="选填" /></view>
      </view>
      <button class="f-btn block" :class="{ disabled: busy }" @tap="submit">提交预约</button>
      <view class="f-sub tip">提交后由基地管家电话确认，用餐和体验项目是否收费以基地确认为准。</view>

      <template v-if="list.length">
        <view class="f-title" style="margin-top: 30rpx">我的预约</view>
        <view class="f-card" v-for="v in list" :key="v.id">
          <view class="f-between"><text>{{ v.baseName }}</text><text class="f-tag orange">待基地确认</text></view>
          <view class="f-sub">{{ v.date }} · {{ v.people }} 人 · {{ v.no }}</view>
        </view>
      </template>
    </view>
  </s-layout>
</template>

<script setup>
  import { computed, reactive, ref } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { call, callRaw, needLogin } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const MEALS = [
    { k: 'none', label: '不用餐' },
    { k: 'lunch', label: '午餐' },
    { k: 'dinner', label: '晚餐' },
  ];
  const ITEMS = ['采摘体验', '看自己的地', '喂养互动', '农事课堂'];
  const d = new Date();
  const today = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

  const bases = ref([]);
  const list = ref([]);
  const busy = ref(false);
  const form = reactive({ baseId: 0, date: '', people: 2, meal: 'none', items: [], remark: '' });
  const baseName = computed(() => (bases.value.find((b) => b.id === form.baseId) || {}).name);

  onLoad(async (opt) => {
    bases.value = (await call(FarmApi.getBaseList())) || [];
    form.baseId = Number(opt && opt.baseId) || (bases.value[0] && bases.value[0].id) || 0;
    if (!needLogin()) list.value = (await call(FarmApi.getVisitList())) || [];
  });
  function onBase(e) {
    form.baseId = bases.value[e.detail.value].id;
  }
  function toggleItem(i) {
    const k = form.items.indexOf(i);
    if (k >= 0) form.items.splice(k, 1);
    else form.items.push(i);
  }
  async function submit() {
    if (needLogin()) return;
    if (!form.baseId) return sheep.$helper.toast('请选择基地');
    if (!form.date) return sheep.$helper.toast('请选择到访日期');
    busy.value = true;
    const r = await callRaw(FarmApi.createVisit({ ...form, items: [...form.items] }));
    busy.value = false;
    if (r.code !== 0) return;
    sheep.$helper.toast('预约已提交');
    list.value = (await call(FarmApi.getVisitList())) || [];
  }
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 20rpx 0;
    border-bottom: 2rpx solid $fline;
  }
  .row.col {
    flex-direction: column;
    align-items: flex-start;
  }
  .row:last-child {
    border-bottom: none;
  }
  .lb {
    color: $fsub;
    font-size: 26rpx;
    margin-right: 20rpx;
  }
  .wrap {
    flex-wrap: wrap;
    margin-top: 14rpx;
  }
  .inp {
    flex: 1;
    text-align: right;
  }
  .opt {
    padding: 12rpx 20rpx;
    font-size: 24rpx;
    margin-bottom: 8rpx;
  }
  .step {
    width: 56rpx;
    height: 56rpx;
    line-height: 52rpx;
    text-align: center;
    border-radius: 50%;
    background: $fgl;
    color: $fgd;
    font-size: 36rpx;
  }
  .num {
    min-width: 80rpx;
    text-align: center;
    font-size: 30rpx;
  }
  .tip {
    margin-top: 16rpx;
  }
</style>
