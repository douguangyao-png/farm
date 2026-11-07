<!-- 认养商品详情（farmer-saas 新增）：三档托管选择（R-4） -->
<template>
  <s-layout title="认养详情">
    <demo-badge />
    <view class="f-page" v-if="p">
      <image class="cover" :src="p.cover" mode="aspectFill" />
      <view class="f-card">
        <view class="title">{{ p.title }}</view>
        <view class="f-sub desc">{{ p.description }}</view>
        <view class="f-row meta">
          <text class="f-tag gray">周期 {{ p.cycleDays }} 天</text>
          <text class="f-tag gray" v-if="p.areaM2">{{ p.areaM2 }}㎡</text>
          <text class="f-tag gray">已认养 {{ p.sold }}</text>
          <text class="f-tag gray">剩 {{ p.stock }} 份</text>
        </view>
      </view>

      <!-- 三档托管 -->
      <view class="f-card">
        <view class="f-title">
          选择托管方式
          <text class="f-more" @tap="toCompare">托管方式对比 ›</text>
        </view>
        <view class="modes">
          <view
            v-for="m in p.careModes"
            :key="m.mode"
            class="mode"
            :class="{ active: m.mode === sel, off: !m.enabled }"
            @tap="pick(m)"
          >
            <view class="mode-name">{{ CARE_MODE_NAME[m.mode] }}</view>
            <view class="mode-price" v-if="m.enabled">{{ yuan(m.price) }}</view>
            <view class="mode-price off-text" v-else>未开放</view>
            <view class="mode-todo">{{ m.weeklyTodo ? `每周约 ${m.weeklyTodo} 项您来做` : '无需您动手' }}</view>
          </view>
        </view>

        <view class="mode-detail" v-if="cur">
          <view class="slogan">「{{ cur.slogan }}」</view>
          <view class="f-kv"><text class="k">价格</text><text class="v f-price">{{ yuan(cur.price) }} / {{ p.cycleDays }} 天</text></view>
          <view class="f-kv"><text class="k">产出说明</text><text class="v">{{ cur.outputPromise }}</text></view>
          <view class="f-kv"><text class="k">保险</text><text class="v">{{ cur.insurance }}</text></view>
          <view class="f-kv"><text class="k">合同</text><text class="v">{{ contractTitle(cur.mode, cur.contractVersion) }}</text></view>
          <view class="f-kv">
            <text class="k">每周待办</text>
            <text class="v">{{ cur.weeklyTodo ? `约 ${cur.weeklyTodo} 项，App 里会提醒您` : '0 项，全部由农场完成' }}</text>
          </view>

          <view class="duty">
            <view class="duty-col">
              <view class="duty-head farm">农场负责（{{ farmDuty.length }} 项）</view>
              <view class="duty-item" v-for="d in farmDuty" :key="d">{{ d }}</view>
            </view>
            <view class="duty-col">
              <view class="duty-head member">您来负责（{{ memberDuty.length }} 项）</view>
              <view class="duty-item" v-for="d in memberDuty" :key="d">{{ d }}</view>
              <view class="duty-item f-sub" v-if="!memberDuty.length">无</view>
            </view>
          </view>
          <view class="f-warn" v-if="cur.mode !== 'full'">
            {{ CARE_MODE_NAME[cur.mode] }}按实际收成交付，产量会随日常管理浮动；下单时需确认《产出浮动告知书》。忙不过来的农活可以随时委托管家代办。
          </view>
        </view>
      </view>

      <!-- 基地 -->
      <view class="f-card" v-if="p.base">
        <view class="f-title">基地信息</view>
        <view class="f-kv"><text class="k">基地</text><text class="v">{{ p.base.name }}</text></view>
        <view class="f-kv"><text class="k">地址</text><text class="v">{{ p.base.address }}</text></view>
        <view class="f-kv"><text class="k">资质编号</text><text class="v">{{ p.base.certNo }}</text></view>
      </view>
      <view class="f-card" v-if="reviews.length">
        <view class="f-title">认养人评价</view>
        <view class="rv" v-for="r in reviews" :key="r.id">
          <view class="f-between"><text>{{ r.memberName }}</text><text class="f-tag orange">{{ r.score }} 分</text></view>
          <view class="f-sub">{{ r.content || '（未填写文字）' }}</view>
        </view>
      </view>
      <view class="f-bottom-space" />
    </view>

    <view class="f-bottom-bar" v-if="p">
      <view class="fav-btn" @tap="toggleFav"><view class="fav-star" :class="{ on: faved }">{{ faved ? '★' : '☆' }}</view><view class="f-sub">收藏</view></view>
      <view>
        <view class="f-sub">{{ cur ? CARE_MODE_NAME[cur.mode] : '请选择托管方式' }}</view>
        <view class="f-price big">{{ cur ? yuan(cur.price) : '' }}</view>
      </view>
      <button class="f-btn" :class="{ disabled: !cur || p.stock <= 0 }" @tap="buy">
        {{ p.stock <= 0 ? '已认养完' : '立即认养' }}
      </button>
    </view>
  </s-layout>
