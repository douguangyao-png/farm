<!-- 确认订单 + 合同确认（farmer-saas 新增）：交付方式 3 选 1、金额明细、三份文件勾选；未勾选不能支付（R-3），演示支付 -->
<template>
  <s-layout title="确认订单">
    <demo-badge />
    <view class="f-page" v-if="p && mode">
      <!-- 商品 -->
      <view class="f-card goods">
        <image class="goods-img" :src="p.cover" mode="aspectFill" />
        <view class="goods-body">
          <view class="goods-title">{{ p.title }}</view>
          <view class="f-sub">{{ p.baseName }} · 周期 {{ p.cycleDays }} 天</view>
          <view class="f-between">
            <text class="f-tag solid">{{ CARE_MODE_NAME[mode.mode] }}</text>
            <text class="f-price">{{ yuan(mode.price) }}</text>
          </view>
        </view>
      </view>

      <view class="f-card">
        <view class="f-kv"><text class="k">托管方式</text><text class="v">{{ CARE_MODE_NAME[mode.mode] }} · {{ mode.slogan }}</text></view>
        <view class="f-kv"><text class="k">产出说明</text><text class="v">{{ mode.outputPromise }}</text></view>
        <view class="f-kv"><text class="k">每周待办</text><text class="v">{{ mode.weeklyTodo }} 项</text></view>
        <view class="f-kv"><text class="k">保险</text><text class="v">{{ mode.insurance }}</text></view>
        <view class="f-kv"><text class="k">地块</text><text class="v">支付后自动分配空闲地块</text></view>
        <view class="f-kv"><text class="k">认养人</text><text class="v">{{ form.receiverName || '—' }}</text></view>
        <view class="f-kv"><text class="k">联系电话</text><text class="v">{{ maskedMobile }}</text></view>
      </view>

      <!-- 交付方式（原型 07 order：3 选 1） -->
      <view class="f-card">
        <view class="f-title">交付方式</view>
        <view class="ship" v-for="w in ORDER_DELIVER_WAYS" :key="w.value" @tap="deliverWay = w.value">
          <view>
            <view class="ship-name">{{ w.label }}</view>
            <view class="f-sub">{{ w.tip }}</view>
          </view>
          <view class="radio" :class="{ on: deliverWay === w.value }"><view class="dot" v-if="deliverWay === w.value" /></view>
        </view>
      </view>

      <!-- 金额明细 -->
      <view class="f-card">
        <view class="f-kv"><text class="k">商品金额</text><text class="v">{{ yuan(mode.price) }}</text></view>
        <view class="f-between pay-line">
          <text>应付金额</text>
          <text class="f-price total">{{ yuan(mode.price) }}</text>
        </view>
      </view>

      <view class="f-card" v-if="packageName">
        <view class="f-kv"><text class="k">农资套餐</text><text class="v">{{ packageName }}</text></view>
      </view>

      <!-- 推荐农资套餐：单独的农资订单，不并入认养金额 -->
      <view class="f-card" v-if="supplyItems.length">
        <view class="f-title">推荐农资套餐<text class="f-more">农资单独结算</text></view>
        <view class="ship" v-for="it in supplyItems" :key="it.id">
          <view>
            <view class="ship-name">{{ it.name }}<text class="f-tag orange" v-if="it.recommended">推荐</text></view>
            <view class="f-sub">{{ yuan(it.price) }}<text v-if="it.unit">/{{ it.unit }}</text></view>
          </view>
          <text class="f-more" @tap="goSupply(it)">去结算 ›</text>
        </view>
      </view>

      <!-- 收货人：快递到家必填；自提 / 基地配送不需要地址 -->
      <view class="f-card">
        <view class="f-sub">下单时记录大致位置，用于服务核验（可拒绝，不影响下单）</view>
      </view>

      <view class="f-card">
        <view class="f-title">收货信息<text class="f-more" v-if="needAddress" @tap="pickAddress">选择地址 ›</text><text class="f-more" v-else>当前交付方式无需收货地址</text></view>
        <view class="field">
          <text class="label">收货人</text>
          <input class="input" v-model="form.receiverName" placeholder="请输入收货人" />
        </view>
        <view class="field">
          <text class="label">手机号</text>
          <input class="input" v-model="form.receiverMobile" type="number" placeholder="请输入手机号" />
        </view>
        <view class="field" v-if="needAddress">
          <text class="label">地址<text class="req">*</text></text>
          <input class="input" v-model="form.address" placeholder="请输入或点右上「选择地址」" />
        </view>
      </view>

      <!-- 合同 -->
      <view class="f-card">
        <view class="f-title">认养合同</view>
        <view class="contract" @tap="openDoc('contract')">
          <text>{{ contractName }}</text>
          <text class="f-more">阅读 ›</text>
        </view>
        <view class="contract" @tap="openDoc('waiver')">
          <text>《免责条款》</text>
          <text class="f-more">阅读 ›</text>
        </view>
        <view class="contract" v-if="needNotice" @tap="openDoc('notice')">
          <text>《产出浮动告知书》</text>
          <text class="f-more">阅读 ›</text>
        </view>
        <view class="f-warn" v-if="needNotice">
          {{ CARE_MODE_NAME[mode.mode] }}按实际收成交付，产量会随日常管理浮动，农场不约定斤数。
        </view>
        <view class="agree" :class="{ shake }" @tap="agreed = !agreed">
          <view class="box" :class="{ on: agreed }">{{ agreed ? '✓' : '' }}</view>
          <view class="agree-text">
            我已阅读并同意{{ contractName }}<text v-if="needNotice">、《产出浮动告知书》</text>与《免责条款》，知悉本项目为实物交付、无资金收益。
          </view>
        </view>
      </view>
      <view class="f-ok legend">下单前请确认：认养为农产品预购行为，交付以实物为准，产出受气候、病虫害等自然条件影响；平台与经营者均不提供资金收益、回购或保本承诺。</view>
      <view class="f-bottom-space" />
    </view>

    <view class="f-bottom-bar" v-if="p && mode">
      <view>
        <text class="f-sub">合计 </text>
        <text class="f-price total">{{ yuan(mode.price) }}</text>
      </view>
      <button class="f-btn" :class="{ disabled: !agreed }" @tap="submit">提交并支付</button>
    </view>

    <!-- 合同 / 告知书 -->
    <su-popup :show="doc !== ''" round="10" showClose @close="doc = ''">
      <view class="doc">
        <view class="doc-title">{{ docTitle }}</view>
        <scroll-view scroll-y class="doc-body">
          <view class="doc-tip">演示文本，正式版由农场上传合同模板并电子签署</view>
          <view class="doc-p" v-for="(t, i) in docText" :key="i">{{ t }}</view>
        </scroll-view>
        <button class="f-btn block" @tap="readDone">我已阅读</button>
      </view>
    </su-popup>

    <!-- 演示支付 -->
    <su-popup :show="paying" round="10" showClose @close="paying = false">
      <view class="pay">
        <view class="pay-title">支付认养费用</view>
        <view class="pay-amount">{{ yuan(order?.amount) }}</view>
        <view class="f-sub center">订单号 {{ order?.no }}</view>
        <view class="pay-way">
          <text>演示支付</text>
          <text class="f-tag orange">演示支付，不扣款</text>
        </view>
        <button class="f-btn block" @tap="pay">确认支付 {{ yuan(order?.amount) }}</button>
      </view>
    </su-popup>
  </s-layout>
