<template>
  <view class="auth-container">
    <!-- 顶部 -->
    <Header />

    <!-- 表单区域 -->
    <view class="form-container">
      <!-- farmer-saas: 按用户名登录时不显示租户选择，租户由用户名确定 -->
      <TenantPicker
        v-if="!loginByUsername"
        ref="tenantPickerRef"
        :disabled="Boolean(socialBindingContext) || authLoading"
        :preferred-tenant-id="socialBindingContext?.tenantId"
      />
      <view v-if="socialBindingContext" class="mb-24rpx rounded-12rpx bg-[#e8f4ff] px-24rpx py-20rpx text-26rpx text-[#1890ff]">
        三方授权成功，请使用账号密码登录完成绑定
      </view>
      <view class="input-item">
        <wd-icon name="user" size="20px" color="#1890ff" />
        <wd-input
          v-model="formData.username"
          placeholder="请输入用户名"
          clearable
          clear-trigger="focus"
        />
      </view>
      <view class="input-item">
        <wd-icon name="lock" size="20px" color="#1890ff" />
        <wd-input
          v-model="formData.password"
          placeholder="请输入密码"
          clearable
          clear-trigger="focus"
          show-password
        />
      </view>
      <view v-if="captchaEnabled">
        <Verify
          ref="verifyRef"
          :captcha-type="captchaType"
          explain="向右滑动完成验证"
          :img-size="{ width: '300px', height: '150px' }"
          mode="pop"
          @success="verifySuccess"
        />
      </view>

      <!-- 登录按钮 -->
      <!-- farmer-saas: 按用户名登录（演示/真后端）时，验证码登录、忘记密码、第三方登录、注册都不开放 -->
      <view v-if="!loginByUsername" class="mb-2 mt-2 flex justify-between">
        <text v-if="!socialBindingContext && !authLoading" class="text-28rpx text-[#1890ff]" @click="goToSmsLogin">
          验证码登录
        </text>
        <text v-if="!authLoading" class="text-28rpx text-[#1890ff]" @click="goToForgetPassword">
          忘记密码？
        </text>
      </view>
      <wd-button block :disabled="authLoading" :loading="loading" type="primary" @click="handleLogin">
        登录
      </wd-button>

      <!-- farmer-saas: 演示账号（点一下自动填好） -->
      <view v-if="demoAccounts.length" class="mt-32rpx">
        <view class="mb-12rpx text-24rpx text-[#999]">演示账号（点一下自动填好，密码 demo123）</view>
        <view class="flex flex-wrap gap-12rpx">
          <view
            v-for="a in demoAccounts"
            :key="a.username"
            class="rounded-30rpx border border-[#1890ff] border-solid px-20rpx py-8rpx text-24rpx text-[#1890ff]"
            @click="useDemoAccount(a)"
          >
            {{ a.label }} {{ a.username }}
          </view>
        </view>
      </view>

      <!-- 第三方登录 -->
      <SocialLoginPanel
        v-if="!loginByUsername"
        v-model="socialBindingContext"
        v-model:loading="socialLoginLoading"
        :disabled="authLoading"
        :redirect-url="redirectUrl"
        :social-bind="Boolean(pageProps.socialBind)"
        :validate-tenant="validateTenant"
      />
      <!-- 创建账号 -->
      <view v-if="!loginByUsername && !socialBindingContext && !authLoading" class="mt-40rpx flex items-center justify-center">
        <text class="text-28rpx text-[#666]">还没有账号？</text>
        <text class="text-28rpx text-[#1890ff]" @click="goToRegister">
          创建账号
        </text>
      </view>
    </view>
  </view>
</template>

<script lang="ts" setup>
import type { SocialLoginBindingContext } from '@/utils/social-login'
import { useToast } from '@wot-ui/ui/components/wd-toast'
import { computed, reactive, ref } from 'vue'
import {
  CODE_LOGIN_PAGE,
  FORGET_PASSWORD_PAGE,
  REGISTER_PAGE,
} from '@/router/config'
import { useTokenStore } from '@/store/token'
import { ensureDecodeURIComponent, redirectAfterLogin } from '@/utils'
import Header from './components/header.vue'
import SocialLoginPanel from './components/social-login-panel.vue'
import TenantPicker from './components/tenant-picker.vue'
import { getTenantIdByUsername } from '@/api/login'
import { useUserStore } from '@/store/user'
import Verify from './components/verifition/verify.vue'

defineOptions({
  name: 'LoginPage',
  style: {
    navigationStyle: 'custom',
  },
})

const pageProps = defineProps<{
  redirect?: string
  socialBind?: string
}>()

definePage({
  style: {
    navigationStyle: 'custom',
  },
})

