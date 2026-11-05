<script lang="tsx">
import { computed, defineComponent, unref } from 'vue'
import { useAppStore } from '@/store/modules/app'
import { Backtop } from '@/components/Backtop'
import { Setting } from '@/layout/components/Setting'
import { useRenderLayout } from './components/useRenderLayout'
import { useDesign } from '@/hooks/web/useDesign'
import { getLayoutRenderMode } from '@/utils/layout'
import FeedbackDock from '@/views/farm/feedback/components/FeedbackDock.vue' // farmer-saas: 全局问题反馈入口（R-12）

// farmer-saas: 演示数据角标（R-7），只在 mock/demo 环境变量为 true 时渲染
const showMockBadge = import.meta.env.VITE_MOCK_BADGE === 'true'

const { getPrefixCls } = useDesign()

const prefixCls = getPrefixCls('layout')

const appStore = useAppStore()

// 是否是移动端
const mobile = computed(() => appStore.getMobile)

// 菜单折叠
const collapse = computed(() => appStore.getCollapse)

const layout = computed(() => appStore.getLayout)

const handleClickOutside = () => {
  appStore.setCollapse(true)
}

const renderLayout = () => {
  switch (getLayoutRenderMode(unref(layout))) {
    case 'classic':
      const { renderClassic } = useRenderLayout()
      return renderClassic()
    case 'topLeft':
      const { renderTopLeft } = useRenderLayout()
      return renderTopLeft()
    case 'top':
      const { renderTop } = useRenderLayout()
      return renderTop()
    case 'cutMenu':
      const { renderCutMenu } = useRenderLayout()
      return renderCutMenu()
    default:
      break
  }
}

export default defineComponent({
  name: 'Layout',
  setup() {
    return () => (
      <section
        class={[
          prefixCls,
          `${prefixCls}__${layout.value}`,
          `${prefixCls}__${getLayoutRenderMode(layout.value)}`,
          'w-[100%] h-[100%] relative'
        ]}
      >
        {mobile.value && !collapse.value ? (
          <div
            class="absolute left-0 top-0 z-99 h-full w-full bg-[var(--el-color-black)] opacity-30"
            onClick={handleClickOutside}
          ></div>
        ) : undefined}

        {renderLayout()}

        <Backtop></Backtop>

        <Setting></Setting>

        {/* farmer-saas: 问题反馈浮动按钮 + 演示数据角标 */}
        <FeedbackDock></FeedbackDock>
        {showMockBadge ? <div class="farm-mock-badge">演示数据</div> : undefined}
      </section>
    )
  }
})
</script>

<style lang="scss" scoped>
$prefix-cls: #{$namespace}-layout;

.#{$prefix-cls} {
  background-color: var(--app-content-bg-color);
}

/* farmer-saas: 演示数据角标（R-7） */
.farm-mock-badge {
  position: fixed;
  top: 0;
  right: 0;
  z-index: 3000;
  padding: 0 8px;
  font-size: 11px;
  line-height: 15px;
  color: #fff;
  pointer-events: none;
  background: #e6a23c;
  border-radius: 0 0 0 6px;
  opacity: 0.92;
}
</style>
