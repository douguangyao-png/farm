import type { RouteMeta } from 'vue-router'
import { Icon } from '@/components/Icon'
import { useI18n } from '@/hooks/web/useI18n'
import { useFarmNavStore } from '@/store/modules/farmNav'

export const useRenderMenuTitle = () => {
  const renderMenuTitle = (meta: RouteMeta, fullPath?: string) => {
    const badge = fullPath ? useFarmNavStore().badges[fullPath] : 0
    const { t } = useI18n()
    const { title = 'Please set title', icon } = meta

    return icon ? (
      <>
        <Icon icon={meta.icon}></Icon>
        <span class="v-menu__title overflow-hidden text-ellipsis whitespace-nowrap">
          {t(title as string)}
        </span>
        {badge > 0 ? <span class="farm-menu-badge">{badge}</span> : null}
      </>
    ) : (
      <span class="v-menu__title overflow-hidden text-ellipsis whitespace-nowrap">
        {t(title as string)}
      </span>
    )
  }

  return {
    renderMenuTitle
  }
}
