/**
 * FAQ 常见问题数据
 */
export interface FaqItem {
  /** 问题标题 */
  title: string
  /** 问题答案 */
  content: string
}

export interface FaqCategory {
  /** 分类图标 */
  icon: string
  /** 分类标题 */
  title: string
  /** 问题列表 */
  childList: FaqItem[]
}

/** FAQ 数据列表 */
export const faqList: FaqCategory[] = [
  {
    // farmer-saas: 换成管家端的常见问题
    icon: 'help-circle',
    title: '管家常见问题',
    childList: [
      {
        title: '打卡要拍几张照片？',
        content: '至少 1 张、最多 9 张现场照片。系统会核对拍摄时间、定位是否在基地范围内、是否和以前的照片重复。',
      },
      {
        title: '打卡被驳回了怎么办？',
        content: '在工作台查看驳回原因，按要求重新完成农事并再次打卡。',
      },
      {
        title: '代管工单怎么接？',
        content: '在「代管工单 → 工单池」里点「接单」，任务会出现在今日任务里，完成后打卡即可。',
      },
      {
        title: '认养人自己没做的任务要我去做吗？',
        content: '不用代替执行。认养人可以在 C 端「委托管家代办」，委托后会生成代管工单。',
      },
    ],
  },
  {
    icon: 'exclamation-circle',
    title: '其他问题',
    childList: [
      {
        title: '如何退出登录？',
        content: '请点击 [我的] - [退出登录] 即可退出登录。',
      },
      {
        title: '如何修改用户头像？',
        content: '请点击 [我的] - [个人资料] - [选择头像] 即可更换用户头像。',
      },
      {
        title: '如何修改登录密码？',
        content: '请点击 [我的] - [账号安全] - [修改密码] 即可修改登录密码。',
      },
      {
        title: '如何切换用户？',
        content: '请先退出当前账号，然后使用其他账号重新登录即可。',
      },
    ],
  },
]