</template>

<script setup>
  import { computed, ref } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { CARE_MODE_NAME, yuan, call, contractTitle, needLogin } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const p = ref(null);
  const sel = ref('');
  const id = ref(0);
  const faved = ref(false);
  const reviews = ref([]);

  async function toggleFav() {
    if (needLogin()) return;
    const r = await call(FarmApi.toggleFavorite('product', id.value));
    if (r !== null) {
      faved.value = r;
      sheep.$helper.toast(r ? '已收藏' : '已取消收藏');
    }
  }

  const cur = computed(() => p.value?.careModes.find((m) => m.mode === sel.value && m.enabled));
  const farmDuty = computed(() => (cur.value?.duty || []).filter((d) => d.owner === 'farm').map((d) => d.item));
  const memberDuty = computed(() => (cur.value?.duty || []).filter((d) => d.owner === 'member').map((d) => d.item));

  function pick(m) {
    if (!m.enabled) {
      sheep.$helper.toast('这个商品暂未开放该托管方式');
      return;
    }
    sel.value = m.mode;
  }

  function toCompare() {
    sheep.$router.go('/pages/farm/product/compare', { id: id.value });
  }

  function buy() {
    if (!cur.value) return sheep.$helper.toast('请选择托管方式');
    if (p.value.stock <= 0) return sheep.$helper.toast('这个商品暂时没有空闲的地块');
    if (needLogin()) return;
    sheep.$router.go('/pages/farm/order/confirm', { id: id.value, mode: sel.value });
  }

  onLoad(async (options) => {
    id.value = Number(options.id || 1);
    const data = await call(FarmApi.getProduct(id.value));
    if (!data) return;
    p.value = data;
    reviews.value = (await call(FarmApi.getReviewList(id.value))) || [];
    if (sheep.$store('user').isLogin) faved.value = !!(await call(FarmApi.checkFavorite('product', id.value)));
    const preferred = data.careModes.find((m) => m.mode === options.mode && m.enabled);
    sel.value = (preferred || data.careModes.find((m) => m.enabled))?.mode || '';
  });
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .rv {
    padding: 14rpx 0;
    border-bottom: 2rpx solid $fline;
  }
  .fav-btn {
    text-align: center;
    margin-right: 24rpx;
  }
  .fav-star {
    font-size: 40rpx;
    line-height: 1;
    color: $fsub;
  }
  .fav-star.on {
    color: $fo;
  }
  .cover {
    width: 100%;
    height: 380rpx;
    border-radius: 20rpx;
    margin-bottom: 20rpx;
  }
  .title {
    font-size: 36rpx;
    font-weight: 700;
    margin-bottom: 12rpx;
  }
  .desc {
    font-size: 26rpx;
    line-height: 1.6;
  }
  .meta {
    margin-top: 16rpx;
    flex-wrap: wrap;
  }
  .modes {
    display: flex;
    gap: 16rpx;
  }
  .mode {
    flex: 1;
    border: 2rpx solid $fline;
    border-radius: 16rpx;
    padding: 20rpx 12rpx;
    text-align: center;
    background: #fafbfc;
  }
  .mode.active {
    border-color: $fg;
    background: $fgl;
  }
  .mode.off {
    opacity: 0.5;
  }
  .mode-name {
    font-size: 28rpx;
    font-weight: 600;
  }
  .mode-price {
    color: $fo;
    font-size: 34rpx;
    font-weight: 700;
    margin: 8rpx 0;
  }
  .off-text {
    color: $fsub;
    font-size: 26rpx;
  }
  .mode-todo {
    font-size: 20rpx;
    color: $fsub;
  }
  .mode-detail {
    margin-top: 24rpx;
    border-top: 2rpx dashed $fline;
    padding-top: 20rpx;
  }
  .slogan {
    font-size: 30rpx;
    color: $fgd;
    font-weight: 600;
    margin-bottom: 8rpx;
  }
  .duty {
    display: flex;
    gap: 16rpx;
    margin: 16rpx 0 20rpx;
  }
  .duty-col {
    flex: 1;
    background: #fafbfc;
    border-radius: 14rpx;
    padding: 16rpx;
  }
  .duty-head {
    font-size: 24rpx;
    font-weight: 600;
    margin-bottom: 8rpx;
  }
  .duty-head.farm {
    color: $fgd;
  }
  .duty-head.member {
    color: $fo;
  }
  .duty-item {
    font-size: 24rpx;
    padding: 4rpx 0;
  }
  .big {
    font-size: 40rpx;
  }
</style>
