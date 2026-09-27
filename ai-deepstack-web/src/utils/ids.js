/**
 * 主键 / 雪花 ID：后端 Long 统一以字符串下发，避免 JS 精度丢失。
 * @param {unknown} value
 * @returns {string}
 */
export function asId(value) {
  if (value == null || value === '') return ''
  return String(value)
}

/**
 * 是否同一 ID（字符串或数字均可比较）。
 * @param {unknown} a
 * @param {unknown} b
 */
export function sameId(a, b) {
  if (a == null || b == null || a === '' || b === '') return false
  return String(a) === String(b)
}
