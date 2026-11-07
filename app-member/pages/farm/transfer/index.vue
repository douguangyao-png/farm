<!-- 认养转赠（原型 07 transfer）：转赠规则、受赠人手机号、转赠范围、留言。提交后待对方确认（演示，不改归属） -->
<template>
  <s-layout title="认养转赠">
    <demo-badge />
    <view class="f-page">
      <view class="f-warn">转赠规则：仅「认养中」的订单可转赠；转赠后合同、地块使用权、未收取的产出一并归受赠人，转赠不可撤回。</view>

      <view class="f-card">
        <view class="row"><text class="lb">受赠人手机号</text><input class="inp" type="number" maxlength="11" v-model="form.toMobile" placeholder="对方注册的手机号" /></view>
        <view class="row">
          <text class="lb">转赠范围</text>
          <view class="f-row">
            <text class="f-tag opt" :class="form.scope === 'all' ? 'solid' : 'gray'" @tap="form.scope = 'all'">整份认养</text>
            <text class="f-tag opt" :class="form.scope === 'right' ? 'solid' : 'gray'" @tap="form.scope = 'right'">仅未收产出</text>
          </view>
        </view>
        <view class="row"><text class="lb">留言</text><input class="inp" v-model="form.message" placeholder="想对对方说的话（选填）" /></view>
      </view>
      <button class="f-btn block" :class="{ disabled: busy }" @tap="submit">提交转赠</button>

      <template v-if="list.length">
        <view class="f-title" style="margin-top: 30rpx">转赠记录</view>
        <view class="f-card" v-for="t in list" :key="t.id">
          <view class="f-between"><text>{{ t.no }}</text><text class="f-tag orange">待对方确认</text></view>
          <view class="f-sub">受赠人 {{ t.toMobile }} · {{ fmtTime(t.createTime) }}</view>
        </view>
      </template>
    </view>
  </s-layout>
</template>

<script setup>
  import { reactive, ref } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { call, callRaw, fmtTime, needLogin } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const form = reactive({ adoptionId: 0, toMobile: '', scope: 'all', message: '' });
  const list = ref([]);
  const busy = ref(false);

  onLoad(async (opt) => {
    form.adoptionId = Number(opt.adoptionId);
    if (needLogin()) return;
    list.value = (await call(FarmApi.getTransferList())) || [];
  });
  async function submit() {
    if (!/^1\d{10}$/.test(form.toMobile)) return sheep.$helper.toast('请填写 11 位手机号');
    busy.value = true;
    const r = await callRaw(FarmApi.createTransfer({ ...form }));
    busy.value = false;
    if (r.code !== 0) return;
    sheep.$helper.toast('已提交，等待对方确认');
    list.value = (await call(FarmApi.getTransferList())) || [];
    form.toMobile = '';
    form.message = '';
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
  .row:last-child {
    border-bottom: none;
  }
  .lb {
    color: $fsub;
    font-size: 26rpx;
    flex-shrink: 0;
    margin-right: 20rpx;
  }
  .inp {
    flex: 1;
    text-align: right;
    font-size: 28rpx;
  }
  .opt {
    padding: 12rpx 20rpx;
    font-size: 24rpx;
  }
</style>
