import { ref } from 'vue'
import { listTerms } from '@/api/knowledge'

/**
 * 医学术语词表加载。此前四个页面各自复制同一段 try/catch；
 * 失败兜底值也统一在这里维护。
 */
export function useTerms() {
  const terms = ref<string[]>([])
  const FALLBACK = ['高血压', '糖尿病', '二甲双胍', '阿司匹林', '氨氯地平']
  async function loadTerms() {
    try {
      terms.value = (await listTerms()).data || []
    } catch {
      terms.value = FALLBACK
    }
  }
  return { terms, loadTerms }
}