const toast = useToast()
const loading = ref(false) // 表单提交状态
const redirectUrl = ref(pageProps.redirect ? ensureDecodeURIComponent(pageProps.redirect) : undefined) // 重定向地址
const tenantPickerRef = ref<InstanceType<typeof TenantPicker>>() // 租户选择器引用
const captchaEnabled = import.meta.env.VITE_APP_CAPTCHA_ENABLE === 'true' // 验证码开关
const verifyRef = ref()
const captchaType = ref('blockPuzzle') // 滑块验证码 blockPuzzle|clickWord

const formData = reactive({
  // edit by 认养农场：演示包（VITE_MOCK_BADGE=true）也预填演示账号
  username: import.meta.env.DEV || import.meta.env.VITE_MOCK_BADGE === 'true' ? import.meta.env.VITE_APP_DEFAULT_LOGIN_USERNAME || '' : '',
  password: import.meta.env.DEV || import.meta.env.VITE_MOCK_BADGE === 'true' ? import.meta.env.VITE_APP_DEFAULT_LOGIN_PASSWORD || '' : '',
  captchaVerification: '', // 验证码校验值
}) // 表单数据
// farmer-saas: 演示账号（种子 mock-server/seed/users.json，kind=steward）
const demoAccounts = import.meta.env.VITE_MOCK_BADGE === 'true' || import.meta.env.VITE_DEMO_ACCOUNTS === 'true'
  ? [
      { label: '张管家 · 青山农场', username: 'zhangguanjia', password: 'demo123' },
      { label: '李管家 · 青山农场', username: 'liguanjia', password: 'demo123' },
      { label: '吕管家 · 绿野农庄', username: 'lvguanjia', password: 'demo123' },
    ]
  : []
function useDemoAccount(a: { username: string, password: string }) {
  formData.username = a.username
  formData.password = a.password
}
const socialBindingContext = ref<SocialLoginBindingContext>() // 待绑定的三方授权上下文
const socialLoginLoading = ref(false) // 三方登录进行状态
const authLoading = computed(() => loading.value || socialLoginLoading.value) // 任一登录流程进行状态
const socialAuth = computed(() => { // 待绑定的三方授权参数
  const context = socialBindingContext.value
  return context
    ? {
        socialType: context.socialType,
        socialCode: context.socialCode,
        socialState: context.socialState,
      }
    : undefined
})

/** 获取验证码 */
async function getCode() {
  // 情况一，未开启：则直接登录
  if (!captchaEnabled) {
    await verifySuccess({})
  } else {
    // 情况二，已开启：则展示验证码；只有完成验证码的情况，才进行登录
    // 弹出验证码
    verifyRef.value.show()
  }
}

/** farmer-saas: 登录不选租户，按用户名确定租户（env/.env.mock、.env.demo 打开） */
const loginByUsername = import.meta.env.VITE_APP_LOGIN_BY_USERNAME === 'true'

/** 登录处理 */
async function handleLogin() {
  if (authLoading.value) {
    return
  }
  if (!validateTenant()) {
    return
  }
  if (!formData.username) {
    toast.warning('请输入用户名')
    return
  }
  if (!formData.password) {
    toast.warning('请输入密码')
    return
  }
  // farmer-saas: 按用户名找租户（用户 2026-11-05：不选租户，用户名密码登录）
  if (loginByUsername) {
    const tenantId = await getTenantIdByUsername(formData.username)
    if (!tenantId) {
      toast.error('账号或密码不正确')
      return
    }
    useUserStore().setTenantId(tenantId)
  }
  await getCode()
}

/** 验证成功后登录 */
async function verifySuccess(params: any) {
  loading.value = true
  try {
    // 调用登录接口
    const tokenStore = useTokenStore()
    formData.captchaVerification = params.captchaVerification
    await tokenStore.login({
      type: 'username',
      ...formData,
      ...socialAuth.value,
    })
    // 处理跳转
    redirectAfterLogin(socialBindingContext.value?.redirect || redirectUrl.value)
  } finally {
    loading.value = false
  }
}

/** 跳转到注册页面 */
function goToRegister() {
  uni.navigateTo({ url: REGISTER_PAGE })
}

/** 跳转到验证码登录 */
function goToSmsLogin() {
  uni.navigateTo({ url: CODE_LOGIN_PAGE })
}

/** 跳转到忘记密码 */
function goToForgetPassword() {
  uni.navigateTo({ url: FORGET_PASSWORD_PAGE })
}

/** 校验当前租户 */
function validateTenant() {
  if (loginByUsername) return true // farmer-saas: 租户由用户名确定
  return Boolean(tenantPickerRef.value?.validate())
}
</script>

<style lang="scss" scoped>
@import './styles/auth.scss';
</style>
