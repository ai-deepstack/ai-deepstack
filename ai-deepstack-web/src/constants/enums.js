/** 与后端 kernel 枚举码对齐（只存码；展示优先用接口返回的 *Name）。 */

export const ModelType = Object.freeze({
  CHAT: 0,
  EMBEDDING: 1
})

/** 与 ModelVisibilityEnum 对齐：0 公共 / 1 私有 */
export const ModelVisibility = Object.freeze({
  PUBLIC: 0,
  PRIVATE: 1
})

export const OrchestrateMode = Object.freeze({
  CHAT: 0,
  GRAPH: 1
})

export const ResponseFormat = Object.freeze({
  TEXT: 0,
  JSON: 1,
  JSON_SCHEMA: 2
})

export const TraceMode = Object.freeze({
  NONE: 0,
  RECORD: 1,
  STREAM: 2
})

export const GraphRunStatus = Object.freeze({
  RUNNING: 0,
  SUCCESS: 1,
  FAILED: 2,
  CANCELLED: 3,
  WAITING_HUMAN: 4
})

export const ToolSourceType = Object.freeze({
  LOCAL: 0,
  MCP: 1
})

export const DocSourceType = Object.freeze({
  MANUAL: 0,
  FILE: 1,
  URL: 2
})

export const McpTransport = Object.freeze({
  SSE: 0,
  STDIO: 1
})

export const CardAction = Object.freeze({
  CONFIRM: 0,
  EDIT: 1,
  REJECT: 2
})

export const ChatCardStatus = Object.freeze({
  PENDING: 0,
  CONFIRMED: 1,
  EDITED: 2,
  REJECTED: 3,
  EXPIRED: 4
})

/** 与 GenericConfirmCardProcessor.CARD_TYPE 对齐。 */
export const CardType = Object.freeze({
  GENERIC_CONFIRM: 'generic_confirm'
})

/** 资源启停（与 EnabledStatusEnum / YesNo 码一致）。 */
export const EnabledStatus = Object.freeze({
  DISABLED: 0,
  ENABLED: 1
})

export const modelTypeOptions = [
  { value: ModelType.CHAT, label: 'CHAT' },
  { value: ModelType.EMBEDDING, label: 'EMBEDDING' }
]

export const modelVisibilityOptions = [
  { value: ModelVisibility.PUBLIC, label: '公共' },
  { value: ModelVisibility.PRIVATE, label: '私有' }
]

export const orchestrateOptions = [
  { value: OrchestrateMode.CHAT, label: 'CHAT' },
  { value: OrchestrateMode.GRAPH, label: 'GRAPH' }
]

export const transportOptions = [
  { value: McpTransport.SSE, label: 'SSE' },
  { value: McpTransport.STDIO, label: 'STDIO' }
]

/** 将卡片动作归一为 CardAction 数字码（只认数字，不接受历史字符串）。 */
export function toCardActionCode(raw) {
  if (typeof raw === 'number' && Number.isFinite(raw)) return raw
  const n = Number(raw)
  if (Number.isFinite(n)) return n
  return CardAction.CONFIRM
}
