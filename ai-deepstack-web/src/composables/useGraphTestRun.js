import { onUnmounted, ref } from 'vue'
import { get, post } from '../api/http'
import { GraphRunStatus } from '../constants/enums'

/**
 * 图试跑：起运行、轮询终态、终止。轮询用令牌作废在途响应，
 * 停止 / 重跑 / 卸载后到达的过期响应一律丢弃。
 * @param {object} ctx
 * @param {import('vue').Ref} ctx.agentId
 * @param {() => Promise<void>} ctx.save 试跑前先落盘草稿
 * @param {import('vue').Ref} ctx.error
 * @param {import('vue').Ref} ctx.okMsg
 * @returns {{ testDialogVisible, testing, cancelling, testMessage, testUserId, testResult, currentRunId, openTestDialog, closeTestDialog, runTest, stopPoll, cancelTest }}
 */
export function useGraphTestRun({ agentId, save, error, okMsg }) {
  const testDialogVisible = ref(false)
  const testing = ref(false)
  const cancelling = ref(false)
  const testMessage = ref('你好，请做一次图试跑。')
  const testUserId = ref('')
  const testResult = ref('')
  const currentRunId = ref(null)
  let pollTimer = null
  let pollInFlight = false // 单飞：上一次请求未返回时跳过本轮
  let pollAttempts = 0
  let pollSeq = 0 // 令牌：停止 / 重跑时自增，用于丢弃过期响应
  const POLL_MAX_ATTEMPTS = 120 // 上限约 60s，避免运行无终态时永久轮询

  /** 停止轮询，并作废在途响应。 */
  function stopPoll() {
    pollSeq += 1
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  function openTestDialog() {
    testDialogVisible.value = true
    testResult.value = ''
  }

  function closeTestDialog() {
    testDialogVisible.value = false
    stopPoll()
  }

  async function runTest() {
    if (!testMessage.value.trim()) {
      error.value = '请输入消息'
      return
    }
    testing.value = true
    error.value = ''
    testResult.value = ''
    currentRunId.value = null
    try {
      await save()
      if (error.value) {
        testing.value = false
        return
      }
      const startRes = await post(`/api/agents/${agentId.value}/graph/test/start`, {
        message: testMessage.value,
        userId: testUserId.value || undefined
      })
      const runId = startRes.data?.runId
      currentRunId.value = runId
      stopPoll()
      const mySeq = pollSeq
      pollAttempts = 0
      pollTimer = setInterval(async () => {
        if (pollInFlight) return
        pollInFlight = true
        try {
          pollAttempts++
          const res = await get(`/api/agents/runs/${runId}`)
          if (mySeq !== pollSeq) return // 已停止 / 重跑，丢弃过期响应
          const data = res.data
          if (!data || data.status === GraphRunStatus.RUNNING) {
            if (pollAttempts >= POLL_MAX_ATTEMPTS) {
              stopPoll()
              testing.value = false
              testResult.value = 'ERROR: 轮询超时，运行仍未结束'
            }
            return
          }
          stopPoll()
          testing.value = false
          testResult.value = JSON.stringify(data, null, 2)
        } catch (err) {
          if (mySeq !== pollSeq) return
          stopPoll()
          testing.value = false
          testResult.value = 'ERROR: ' + err.message
        } finally {
          pollInFlight = false
        }
      }, 500)
    } catch (e) {
      testing.value = false
      error.value = e.message
    }
  }

  async function cancelTest() {
    if (!currentRunId.value) return
    cancelling.value = true
    try {
      await post(`/api/agents/runs/${currentRunId.value}/cancel`)
      okMsg.value = '已发送终止请求'
    } catch (e) {
      error.value = e.message
    } finally {
      cancelling.value = false
    }
  }

  // 卸载务必停表，避免组件销毁后仍在轮询
  onUnmounted(stopPoll)

  return {
    testDialogVisible,
    testing,
    cancelling,
    testMessage,
    testUserId,
    testResult,
    currentRunId,
    openTestDialog,
    closeTestDialog,
    runTest,
    stopPoll,
    cancelTest
  }
}
