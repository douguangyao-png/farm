<!-- 农事记录详情（原型 07 record）：照片、农事内容、环境读数、核验项、存证编号、向管家提问 -->
<template>
  <s-layout title="农事记录">
    <demo-badge />
    <view class="f-page" v-if="r">
      <view class="f-card">
        <view class="f-between">
          <text class="title">{{ r.title }}</text>
          <text class="f-tag">{{ r.kindName }}</text>
        </view>
        <view class="f-sub">地块 {{ r.assetCode }} · {{ fmtTime(r.createTime) }} · {{ r.stewardName }}</view>
        <view class="content">{{ r.content }}</view>
        <view class="photos" v-if="r.photos && r.photos.length">
          <image v-for="(ph, i) in r.photos" :key="i" class="photo" :src="ph" mode="aspectFill" @tap="preview(i)" />
        </view>
      </view>

      <view class="f-card">
        <view class="f-title">当时的环境</view>
        <view class="f-kv"><text class="k">气温</text><text class="v">{{ r.env ? r.env.airTemp + '℃' : '—' }}</text></view>
        <view class="f-kv"><text class="k">空气湿度</text><text class="v">{{ r.env ? r.env.airHumidity + '%' : '—' }}</text></view>
        <view class="f-kv"><text class="k">土壤湿度</text><text class="v">{{ r.env ? r.env.soilMoisture + '%' : '—' }}</text></view>
        <view class="f-kv"><text class="k">作业工时</text><text class="v f-sub">{{ r.workHours ? r.workHours + ' 小时' : '系统暂未采集' }}</text></view>
      </view>

      <view class="f-card">
        <view class="f-title">真实性核验</view>
        <view class="f-row wrap">
          <text class="f-tag" :class="r.checks && r.checks.gpsFence ? '' : 'orange'">{{ r.checks && r.checks.gpsFence ? '定位在地块内' : '定位待复核' }}</text>
          <text class="f-tag" :class="r.checks && r.checks.exifTime ? '' : 'orange'">{{ r.checks && r.checks.exifTime ? '拍摄时间已核验' : '拍摄时间待复核' }}</text>
          <text class="f-tag" :class="r.checks && r.checks.duplicate ? '' : 'orange'">{{ r.checks && r.checks.duplicate ? '非重复图片' : '图片重复待复核' }}</text>
        </view>
        <view class="f-kv"><text class="k">存证编号</text><text class="v">{{ r.evidenceNo }}</text></view>
        <view class="f-sub">存证编号为演示数据，正式版写入区块链存证哈希。</view>
      </view>

      <button class="f-btn ghost block" @tap="ask">向管家提问</button>
    </view>
  </s-layout>
</template>

<script setup>
  import { ref } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import FarmApi from '@/sheep/api/farm';
  import { call, fmtTime } from '@/pages/farm/common/farm';
  import DemoBadge from '@/pages/farm/components/demo-badge.vue';

  const r = ref(null);
  onLoad(async (opt) => {
    r.value = await call(FarmApi.getRecord(opt.id));
  });
  function preview(i) {
    uni.previewImage({ urls: r.value.photos, current: i });
  }
  function ask() {
    sheep.$router.go('/pages/farm/service/chat', { ref: r.value.title });
  }
</script>

<style lang="scss" scoped>
  @import '@/pages/farm/common/farm.scss';
  .title {
    font-size: 32rpx;
    font-weight: 600;
  }
  .content {
    margin: 16rpx 0;
    line-height: 1.6;
  }
  .photos {
    display: flex;
    flex-wrap: wrap;
  }
  .photo {
    width: 210rpx;
    height: 210rpx;
    border-radius: 12rpx;
    margin: 0 12rpx 12rpx 0;
  }
  .wrap {
    flex-wrap: wrap;
    margin-bottom: 12rpx;
  }
</style>
