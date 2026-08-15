export interface ChatPrompt {
  title: string
  desc: string
}

export const SUGGESTED_PROMPTS: ChatPrompt[] = [
  { title: '帮我写代码', desc: '写一个 Python 快速排序' },
  { title: '翻译文本', desc: '将一段中文翻译为英文' },
  { title: '制定计划', desc: '制定一周学习计划' },
  { title: '解释概念', desc: '什么是量子计算？' },
]
