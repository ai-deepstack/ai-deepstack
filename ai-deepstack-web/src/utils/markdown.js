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

/** 代码块 / 行内代码：预处理加粗时要跳过这些片段 */
const CODE_SEGMENT = /(```[\s\S]*?```|~~~[\s\S]*?~~~|`[^`\n]*`)/g

/**
 * 中文标点紧贴 ** 时（如 `但**「龙」…**，`），CommonMark 的左/右边界规则会拒绝加粗，
 * 表现为原样输出 **。这里把成对的加粗直接提升为 <strong>，代码片段保持原样。
 */
function normalizeBold(text) {
  return text
    .split(CODE_SEGMENT)
    .map((part, index) => (index % 2 === 1
      ? part
      : part.replace(/\*\*(?=\S)([^\n]*?\S)\*\*/g, '<strong>$1</strong>')))
    .join('')
}

/** 宽表格放进可横向滚动的容器，避免把气泡撑破 */
function wrapTables(html) {
  return html
    .replace(/<table>/g, '<div class="md-table-wrap"><table>')
    .replace(/<\/table>/g, '</table></div>')
}

/**
 * 渲染并净化 Markdown。流式过程中允许不完整语法。
 * @param {string} text
 * @returns {string} 可安全用于 v-html 的 HTML
 */
export function renderMarkdown(text) {
  if (!text) return ''
  const html = marked.parse(normalizeBold(text), { async: false })
  const safe = DOMPurify.sanitize(html, { USE_PROFILES: { html: true } })
  return wrapTables(safe)
}
