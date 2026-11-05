<template>
  <div
    :class="prefixCls"
    class="relative h-[100%] lt-md:px-10px lt-sm:px-10px lt-xl:px-10px lt-xl:px-10px"
  >
    <div class="relative mx-auto h-full flex">
      <div
        :class="`${prefixCls}__left flex-1 relative p-30px lt-xl:hidden overflow-x-hidden overflow-y-auto`"
      >
        <!-- 左上角的 logo + 系统标题 -->
        <div :class="`${prefixCls}__brand relative flex items-center`">
          <img alt="" class="mr-10px h-48px w-48px" src="@/assets/imgs/farm-logo.svg" />
          <span class="text-20px font-bold">{{ underlineToHump(appStore.getTitle) }}</span>
        </div>
        <!-- 农场简图 + 欢迎语（纯 SVG，颜色见下方 --farm-* 变量，暗色模式另有一套） -->
        <div class="h-[calc(100%-60px)] flex items-center justify-center">
          <Transition appear enter-active-class="animate__animated animate__fadeIn">
            <div class="w-full max-w-520px text-center">
              <svg
                :class="`${prefixCls}__scene`"
                aria-hidden="true"
                viewBox="0 0 480 320"
                xmlns="http://www.w3.org/2000/svg"
              >
                <defs>
                  <clipPath id="farm-login-field">
                    <path d="M0 250 C120 222 300 226 480 246 L480 320 L0 320 Z" />
                  </clipPath>
                  <clipPath id="farm-login-scene">
                    <rect height="320" rx="24" width="480" />
                  </clipPath>
                </defs>
                <g clip-path="url(#farm-login-scene)">
                  <rect class="sky" height="320" width="480" />
                  <!-- 太阳和云 -->
                  <circle class="sun-halo" cx="388" cy="72" r="44" />
                  <circle class="sun" cx="388" cy="72" r="26" />
                  <rect class="cloud" height="18" rx="9" width="72" x="64" y="62" />
                  <rect class="cloud" height="14" rx="7" width="48" x="236" y="44" />
                  <!-- 远山、近坡 -->
                  <path class="hill-far" d="M0 186 C110 140 230 150 330 170 C400 184 450 160 480 150 L480 320 L0 320 Z" />
                  <path class="hill-mid" d="M0 214 C90 176 200 170 300 194 C370 211 430 204 480 190 L480 320 L0 320 Z" />
                  <!-- 谷仓 -->
                  <rect class="barn" height="46" width="64" x="88" y="160" />
                  <polygon class="barn-roof" points="80,163 120,130 160,163" />
                  <rect class="barn-light" height="10" width="12" x="114" y="144" />
                  <rect class="barn-light" height="26" width="24" x="108" y="180" />
                  <path class="barn-line" d="M108 180 L132 206 M132 180 L108 206" />
                  <!-- 树 -->
                  <rect class="trunk" height="16" width="4" x="198" y="188" />
                  <circle class="tree" cx="200" cy="182" r="14" />
                  <rect class="trunk" height="14" width="4" x="226" y="194" />
                  <circle class="tree tree-light" cx="228" cy="189" r="11" />
                  <rect class="trunk" height="16" width="4" x="420" y="186" />
                  <circle class="tree" cx="422" cy="180" r="14" />
                  <!-- 温室大棚 -->
                  <path class="greenhouse" d="M296 216 L296 192 Q338 160 380 192 L380 216 Z" />
                  <path class="greenhouse-line" d="M317 216 L317 180 M338 216 L338 176 M359 216 L359 180 M296 204 L380 204" />
                  <!-- 前景田块和垄沟 -->
                  <path class="field" d="M0 250 C120 222 300 226 480 246 L480 320 L0 320 Z" />
                  <g class="row" clip-path="url(#farm-login-field)">
                    <path d="M0 262 C120 236 300 240 480 258" />
                    <path d="M0 278 C120 254 300 258 480 274" />
                    <path d="M0 296 C120 274 300 278 480 292" />
                    <path d="M0 316 C120 296 300 300 480 312" />
                  </g>
                </g>
              </svg>
              <div :class="`${prefixCls}__welcome mt-30px text-3xl`">{{ t('login.welcome') }}</div>
              <div :class="`${prefixCls}__message mt-12px text-14px font-normal`">
                {{ t('login.message') }}
              </div>
            </div>
          </Transition>
        </div>
      </div>
      <div
        class="relative flex-1 p-30px dark:bg-[var(--login-bg-color)] lt-sm:p-10px overflow-x-hidden overflow-y-auto"
      >
        <!-- 右上角的主题、语言选择 -->
        <div
          class="flex items-center justify-between at-2xl:justify-end at-xl:justify-end"
          style="color: var(--el-text-color-primary)"
        >
          <div class="flex items-center at-2xl:hidden at-xl:hidden">
            <img alt="" class="mr-10px h-48px w-48px" src="@/assets/imgs/farm-logo.svg" />
            <span class="text-20px font-bold">{{ underlineToHump(appStore.getTitle) }}</span>
          </div>
          <div class="flex items-center justify-end space-x-10px h-48px">
            <ThemeSwitch />
            <LocaleDropdown />
          </div>
        </div>
        <!-- 右边的登录界面 -->
        <Transition appear enter-active-class="animate__animated animate__bounceInRight">
          <div
            class="m-auto h-[calc(100%-60px)] w-[100%] flex items-center at-2xl:max-w-500px at-lg:max-w-500px at-md:max-w-500px at-xl:max-w-500px"
          >
            <!-- 账号登录 -->
            <LoginForm class="m-auto h-auto p-20px lt-xl:(rounded-3xl light:bg-white)" />
            <!-- 手机登录 -->
            <MobileForm class="m-auto h-auto p-20px lt-xl:(rounded-3xl light:bg-white)" />
            <!-- 二维码登录 -->
            <QrCodeForm class="m-auto h-auto p-20px lt-xl:(rounded-3xl light:bg-white)" />
            <!-- 注册 -->
            <RegisterForm class="m-auto h-auto p-20px lt-xl:(rounded-3xl light:bg-white)" />
            <!-- 三方登录 -->
            <SSOLoginVue class="m-auto h-auto p-20px lt-xl:(rounded-3xl light:bg-white)" />
            <!-- 忘记密码 -->
            <ForgetPasswordForm class="m-auto h-auto p-20px lt-xl:(rounded-3xl light:bg-white)" />
          </div>
        </Transition>
      </div>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { underlineToHump } from '@/utils'

