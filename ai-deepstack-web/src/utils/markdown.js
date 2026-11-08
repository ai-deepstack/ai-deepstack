import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({
  gfm: true,
  breaks: true
})

DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A' && node.getAttribute('href')) {
    node.setAttribute('target', '_blank')
    node.setAttribute('rel', 'noopener noreferrer')
  }
})

/**
 * 渲染并净化 Markdown。流式过程中允许不完整语法。
 * @param {string} text
 * @returns {string} 可安全用于 v-html 的 HTML
 */
export function renderMarkdown(text) {
  if (!text) return ''
  const html = marked.parse(text, { async: false })
  return DOMPurify.sanitize(html, { USE_PROFILES: { html: true } })
}