</template>

<script setup>
  import { computed, reactive, ref } from 'vue';
  import { onLoad, onShow } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { CARE_MODE_NAME, ORDER_DELIVER_WAYS, yuan, call, contractTitle, needLogin } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const p = ref(null);
  const modeKey = ref('');
  const agreed = ref(false);
  const shake = ref(false);
  const doc = ref('');
  const paying = ref(false);
  const order = ref(null);
  const form = reactive({ receiverName: '', receiverMobile: '', address: '' });

  // 交付方式 3 选 1（原型 07 order；值见接口约定第 1 条 deliverWay）
  const deliverWay = ref('express');
  const packageName = ref('');
  const needAddress = computed(() => deliverWay.value === 'express');
  const maskedMobile = computed(() => (form.receiverMobile || '').replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2') || '—');
  const docTitle = computed(() =>
    doc.value === 'notice' ? '《产出浮动告知书》' : doc.value === 'waiver' ? '《免责条款》' : contractName.value,
  );

  const mode = computed(() => p.value?.careModes.find((m) => m.mode === modeKey.value && m.enabled));
  const contractName = computed(() => (mode.value ? contractTitle(mode.value.mode, mode.value.contractVersion) : ''));
  const needNotice = computed(() => mode.value && mode.value.mode !== 'full');
  const supplyItems = computed(() => {
    if (!mode.value || mode.value.mode === 'full') return [];
    const pk = (p.value?.supplyPackages || []).find((x) => x.mode === mode.value.mode);
    return pk?.items || [];
  });

  const docText = computed(() => {
    const m = mode.value;
    if (!m) return [];
    if (doc.value === 'waiver') {
      return [
        '一、认养为农产品预购和农业生产服务，交付物为实物农产品，不构成任何形式的理财、投资或收益承诺。',
        '二、因台风、冰雹、洪涝、干旱、病虫害等不可抗力导致减产，按所投农业保险条款理赔；非不可抗力的减产，按合同约定补发或延期。',
        '三、物流途中的轻微损耗（运输颠簸、冷链温差等）在合理范围内不属于质量问题；收货时发现明显破损请当场拍照并联系专属管家。',
        '四、认养人自行负责的农事环节（见合同责任划分）未按时完成造成的减产，农场不承担补发责任。',
        '五、认养费用是农业服务费，不转移土地或畜禽所有权。',
      ];
    }
    if (doc.value === 'notice') {
      return [
        `一、您选择的是${CARE_MODE_NAME[m.mode]}。浇水、除草、追肥等日常农活由您负责（可委托管家代办），产量会受日常管理、天气、病虫害影响。`,
        `二、产出按实际收成交付：${m.outputPromise}。农场不约定斤数。`,
        '三、农场负责的环节（如整地、统防、采收）按合同执行；每次农事都有照片、定位和环境数据留档，您可以在「生长档案」里查看。',
        '四、自管任务逾期时，App 会提醒您，也可以一键委托管家代办，费用按任务标价收取。',
      ];
    }
    return [
      `甲方（认养人）向乙方（${p.value.baseName}所属农场）认养「${p.value.title}」，周期 ${p.value.cycleDays} 天，托管方式：${CARE_MODE_NAME[m.mode]}，费用 ${yuan(m.price)}。`,
      `一、责任划分：农场负责 ${m.duty.filter((d) => d.owner === 'farm').map((d) => d.item).join('、')}；${
        m.duty.some((d) => d.owner === 'member')
          ? '认养人负责 ' + m.duty.filter((d) => d.owner === 'member').map((d) => d.item).join('、') + '。'
          : '认养人无需动手。'
      }`,
      `二、产出交付：${m.outputPromise}。收成按批次称重后配送到收货地址。`,
      `三、保险：${m.insurance}。`,
      '四、认养费用是农业服务费，地块所有权不发生转移。',
      '五、支付成功后系统自动分配空闲地块，认养期内可随时查看监控画面、环境数据和溯源档案。',
    ];
  });

  function openDoc(k) {
    doc.value = k;
  }
  function readDone() {
    doc.value = '';
    agreed.value = true;
  }

  // 取大致位置：3 秒超时，拒绝或失败返回 null，不阻塞下单
  function getLoc() {
    return new Promise((resolve) => {
      let done = false;
      const fin = (v) => {
        if (!done) {
          done = true;
          resolve(v);
        }
      };
      setTimeout(() => fin(null), 3000);
      try {
        uni.getLocation({
          type: 'wgs84',
          success: (r) => fin(r && typeof r.longitude === 'number' ? { lng: r.longitude, lat: r.latitude } : null),
          fail: () => {
            try {
              if (typeof navigator !== 'undefined' && navigator.geolocation) {
                navigator.geolocation.getCurrentPosition(
                  (pos) => fin({ lng: pos.coords.longitude, lat: pos.coords.latitude }),
                  () => fin(null),
                  { timeout: 2800 },
                );
              } else fin(null);
            } catch (e) {
              fin(null);
            }
          },
        });
      } catch (e) {
        fin(null);
      }
    });
  }

  function goSupply(it) {
    sheep.$router.go('/pages/farm/supply/checkout', { id: it.id, qty: 1 });
  }

  async function submit() {
    if (!agreed.value) {
      sheep.$helper.toast('请先阅读并勾选认养合同');
      shake.value = true;
      setTimeout(() => (shake.value = false), 600);
      return;
    }
    if (needLogin()) return;
    if (!order.value) {
      if (needAddress.value) {
        if (!form.receiverName.trim()) return sheep.$helper.toast('请填写收货人');
        if (!/^1\d{10}$/.test(form.receiverMobile)) return sheep.$helper.toast('请填写 11 位手机号');
        if (!form.address.trim()) return sheep.$helper.toast('请填写或选择收货地址');
      }
      const loc = await getLoc();
      const data = await call(
        FarmApi.createOrder({
          ...(loc ? { lng: loc.lng, lat: loc.lat } : {}),
          gpsText: needAddress.value ? form.address : '',
          productId: p.value.id,
          careMode: mode.value.mode,
          contractAgreed: true,
          receiverName: form.receiverName,
          receiverMobile: form.receiverMobile,
          address: needAddress.value ? form.address : '',
          deliverWay: deliverWay.value,
          channel: 'h5',
          ...(packageName.value ? { packageName: packageName.value } : {}),
        }),
      );
      if (!data) return;
      order.value = data;
    }
    paying.value = true;
  }

  async function pay() {
    const data = await call(FarmApi.payOrder(order.value.id));
    if (!data) return;
    paying.value = false;
    sheep.$router.redirect('/pages/farm/order/result', {
      orderId: data.orderId,
      adoptionId: data.adoptionId,
      assetCode: data.assetCode || '',
    });
  }

  // 地址页选择模式（?select=1）会把所选地址写进 storage，返回时在 onShow 里取走
  function pickAddress() {
    sheep.$router.go('/pages/farm/address/list', { select: 1 });
  }
  onShow(() => {
    const a = uni.getStorageSync('farm_picked_address');
    if (!a) return;
    uni.removeStorageSync('farm_picked_address');
    if (a.name) form.receiverName = a.name;
    if (a.mobile) form.receiverMobile = a.mobile;
    if (a.detail) form.address = a.detail;
  });

  onLoad(async (options) => {
    packageName.value = options.package ? decodeURIComponent(options.package) : '';
    modeKey.value = options.mode || 'full';
    const user = sheep.$store('user');
    if (user.isLogin && !user.userInfo?.mobile) await user.getInfo();
    form.receiverName = user.userInfo?.nickname || '';
    form.receiverMobile = user.userInfo?.mobile || '';
    p.value = await call(FarmApi.getProduct(Number(options.id || 1)));
  });
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .goods {
    display: flex;
  }
  .goods-img {
    width: 160rpx;
    height: 160rpx;
    border-radius: 14rpx;
    margin-right: 20rpx;
    flex-shrink: 0;
  }
  .goods-body {
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
  }
  .goods-title {
    font-size: 30rpx;
    font-weight: 600;
  }
  .field {
    display: flex;
    align-items: center;
    border-bottom: 2rpx solid $fline;
    padding: 18rpx 0;
    .label {
      width: 140rpx;
      color: $fsub;
      font-size: 26rpx;
    }
    .input {
      flex: 1;
      font-size: 26rpx;
    }
  }
  .contract {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: #fafbfc;
    border-radius: 14rpx;
    padding: 20rpx;
    margin-bottom: 16rpx;
    color: $fgd;
    font-size: 26rpx;
  }
  .agree {
    display: flex;
    align-items: flex-start;
    padding: 8rpx 0;
  }
  .agree .box {
    width: 34rpx;
    height: 34rpx;
    border: 2rpx solid #c9ced6;
    border-radius: 8rpx;
    margin-right: 14rpx;
    flex-shrink: 0;
    text-align: center;
    line-height: 34rpx;
    color: #fff;
    font-size: 24rpx;
    margin-top: 2rpx;
  }
  .agree .box.on {
    background: $fg;
    border-color: $fg;
  }
  .agree-text {
    font-size: 26rpx;
    line-height: 1.5;
  }
  .shake {
    animation: shake 0.5s;
  }
  @keyframes shake {
    0%,
    100% {
      transform: translateX(0);
    }
    25%,
    75% {
      transform: translateX(-10rpx);
    }
    50% {
      transform: translateX(10rpx);
    }
  }
  .total {
    font-size: 40rpx;
  }
  .pay-line {
    margin-top: 12rpx;
    padding-top: 16rpx;
    border-top: 2rpx solid $fline;
    font-size: 28rpx;
    .total {
      font-size: 40rpx;
    }
  }
  .req {
    color: $fr;
    margin-left: 4rpx;
  }
  .ship {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 18rpx 0;
  }
  .ship-name {
    font-size: 28rpx;
  }
  .radio {
    width: 36rpx;
    height: 36rpx;
    border-radius: 50%;
    border: 3rpx solid #d5d9de;
    display: flex;
    align-items: center;
    justify-content: center;
    &.on {
      border-color: $fg;
    }
    .dot {
      width: 18rpx;
      height: 18rpx;
      border-radius: 50%;
      background: $fg;
    }
  }
  .legend {
    margin-top: 10rpx;
    line-height: 1.6;
  }
  .doc,
  .pay {
    padding: 40rpx 30rpx calc(30rpx + env(safe-area-inset-bottom));
  }
  .doc-title,
  .pay-title {
    font-size: 32rpx;
    font-weight: 600;
    text-align: center;
    margin-bottom: 20rpx;
  }
  .doc-body {
    max-height: 700rpx;
    margin-bottom: 24rpx;
  }
  .doc-tip {
    font-size: 22rpx;
    color: $fo;
    margin-bottom: 16rpx;
  }
  .doc-p {
    font-size: 26rpx;
    line-height: 1.7;
    margin-bottom: 12rpx;
  }
  .pay-amount {
    text-align: center;
    font-size: 56rpx;
    font-weight: 700;
    color: $ftx;
  }
  .center {
    text-align: center;
  }
  .pay-way {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border: 2rpx solid $fg;
    border-radius: 14rpx;
    padding: 24rpx;
    margin: 30rpx 0;
  }
</style>
