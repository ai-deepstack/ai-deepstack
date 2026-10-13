import { reactive } from 'vue'

const state = reactive({
  visible: false,
  title: '确认操作',
  message: '',
  confirmText: '确定',
  cancelText: '取消',
  danger: true
})

let resolver = null

/**
 * 主题确认框，返回 Promise&lt;boolean&gt;
 * @param {string|object} messageOrOpts
 * @param {object} [opts]
 */
export function confirmDialog(messageOrOpts, opts = {}) {
  const options =
    typeof messageOrOpts === 'string'
      ? { message: messageOrOpts, ...opts }
      : { ...messageOrOpts }

  state.title = options.title || '确认操作'
  state.message = options.message || ''
  state.confirmText = options.confirmText || '确定'
  state.cancelText = options.cancelText || '取消'
  state.danger = options.danger !== false
  state.visible = true

  // 若已有未决确认框，先让其以 false 结束，避免旧调用方永久挂起
  if (resolver) {
    const prev = resolver
    resolver = null
    prev(false)
  }

  return new Promise((resolve) => {
    resolver = resolve
  })
}

export function useConfirmState() {
  function resolve(ok) {
    state.visible = false
    if (resolver) {
      resolver(ok)
      resolver = null
    }
  }

  return {
    state,
    accept: () => resolve(true),
    dismiss: () => resolve(false)
  }
}
