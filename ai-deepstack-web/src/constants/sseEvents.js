/** 与后端 ChatSseEvents 对齐的 SSE 事件名。 */
export const ChatSseEvents = Object.freeze({
  CONVERSATION: 'conversation',
  MESSAGE: 'message',
  CARD: 'card',
  CARD_STATUS: 'card_status',
  CARD_ACTION_RESULT: 'card_action_result',
  AGENT_NODE_START: 'agent_node_start',
  AGENT_NODE_COMPLETE: 'agent_node_complete',
  WORKFLOW_COMPLETE: 'workflow_complete',
  AGENT_EVENT: 'agent_event',
  AGENT_ERROR: 'agent_error',
  USAGE: 'usage',
  DONE: 'done',
  ERROR: 'error'
})

/** 与后端 CardStatusPhases 对齐。 */
export const CardStatusPhases = Object.freeze({
  GENERATING: 'generating',
  FAILED: 'failed'
})

/** 与后端 CardStatusFields 对齐。 */
export const CardStatusFields = Object.freeze({
  PHASE: 'phase',
  TOOL_NAME: 'toolName',
  MESSAGE: 'message',
  NODE_ID: 'nodeId',
  NODE_NAME: 'nodeName'
})

/** 与后端 AgentRunUsageKeys 对齐。 */
export const AgentRunUsageKeys = Object.freeze({
  LATENCY_MS: 'latencyMs',
  FIRST_TOKEN_LATENCY_MS: 'firstTokenLatencyMs',
  PROMPT_TOKENS: 'promptTokens',
  COMPLETION_TOKENS: 'completionTokens',
  TOTAL_TOKENS: 'totalTokens',
  RUN_ID: 'runId',
  TRACE_ID: 'traceId',
  TRACE_MODE: 'traceMode'
})