import { useDesign } from '@/hooks/web/useDesign'
import { useAppStore } from '@/store/modules/app'
import { ThemeSwitch } from '@/layout/components/ThemeSwitch'
import { LocaleDropdown } from '@/layout/components/LocaleDropdown'

import {
  LoginForm,
  MobileForm,
  QrCodeForm,
  RegisterForm,
  SSOLoginVue,
  ForgetPasswordForm
} from './components'

defineOptions({ name: 'Login' })

const { t } = useI18n()
const appStore = useAppStore()
const { getPrefixCls } = useDesign()
const prefixCls = getPrefixCls('login')
</script>

<style lang="scss" scoped>
$prefix-cls: #{$namespace}-login;

.#{$prefix-cls} {
  overflow: auto;

  &__left {
    /* 农场简图配色：浅色模式 */
    --farm-bg: linear-gradient(180deg, #eef6ea 0%, #f7faf3 100%);
    --farm-text: #1f3d24;
    --farm-text-sub: #4f6b52;
    --farm-sky: #e3f0dc;
    --farm-sun: #f2c14e;
    --farm-cloud: #ffffff;
    --farm-hill-far: #c4dfb6;
    --farm-hill-mid: #9fcb8e;
    --farm-field: #6fae5f;
    --farm-row: #5a9a4c;
    --farm-tree: #3f8a45;
    --farm-tree-light: #5aa25c;
    --farm-trunk: #7a5a3c;
    --farm-barn: #b8573f;
    --farm-barn-roof: #8a3d2c;
    --farm-barn-light: #f6efe4;
    --farm-greenhouse: rgb(255 255 255 / 70%);
    --farm-greenhouse-line: #8fbf86;

    background: var(--farm-bg);
    color: var(--farm-text);
  }

  &__scene {
    display: block;
    width: 100%;
    height: auto;
    filter: drop-shadow(0 12px 24px rgb(46 125 50 / 12%));

    .sky {
      fill: var(--farm-sky);
    }

    .sun {
      fill: var(--farm-sun);
    }

    .sun-halo {
      fill: var(--farm-sun);
      opacity: 0.22;
    }

    .cloud {
      fill: var(--farm-cloud);
      opacity: 0.85;
    }

    .hill-far {
      fill: var(--farm-hill-far);
    }

    .hill-mid {
      fill: var(--farm-hill-mid);
    }

    .field {
      fill: var(--farm-field);
    }

    .row path {
      fill: none;
      stroke: var(--farm-row);
      stroke-width: 5;
      stroke-linecap: round;
    }

    .tree {
      fill: var(--farm-tree);
    }

    .tree-light {
      fill: var(--farm-tree-light);
    }

    .trunk {
      fill: var(--farm-trunk);
    }

    .barn {
      fill: var(--farm-barn);
    }

    .barn-roof {
      fill: var(--farm-barn-roof);
    }

    .barn-light {
      fill: var(--farm-barn-light);
    }

    .barn-line {
      fill: none;
      stroke: var(--farm-barn);
      stroke-width: 2;
    }

    .greenhouse {
      fill: var(--farm-greenhouse);
      stroke: var(--farm-greenhouse-line);
      stroke-width: 2;
    }

    .greenhouse-line {
      fill: none;
      stroke: var(--farm-greenhouse-line);
      stroke-width: 1.5;
    }
  }

  &__welcome {
    font-weight: 600;
    letter-spacing: 1px;
    color: var(--farm-text);
  }

  &__message {
    color: var(--farm-text-sub);
  }
}

/* 暗色模式：同一张图换成夜间低饱和配色 */
.dark .#{$prefix-cls}__left {
  --farm-bg: linear-gradient(180deg, #18241b 0%, #1d2a20 100%);
  --farm-text: #e6efe3;
  --farm-text-sub: #a9bba6;
  --farm-sky: #223326;
  --farm-sun: #e8d9a0;
  --farm-cloud: #3a4d3e;
  --farm-hill-far: #2c4330;
  --farm-hill-mid: #345239;
  --farm-field: #3d6640;
  --farm-row: #335a36;
  --farm-tree: #2f5a33;
  --farm-tree-light: #3b6c3f;
  --farm-trunk: #5a4632;
  --farm-barn: #8a4636;
  --farm-barn-roof: #6a3328;
  --farm-barn-light: #d9cdb8;
  --farm-greenhouse: rgb(255 255 255 / 18%);
  --farm-greenhouse-line: #5f8a5c;
}

.dark .#{$prefix-cls}__scene {
  filter: none;
}
</style>

<style lang="scss">
.dark .login-form {
  .el-divider__text {
    background-color: var(--login-bg-color);
  }

  .el-card {
    background-color: var(--login-bg-color);
  }
}
</style>
