import type { Directive } from 'vue'

/**
 * 入场揭示。元素进入视口时加 .in，触发 styles/index.css 里
 * .reveal → .reveal.in 的位移 + 去模糊过渡。
 *
 * 用 IntersectionObserver 而不是 scroll 事件监听：后者每帧回调，
 * 会持续触发重排，移动端直接掉帧。
 *
 * 值可传延迟毫秒（v-reveal="80"）做交错，只触发一次，之后取消观察。
 */
const observers = new WeakMap<Element, IntersectionObserver>()

export const vReveal: Directive<HTMLElement, number | undefined> = {
  mounted(el, binding) {
    // 尊重系统「减少动效」：直接落位，不做位移与模糊
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      el.classList.add('reveal', 'in')
      return
    }
    el.classList.add('reveal')
    const delay = Number(binding.value) || 0
    const ob = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (!e.isIntersecting) continue
          setTimeout(() => el.classList.add('in'), delay)
          ob.unobserve(el)
        }
      },
      // 略微提前触发，避免元素卡在视口边缘时闪烁
      { rootMargin: '0px 0px -8% 0px', threshold: 0.05 },
    )
    ob.observe(el)
    observers.set(el, ob)
  },
  unmounted(el) {
    observers.get(el)?.disconnect()
    observers.delete(el)
  },
}
