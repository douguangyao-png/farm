<!-- 农资详情 + 下单（farmer-saas 新增）：必须选择用在哪块认养地 -->
<template>
  <s-layout title="农资详情">
    <demo-badge />
    <view class="f-page" v-if="s">
      <image class="cover" :src="s.cover" mode="aspectFill" />
      <view class="f-card">
        <view class="name">{{ s.name }}</view>
        <view class="f-between">
          <text class="f-price big">{{ yuan(s.price) }}<text class="unit">/{{ s.unit }}</text></text>
          <text class="f-sub">库存 {{ s.stock }} · 已售 {{ s.sold }}</text>
        </view>
        <view class="f-kv"><text class="k">规格</text><text class="v">{{ s.spec }}</text></view>
        <view class="f-kv"><text class="k">批次号</text><text class="v">{{ s.batchNo }}</text></view>
        <view class="f-kv" v-if="s.fitModes?.length">
          <text class="k">适合</text><text class="v">{{ s.fitModes.map((m) => CARE_MODE_NAME[m]).join('、') }}</text>
        </view>
        <view class="f-sub desc" v-if="s.description">{{ s.description }}</view>
        <view class="f-warn" v-if="s.needWorkorder">下单后自动生成代管工单，由管家到地块完成。</view>
      </view>

      <view class="f-card">
        <view class="f-title">购买</view>
        <view class="field">
          <text class="label">用于地块<text class="req">*</text></text>
          <view class="value" v-if="!isLogin" @tap="login">
            <text class="placeholder">登录后选择</text>
          </view>
          <picker v-else class="value" :range="adoptionLabels" :value="adoptionIdx < 0 ? 0 : adoptionIdx" @change="onPick">
            <text v-if="adoptionIdx >= 0">{{ adoptionLabels[adoptionIdx] }}</text>
            <text v-else class="placeholder">{{ adoptions.length ? '请选择认养地块' : '您还没有认养地块' }}</text>
            <text class="arrow">›</text>
          </picker>
        </view>
        <view class="field">
          <text class="label">数量</text>
          <view class="stepper">
            <view class="step" @tap="qty > 1 && qty--">−</view>
            <text class="qty">{{ qty }}</text>
            <view class="step" @tap="qty < s.stock && qty++">＋</view>
          </view>
        </view>
        <view class="field col">
          <text class="label">交付方式</text>
          <view class="ways">
            <view v-for="w in DELIVER_WAYS" :key="w.value" class="way" :class="{ active: deliverWay === w.value }" @tap="deliverWay = w.value">
              {{ w.label }}
            </view>
          </view>
          <view class="f-sub">{{ wayTip }}</view>
        </view>
        <view class="f-sub note">这份农资会记在所选地块的溯源档案里：使用后由管家登记用量，状态从「待登记」变成「已写入」。</view>
      </view>
      <view class="f-bottom-space" />
    </view>

    <view class="f-bottom-bar" v-if="s">
      <view>
        <text class="f-sub">合计 </text>
        <text class="f-price big">{{ yuan(s.price * qty) }}</text>
      </view>
      <button class="f-btn" :class="{ disabled: adoptionIdx < 0 }" @tap="buy">去结算</button>
    </view>
  </s-layout>
</template>

<script setup>
  import { computed, ref, watch } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { CARE_MODE_NAME, DELIVER_WAYS, yuan, call, needLogin } from '@/pages/farm/common/farm';
  import { showAuthModal } from '@/sheep/hooks/useModal';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const s = ref(null);
  const qty = ref(1);
  const deliverWay = ref('store');
  const adoptions = ref([]);
  const adoptionIdx = ref(-1);
  const presetAdoptionId = ref(0);
  const isLogin = computed(() => sheep.$store('user').isLogin);

  const adoptionLabels = computed(() =>
    adoptions.value.map((a) => `${a.assetCode} · ${CARE_MODE_NAME[a.careMode]} · ${a.productTitle}`),
  );
  const wayTip = computed(() => DELIVER_WAYS.find((w) => w.value === deliverWay.value)?.tip || '');

  function onPick(e) {
    adoptionIdx.value = Number(e.detail.value);
  }
  function login() {
    showAuthModal();
  }

  async function loadAdoptions() {
    if (!isLogin.value) return;
    adoptions.value = (await call(FarmApi.getAdoptionList())) || [];
    const i = adoptions.value.findIndex((a) => a.id === presetAdoptionId.value);
    if (i >= 0) adoptionIdx.value = i;
    else if (adoptions.value.length === 1) adoptionIdx.value = 0;
  }

  async function buy() {
    if (needLogin()) return;
    if (adoptionIdx.value < 0) {
      sheep.$helper.toast('请选择要使用这份农资的认养地块');
      return;
    }
    const ad = adoptions.value[adoptionIdx.value];
    // 进入独立结算页确认商品、数量、金额和收货地址后再下单
    sheep.$router.go('/pages/farm/supply/checkout', {
      id: s.value.id,
      qty: qty.value,
      adoptionId: ad.id,
      deliverWay: deliverWay.value,
    });
  }

  onLoad(async (options) => {
    presetAdoptionId.value = Number(options.adoptionId || 0);
    s.value = await call(FarmApi.getSupply(Number(options.id)));
    loadAdoptions();
  });
  watch(isLogin, (v) => v && loadAdoptions());
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .cover {
    width: 100%;
    height: 400rpx;
    border-radius: 20rpx;
    margin-bottom: 20rpx;
  }
  .name {
    font-size: 34rpx;
    font-weight: 700;
    margin-bottom: 10rpx;
  }
  .big {
    font-size: 40rpx;
  }
  .unit {
    font-size: 24rpx;
    font-weight: 400;
  }
  .desc {
    margin-top: 10rpx;
    line-height: 1.6;
  }
  .field {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 20rpx 0;
    border-bottom: 2rpx solid $fline;
    .label {
      color: $fsub;
      font-size: 26rpx;
      flex-shrink: 0;
    }
    .req {
      color: $fr;
      margin-left: 4rpx;
    }
    .value {
      flex: 1;
      text-align: right;
      font-size: 26rpx;
      margin-left: 20rpx;
    }
    .placeholder {
      color: #b0b6bf;
    }
    .arrow {
      color: $fsub;
      margin-left: 8rpx;
    }
  }
  .field.col {
    flex-direction: column;
    align-items: flex-start;
  }
  .stepper {
    display: flex;
    align-items: center;
    .step {
      width: 56rpx;
      height: 56rpx;
      line-height: 56rpx;
      text-align: center;
      background: #f1f2f4;
      border-radius: 10rpx;
      font-size: 32rpx;
    }
    .qty {
      width: 80rpx;
      text-align: center;
    }
  }
  .ways {
    display: flex;
    flex-wrap: wrap;
    gap: 16rpx;
    margin: 16rpx 0 10rpx;
  }
  .way {
    padding: 12rpx 24rpx;
    border-radius: 30rpx;
    border: 2rpx solid $fline;
    font-size: 24rpx;
  }
  .way.active {
    border-color: $fg;
    color: $fgd;
    background: $fgl;
  }
  .note {
    margin-top: 16rpx;
    line-height: 1.6;
  }
</style>
