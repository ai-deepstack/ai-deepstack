import { onUnmounted, ref } from 'vue'

/**
 * 手机端断点（与 styles.css 里 @media (max-width: 768px) 保持一致）。
 * 用于少数必须在 JS 里切换行为的地方：画布只读、面板折叠。
 */
export const MOBILE_QUERY = '(max-width: 768px)'

export function useIsMobile(query = MOBILE_QUERY) {
  const mql = window.matchMedia(query)
  const isMobile = ref(mql.matches)
  const onChange = (e) => { isMobile.value = e.matches }
  mql.addEventListener('change', onChange)
  onUnmounted(() => mql.removeEventListener('change', onChange))
  return isMobile
}
