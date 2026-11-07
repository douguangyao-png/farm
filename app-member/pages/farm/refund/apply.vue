<!-- 退款/售后申请（原型 07 refund）：退款规则、原因 5 选 1、补充说明、提交。接口由 A agent 提供：POST /app-api/farm/refund/create {orderId, reason} -->
<template>
  <s-layout title="申请退款">
    <demo-badge />
    <view class="f-page" v-if="order">
      <view class="f-card">
        <view class="f-row">
          <image class="cover" :src="order.productCover" mode="aspectFill" />
          <view class="body">
            <view class="g-title">{{ order.productTitle }}</view>
            <view class="f-sub">{{ order.careModeName }}<text v-if="order.assetCode"> · {{ order.assetCode }}</text> · 订单 {{ order.no }}</view>
          </view>
        </view>
      </view>

      <view class="f-warn">退款规则（合同第六条）：已发生的农事投入与已交付农产品按实际成本扣除，余额原路退回。具体金额以农场审核结果为准。</view>

      <view class="f-card">
        <view class="f-title">退款原因</view>
        <view class="reason" v-for="r in REASONS" :key="r" @tap="reason = r">
          <text>{{ r }}</text>
          <text class="radio" :class="{ on: reason === r }" />
        </view>
        <textarea class="ta" v-model="remark" maxlength="200" placeholder="补充说明（选填）" />
      </view>

      <view class="f-card">
        <view class="f-kv"><text class="k">实付金额</text><text class="v f-price">{{ yuan(order.amount) }}</text></view>
        <view class="f-kv"><text class="k">预计退回</text><text class="v f-sub">农场审核后确定</text></view>
      </view>

      <button class="f-btn block" :class="{ disabled: busy }" @tap="submit">提交申请</button>
    </view>
    <view class="f-page" v-else-if="loaded"><view class="f-empty"><view class="f-empty-title">找不到这笔订单</view></view></view>
  </s-layout>
</template>

<script setup>
  import { ref } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { yuan, call, callRaw, needLogin } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const REASONS = ['计划有变，不想认养了', '基地服务与承诺不符', '农产品品质问题', '配送问题', '其他原因'];
  const order = ref(null);
  const loaded = ref(false);
  const reason = ref(REASONS[0]);
  const remark = ref('');
  const busy = ref(false);

  onLoad(async (opt) => {
    if (needLogin()) return;
    const list = (await call(FarmApi.getOrderList())) || [];
    order.value = list.find((o) => String(o.id) === String(opt.orderId)) || null;
    loaded.value = true;
  });
  async function submit() {
    busy.value = true;
    // 约定字段 {orderId, reason}；补充说明拼进 reason，等 A 的契约确认是否有独立字段
    const text = remark.value.trim() ? `${reason.value}：${remark.value.trim()}` : reason.value;
    const r = await callRaw(FarmApi.createRefund({ orderId: order.value.id, reason: text }));
    busy.value = false;
    if (r.code !== 0) return;
    sheep.$helper.toast('退款申请已提交');
    setTimeout(() => sheep.$router.redirect('/pages/farm/order/list'), 600);
  }
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .cover {
    width: 120rpx;
    height: 120rpx;
    border-radius: 14rpx;
    margin-right: 20rpx;
  }
  .g-title {
    font-size: 30rpx;
    font-weight: 600;
  }
  .reason {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 18rpx 0;
    border-bottom: 2rpx solid $fline;
  }
  .radio {
    width: 34rpx;
    height: 34rpx;
    border-radius: 50%;
    border: 2rpx solid #c9ced6;
  }
  .radio.on {
    border: 10rpx solid $fg;
  }
  .ta {
    width: 100%;
    height: 160rpx;
    margin-top: 16rpx;
    font-size: 26rpx;
  }
</style>
