/**
 * 认养文案合规校验（R-2；文档 12 第 6 节 C1、第 11.4 节）
 * 认养是实物交付，不能写成理财：命中金融类词语就拦截，并给出改写建议。
 * 这张词表本身会出现被禁的词，R-10 的「保X」grep 检查不含本文件。
 */
export const BANNED_WORDS = ['收益', '回报', '利息', '稳赚', '返利', '回购', '分红', '保底', '年化', '投资', '拉新提成']

export const SUGGESTION = '建议改为实物交付的说法，例如「约定交付 240 斤，不足部分按同等品质补发」'

export function findBannedWords(...texts: (string | null | undefined)[]): string[] {
  const all = texts.filter(Boolean).join('\n')
  return BANNED_WORDS.filter((w) => all.includes(w))
}

export function bannedMessage(words: string[]): string {
  return `文案含有金融类违禁词：${words.join('、')}。${SUGGESTION}`
}
