<!-- 收货地址（原型 07 address）：列表、新增、编辑、删除、设默认；?select=1 时点选返回 -->
<template>
  <s-layout title="收货地址">
    <demo-badge />
    <view class="f-page">
      <view class="f-card" v-for="a in list" :key="a.id || 'p'" @tap="choose(a)">
        <view class="f-between">
          <text class="nm">{{ a.name }} {{ a.mobile }}</text>
          <text class="f-tag" v-if="a.isDefault">默认</text>
        </view>
        <view class="dt">{{ a.detail }}</view>
        <view class="f-row acts">
          <text class="f-sub" @tap.stop="edit(a)">编辑</text>
          <text class="f-sub del" v-if="a.id" @tap.stop="remove(a)">删除</text>
        </view>
      </view>
      <view v-if="loaded && !list.length" class="f-empty"><view class="f-empty-title">还没有收货地址</view></view>
      <view class="f-bottom-space" />
    </view>
    <view class="f-bottom-bar"><button class="f-btn block" @tap="edit(null)">新增收货地址</button></view>

    <su-popup :show="show" round="10" showClose @close="show = false">
      <view class="pop">
        <view class="pop-title">{{ form.id ? '编辑地址' : '新增地址' }}</view>
        <view class="row"><text class="lb">收货人</text><input class="inp" v-model="form.name" placeholder="姓名" /></view>
        <view class="row"><text class="lb">手机号</text><input class="inp" type="number" maxlength="11" v-model="form.mobile" placeholder="11 位手机号" /></view>
        <view class="row"><text class="lb">详细地址</text><input class="inp" v-model="form.detail" placeholder="省市区、街道、门牌号" /></view>
        <view class="row"><text class="lb">设为默认</text><switch :checked="form.isDefault" color="#22a06b" @change="form.isDefault = $event.detail.value" /></view>
        <button class="f-btn block" @tap="save">保存</button>
      </view>
    </su-popup>
  </s-layout>
</template>

<script setup>
  import { reactive, ref, watch } from 'vue';
  import { onLoad, onShow } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { call, needLogin } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const list = ref([]);
  const loaded = ref(false);
  const show = ref(false);
  const selecting = ref(false);
  const form = reactive({ id: 0, name: '', mobile: '', detail: '', isDefault: false });

  onLoad((opt) => {
    selecting.value = !!(opt && opt.select);
  });
  async function load() {
    if (needLogin()) return;
    list.value = (await call(FarmApi.getAddressList())) || [];
    loaded.value = true;
  }
  function edit(a) {
    // id 为 0 的是会员资料里带出的地址，保存时作为新地址入库
    Object.assign(form, a ? { id: a.id, name: a.name, mobile: a.mobile, detail: a.detail, isDefault: a.isDefault } : { id: 0, name: '', mobile: '', detail: '', isDefault: !list.value.length });
    show.value = true;
  }
  async function save() {
    const r = await call(FarmApi.saveAddress({ ...form }));
    if (r === null) return;
    show.value = false;
    sheep.$helper.toast('已保存');
    load();
  }
  function remove(a) {
    uni.showModal({
      title: '删除地址',
      content: '确定删除这条收货地址吗？',
      success: async (r) => {
        if (!r.confirm) return;
        await call(FarmApi.deleteAddress(a.id));
        load();
      },
    });
  }
  function choose(a) {
    if (!selecting.value) return;
    uni.setStorageSync('farm_picked_address', a);
    uni.navigateBack();
  }
  onShow(load);
  watch(
    () => sheep.$store('user').isLogin,
    (v) => v && load(),
  );
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .nm {
    font-size: 30rpx;
    font-weight: 600;
  }
  .dt {
    margin: 10rpx 0 14rpx;
    color: $ftx;
  }
  .acts .del {
    margin-left: 36rpx;
    color: $fr;
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
  .row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 18rpx 0;
    border-bottom: 2rpx solid $fline;
  }
  .lb {
    width: 160rpx;
    color: $fsub;
    font-size: 26rpx;
  }
  .inp {
    flex: 1;
    text-align: right;
    font-size: 28rpx;
  }
  .pop .f-btn {
    margin-top: 28rpx;
  }
</style>
