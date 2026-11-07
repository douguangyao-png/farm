<!-- 合约中心（farmer-saas 新增，原型 07 myContract）：我的合同 + 附属单据；数据来自订单列表里的 contract 字段 -->
<template>
  <s-layout title="合约中心">
    <demo-badge />
    <view class="f-page">
      <view class="f-ok">所有合同在支付成功后归档，可随时查看合同信息（演示版不含真实电子签章与 CA / 区块链存证）</view>

      <view class="f-card" v-for="o in list" :key="o.id" @tap="open(o)">
        <view class="f-between">
          <view class="f-row">
            <view class="doc-icon">合</view>
            <view>
              <view class="c-title">农产品认养服务合同</view>
              <view class="f-sub">编号 {{ o.contract.no }} · {{ fmtTime(o.contract.signedAt, 'YYYY-MM-DD') }}</view>
            </view>
          </view>
          <text class="f-tag" :class="o.adoptionStatus === 10 ? '' : 'gray'">{{ o.adoptionStatus === 10 ? '生效中' : '已届满' }}</text>
        </view>
        <view class="f-between foot">
          <view class="f-row">
            <text class="f-tag gray">CA 认证</text>
            <text class="f-tag gray">区块链存证</text>
          </view>
          <text class="f-more">查看合同 ›</text>
        </view>
      </view>

      <view v-if="loaded && !list.length" class="f-empty">
        <view class="f-empty-title">还没有合同</view>
        <view>认养支付成功后，合同会归档在这里</view>
      </view>

      <template v-if="list.length">
        <view class="f-title sec">附属单据</view>
        <view class="f-card">
          <view class="att" v-for="x in attachments" :key="x.name">
            <view>
              <view class="a-name">{{ x.name }}</view>
              <view class="f-sub">{{ x.sub }}</view>
            </view>
            <text class="f-sub">{{ x.no }}</text>
          </view>
        </view>
      </template>
    </view>

    <!-- 合同信息 -->
    <su-popup :show="!!cur" round="10" showClose @close="cur = null">
      <view class="pop" v-if="cur">
        <view class="pop-title">{{ cur.contract.title }}</view>
        <scroll-view scroll-y class="pop-body">
          <view class="f-kv"><text class="k">合同编号</text><text class="v">{{ cur.contract.no }}</text></view>
          <view class="f-kv"><text class="k">合同版本</text><text class="v">{{ cur.contract.version }}</text></view>
          <view class="f-kv"><text class="k">关联订单</text><text class="v">{{ cur.no }}</text></view>
          <view class="f-kv"><text class="k">认养标的</text><text class="v">{{ cur.productTitle }}</text></view>
          <view class="f-kv"><text class="k">托管方式</text><text class="v">{{ cur.careModeName }}</text></view>
          <view class="f-kv"><text class="k">合同金额</text><text class="v">{{ yuan(cur.amount) }}</text></view>
          <view class="f-kv" v-if="cur.assetCode"><text class="k">地块</text><text class="v">{{ cur.assetCode }}</text></view>
          <view class="f-kv" v-if="cur.startAt"><text class="k">服务周期</text><text class="v">{{ fmtTime(cur.startAt, 'YYYY-MM-DD') }} 至 {{ fmtTime(cur.endAt, 'YYYY-MM-DD') }}</text></view>
          <view class="f-kv"><text class="k">签署时间</text><text class="v">{{ cur.contract.signedAt ? fmtTime(cur.contract.signedAt) : '未签署（待付款）' }}</text></view>
          <view class="f-kv"><text class="k">阅读确认</text><text class="v">{{ cur.contract.agreed ? '已阅读并勾选' : '未确认' }}</text></view>
          <view class="f-warn" v-if="cur.promisedJin">约定交付 {{ cur.promisedJin }} 斤，不足部分按同等品质补发。</view>
          <view class="f-sub tip">合同原文下载和 CA / 区块链验真，正式版接入电子合同服务后开放。</view>
        </scroll-view>
        <button class="f-btn block" @tap="cur = null">知道了</button>
      </view>
    </su-popup>
  </s-layout>
</template>

<script setup>
  import { computed, ref, watch } from 'vue';
  import { onShow } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { yuan, fmtTime, call, needLogin } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const orders = ref([]);
  const loaded = ref(false);
  const cur = ref(null);

  // 已支付、已签署的订单才有合同归档（待付款订单没有）
  const list = computed(() => orders.value.filter((o) => o.contract && o.contract.signedAt));

  // 附属单据：取最新一份认养的编号，演示口径
  const attachments = computed(() => {
    const o = list.value[0];
    if (!o) return [];
    return [
      { name: '农业保险保单', no: o.insuranceNo || '—', sub: '政策性农业保险，自然灾害减产按条款赔付' },
      { name: '产出浮动告知书', no: o.careMode === 'full' ? '全托管无需签署' : '已签署', sub: '说明产出随自然条件和日常管理浮动' },
      { name: '第三方检测报告', no: '演示数据', sub: '农残检测结果，正式版由检测机构出具' },
      { name: '土地权属证明', no: '演示数据', sub: '基地土地流转合同，正式版上传扫描件' },
    ];
  });

  function open(o) {
    cur.value = o;
  }

  async function load() {
    if (needLogin()) return;
    orders.value = (await call(FarmApi.getOrderList())) || [];
    loaded.value = true;
  }

  onShow(load);
  watch(
    () => sheep.$store('user').isLogin,
    (v) => v && load(),
  );
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .doc-icon {
    width: 64rpx;
    height: 64rpx;
    line-height: 64rpx;
    text-align: center;
    border-radius: 14rpx;
    background: $fgl;
    color: $fgd;
    font-weight: 700;
    margin-right: 16rpx;
  }
  .c-title {
    font-size: 30rpx;
    font-weight: 600;
  }
  .foot {
    margin-top: 20rpx;
    padding-top: 18rpx;
    border-top: 2rpx solid $fline;
  }
  .sec {
    margin: 10rpx 0 14rpx;
  }
  .att {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 20rpx 0;
    border-bottom: 2rpx solid $fline;
  }
  .att:last-child {
    border-bottom: none;
  }
  .a-name {
    font-size: 28rpx;
  }
  .pop {
    padding: 40rpx 30rpx calc(30rpx + env(safe-area-inset-bottom));
  }
  .pop-title {
    font-size: 32rpx;
    font-weight: 600;
    text-align: center;
    margin-bottom: 20rpx;
  }
  .pop-body {
    max-height: 760rpx;
    margin-bottom: 24rpx;
  }
  .tip {
    margin-top: 16rpx;
  }
</style>
