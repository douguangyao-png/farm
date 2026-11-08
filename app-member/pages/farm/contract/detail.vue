<!-- 合同详情 + 手写签名（第一期补齐 §9）：正文用订单 / 认养信息拼；签名 canvas 确认后 POST /app-api/farm/contract/sign -->
<template>
  <s-layout title="认养合同">
    <demo-badge />
    <view class="f-page" v-if="o">
      <view class="f-ok">演示合同文本，正式版由农场上传合同模板；手写签名图片随合同保存，不含 CA / 区块链存证。</view>
      <view class="f-card">
        <view class="title">{{ o.contract?.title || '农产品认养服务合同' }}</view>
        <view class="f-kv"><text class="k">合同编号</text><text class="v">{{ o.contract?.no || '—' }}</text></view>
        <view class="f-kv"><text class="k">关联订单</text><text class="v">{{ o.no }}</text></view>
        <view class="f-kv"><text class="k">认养标的</text><text class="v">{{ o.productTitle }}</text></view>
        <view class="f-kv"><text class="k">托管方式</text><text class="v">{{ o.careModeName }}</text></view>
        <view class="f-kv"><text class="k">合同金额</text><text class="v">{{ yuan(o.amount) }}</text></view>
        <view class="f-kv" v-if="o.assetCode"><text class="k">地块</text><text class="v">{{ o.assetCode }}</text></view>
        <view class="f-kv" v-if="o.startAt"><text class="k">服务周期</text><text class="v">{{ fmtTime(o.startAt, 'YYYY-MM-DD') }} 至 {{ fmtTime(o.endAt, 'YYYY-MM-DD') }}</text></view>
      </view>

      <view class="f-card">
        <view class="f-title">合同条款</view>
        <view class="p" v-for="(t, i) in clauses" :key="i">{{ t }}</view>
      </view>

      <view class="f-card" v-if="duty.length">
        <view class="f-title">责任矩阵（{{ o.careModeName }}）</view>
        <view class="duty-row head"><text>农事环节</text><text>负责方</text></view>
        <view class="duty-row" v-for="d in duty" :key="d.item">
          <text>{{ d.item }}</text>
          <text class="f-tag" :class="d.owner === 'farm' ? '' : 'orange'">{{ d.owner === 'farm' ? '农场' : '认养人' }}</text>
        </view>
      </view>

      <view class="f-card">
        <view class="f-title">认养人签名</view>
        <template v-if="signed">
          <image class="sig-img" :src="signed.signature" mode="aspectFit" />
          <view class="f-sub">签署时间 {{ fmtTime(signed.signedAt, 'YYYY-MM-DD HH:mm') }}</view>
        </template>
        <template v-else>
          <view class="f-sub">请在下方框内手写签名，确认后与合同一并保存</view>
          <canvas
            canvas-id="signCanvas"
            id="signCanvas"
            class="sign-canvas"
            disable-scroll
            @touchstart="onStart"
            @touchmove.stop.prevent="onMove"
            @touchend="onEnd"
          />
          <view class="btns">
            <button class="f-btn ghost" @tap="clearSign">清除</button>
            <button class="f-btn" :class="{ disabled: busy }" @tap="confirmSign">确认签名</button>
          </view>
        </template>
      </view>
      <view class="f-bottom-space" />
    </view>
    <view class="f-page" v-else-if="loaded"><view class="f-empty"><view class="f-empty-title">找不到这份合同</view></view></view>
  </s-layout>
</template>

