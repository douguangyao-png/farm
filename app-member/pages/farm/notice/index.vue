<!-- 平台合规说明（farmer-saas 新增，原型 07 notice）：静态页 -->
<template>
  <s-layout title="平台合规说明">
    <demo-badge />
    <view class="f-page">
      <view class="f-ok">本页内容同步展示于「合规中心」，所有经营者需完成核验后方可上线</view>

      <view class="f-card">
        <view class="big">我们不是理财，是农产品预售</view>
        <view class="intro">
          认养农业的本质是<text class="b">农产品预售 + 农业生产服务</text>：您支付的是土地 / 畜禽的使用与托管服务费，农场交付的是实物农产品与到园体验。
        </view>
        <view class="rule" v-for="r in RULES" :key="r.t">
          <text class="f-tag" :class="r.bad ? 'red' : ''">{{ r.bad ? '✕ ' : '✓ ' }}{{ r.t }}</text>
          <text class="r-desc">{{ r.d }}</text>
        </view>
      </view>

      <view class="f-card">
        <view class="f-title">资金流向说明</view>
        <view class="flow">
          <template v-for="(s, i) in FLOW" :key="s">
            <view class="step">{{ s }}</view>
            <text class="arrow" v-if="i < FLOW.length - 1">→</text>
          </template>
        </view>
        <view class="f-sub">资金不进入平台账户；平台仅收取软件服务费，由支付机构按约定比例自动分账，全程可查。</view>
      </view>

      <view class="f-card">
        <view class="f-title">常见问题</view>
        <view class="qa" v-for="x in QA" :key="x.q">
          <view class="q">Q：{{ x.q }}</view>
          <view class="a">A：{{ x.a }}</view>
        </view>
      </view>
      <view class="f-sub foot">演示版文案，正式版以经营者实际合同和平台公示为准。</view>
    </view>
  </s-layout>
</template>

<script setup>
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const RULES = [
    { bad: true, t: '不承诺收益', d: '任何形式承诺年化收益、到期回购、保本付息的行为，均属违规' },
    { bad: true, t: '不设钱包', d: '平台不提供充值钱包、余额提现，不存在资金池' },
    { bad: true, t: '不带拉人头', d: '不设多级分销返佣，仅提供一次性实物权益赠送' },
    { bad: false, t: '真实标的', d: '每份认养对应唯一编号地块 / 畜禽，可实地考察核验' },
    { bad: false, t: '实物交割', d: '交付物为蔬菜 / 蛋 / 肉 / 果等实物，产出随自然条件浮动' },
    { bad: false, t: '资金合规', d: '款项进入经营者对公账户，由第三方支付机构监管结算' },
    { bad: false, t: '合同保障', d: '每笔认养签署电子合同，可在合约中心调阅' },
    { bad: false, t: '保险兜底', d: '已对接政策性农业保险，自然灾害导致的减产按条款赔付' },
  ];
  const FLOW = ['消费者支付', '第三方支付通道', '经营者对公账户', '按生产进度结算'];
  const QA = [
    { q: '收成不好怎么办？', a: '自然灾害导致减产，按农业保险条款赔付；非灾害性减产，经营者按合同补足或延期。全托管按约定交付的斤数，不足部分按同等品质补发。' },
    { q: '可以退款吗？', a: '认养开始前可全额退；开始后按已发生的农事投入比例扣除后退剩余部分，合同有明确公式。' },
    { q: '怎么确认我的地是真的？', a: '每份认养有唯一编号与 GPS 定位，可到园核验；监控画面 + 生长档案双重佐证。' },
    { q: '能转让给朋友吗？', a: '支持认养权益转赠，需受赠人完成实名（转赠功能后续开放）。' },
  ];
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .big {
    font-size: 34rpx;
    font-weight: 700;
    color: $fgd;
    margin-bottom: 14rpx;
  }
  .intro {
    font-size: 26rpx;
    line-height: 1.7;
    margin-bottom: 20rpx;
    .b {
      font-weight: 700;
    }
  }
  .rule {
    display: flex;
    align-items: flex-start;
    margin-bottom: 18rpx;
    .f-tag {
      flex-shrink: 0;
      margin-right: 16rpx;
    }
    .r-desc {
      flex: 1;
      font-size: 26rpx;
      line-height: 1.6;
    }
  }
  .flow {
    display: flex;
    align-items: center;
    margin: 20rpx 0;
    .step {
      flex: 1;
      text-align: center;
      background: $fgl;
      color: $fgd;
      border-radius: 14rpx;
      padding: 16rpx 6rpx;
      font-size: 22rpx;
      line-height: 1.5;
    }
    .arrow {
      color: $fg;
      margin: 0 6rpx;
    }
  }
  .qa {
    padding: 16rpx 0;
    border-bottom: 2rpx solid $fline;
    .q {
      font-size: 28rpx;
      font-weight: 600;
    }
    .a {
      font-size: 26rpx;
      color: $fsub;
      margin-top: 8rpx;
      line-height: 1.6;
    }
  }
  .qa:last-child {
    border-bottom: none;
  }
  .foot {
    text-align: center;
    margin: 10rpx 0 20rpx;
  }
</style>