<script setup>
  import { computed, nextTick, ref } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { yuan, fmtTime, call, callRaw, needLogin } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const o = ref(null);
  const product = ref(null);
  const loaded = ref(false);
  const signed = ref(null); // { signature, signedAt }
  const busy = ref(false);
  let orderId = 0;
  let ctx = null;
  let drawing = false;
  const hasStroke = ref(false);

  // 责任矩阵：取商品里对应托管模式的 duty
  const duty = computed(() => {
    const m = product.value?.careModes?.find((x) => x.mode === o.value?.careMode);
    return m?.duty || [];
  });
  const clauses = computed(() => {
    const v = o.value;
    if (!v) return [];
    const m = product.value?.careModes?.find((x) => x.mode === v.careMode);
    const farmItems = duty.value.filter((d) => d.owner === 'farm').map((d) => d.item).join('、');
    const memberItems = duty.value.filter((d) => d.owner !== 'farm').map((d) => d.item).join('、');
    return [
      `甲方（认养人）认养乙方（农场）的「${v.productTitle}」，托管方式：${v.careModeName}，费用 ${yuan(v.amount)}。`,
      duty.value.length
        ? `一、责任划分：农场负责 ${farmItems}；${memberItems ? '认养人负责 ' + memberItems + '。' : '认养人无需动手。'}`
        : '一、责任划分：以所选托管方式的责任矩阵为准。',
      `二、产出交付：${m?.outputPromise || '按实际收成交付'}${v.promisedJin ? '，约定交付 ' + v.promisedJin + ' 斤，不足部分按同等品质补发' : ''}。收成按批次称重后按订单选择的交付方式交付。`,
      `三、保险：${m?.insurance || '以农场投保的政策性农业保险条款为准'}。`,
      '四、认养费用是农业服务费，地块或畜禽所有权不发生转移；本项目为实物交付，无资金收益、回购或保本承诺。',
      '五、因不可抗力导致减产，按所投保险条款理赔；退款按合同第六条，已发生的农事投入与已交付农产品按实际成本扣除。',
    ];
  });

  function initCanvas() {
    ctx = uni.createCanvasContext('signCanvas');
    ctx.setLineWidth(3);
    ctx.setStrokeStyle('#222222');
    ctx.setLineCap('round');
    ctx.setLineJoin('round');
  }
  function pos(e) {
    const t = (e.touches && e.touches[0]) || (e.changedTouches && e.changedTouches[0]) || {};
    // uni 在 canvas 上给出的 x / y 是相对 canvas 左上角的坐标
    return { x: t.x !== undefined ? t.x : t.clientX, y: t.y !== undefined ? t.y : t.clientY };
  }
  function onStart(e) {
    if (!ctx) initCanvas();
    drawing = true;
    const p = pos(e);
    ctx.beginPath();
    ctx.moveTo(p.x, p.y);
  }
  function onMove(e) {
    if (!drawing || !ctx) return;
    const p = pos(e);
    ctx.lineTo(p.x, p.y);
    ctx.stroke();
    ctx.draw(true);
    ctx.beginPath();
    ctx.moveTo(p.x, p.y);
    hasStroke.value = true;
  }
  function onEnd() {
    drawing = false;
  }
  function clearSign() {
    if (!ctx) initCanvas();
    ctx.clearRect(0, 0, 2000, 2000);
    ctx.draw();
    hasStroke.value = false;
  }
  function toDataUrl() {
    return new Promise((resolve) => {
      uni.canvasToTempFilePath({
        canvasId: 'signCanvas',
        fileType: 'png',
        success: (r) => resolve(r.tempFilePath),
        fail: () => resolve(''),
      });
    });
  }
  async function confirmSign() {
    if (busy.value) return;
    if (!hasStroke.value) return sheep.$helper.toast('请先在框内手写签名');
    busy.value = true;
    const sig = await toDataUrl();
    if (!sig) {
      busy.value = false;
      return sheep.$helper.toast('签名生成失败，请重试');
    }
    const r = await callRaw(FarmApi.signContract({ orderId, signature: sig }));
    busy.value = false;
    if (r.code !== 0) return sheep.$helper.toast(r.msg || '签名提交失败，请稍后再试');
    signed.value = { signature: r.data?.signature || sig, signedAt: r.data?.signedAt || Date.now() };
    sheep.$helper.toast('签名已保存');
  }

  onLoad(async (opt) => {
    if (needLogin()) return;
    orderId = Number(opt.orderId || 0);
    const list = (await call(FarmApi.getOrderList())) || [];
    o.value = list.find((x) => x.id === orderId) || null;
    loaded.value = true;
    if (!o.value) return;
    // 以下两个请求失败都不挡页面：接口没就绪就只看正文、签名区仍可操作（提交失败会提示）
    const [c, pr] = await Promise.all([
      call(FarmApi.getContract(orderId)),
      o.value.productId ? call(FarmApi.getProduct(o.value.productId)) : Promise.resolve(null),
    ]);
    product.value = pr;
    if (c && c.signature) signed.value = { signature: c.signature, signedAt: c.signedAt };
    await nextTick();
    if (!signed.value) initCanvas();
  });
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .title {
    font-size: 32rpx;
    font-weight: 600;
    text-align: center;
    margin-bottom: 16rpx;
  }
  .p {
    font-size: 26rpx;
    line-height: 1.7;
    margin-bottom: 12rpx;
  }
  .duty-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 14rpx 0;
    border-bottom: 2rpx solid $fline;
    font-size: 26rpx;
    &.head {
      color: $fsub;
    }
  }
  .sign-canvas {
    width: 100%;
    height: 320rpx;
    margin: 16rpx 0;
    background: #fafbfc;
    border: 2rpx dashed #c9ced6;
    border-radius: 14rpx;
  }
  .sig-img {
    width: 100%;
    height: 240rpx;
    background: #fafbfc;
    border-radius: 14rpx;
    margin: 12rpx 0;
  }
  .btns {
    display: flex;
    gap: 20rpx;
    .f-btn {
      flex: 1;
    }
  }
</style>
