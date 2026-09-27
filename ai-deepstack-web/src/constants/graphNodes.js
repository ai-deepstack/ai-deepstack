/** 编排节点图标与中文文案 */

export const NODE_TYPE_LABELS = {
  'start-node': '开始',
  'intent-node': '意图分类',
  'llm-node': '大模型',
  'rag-node': '知识检索',
  'tool-node': '工具调用',
  'condition-node': '条件分支',
  'memory-node': '长期记忆',
  'assign-node': '变量赋值',
  'end-node': '结束'
}

export const NODE_CATEGORY_LABELS = {
  basic: '基础',
  ai: '智能',
  tool: '工具',
  flow: '流程'
}

/** 节点库一行简述（宜短） */
export const NODE_TYPE_HINTS = {
  'start-node': '流程入口',
  'intent-node': '意图分流',
  'llm-node': '模型推理',
  'rag-node': '知识召回',
  'tool-node': '调用工具',
  'condition-node': '条件分流',
  'memory-node': '读写记忆',
  'assign-node': '写入变量',
  'end-node': '流程出口'
}

/** viewBox 0 0 24 24 */
export const NODE_ICONS = {
  'start-node': {
    color: '#3de0c5',
    filled: true,
    paths: ['M8 5v14l11-7z']
  },
  'intent-node': {
    color: '#7aa2ff',
    paths: [
      'M9.5 3.5h5l1 3.2h2.2a1.8 1.8 0 0 1 1.8 1.8v6.2a1.8 1.8 0 0 1-1.8 1.8H16l-1 3.2h-5l-1-3.2H6.8a1.8 1.8 0 0 1-1.8-1.8V8.5a1.8 1.8 0 0 1 1.8-1.8H8.5l1-3.2z',
      'M9.8 12h4.4M12 9.8v4.4'
    ]
  },
  'llm-node': {
    color: '#c9a227',
    paths: [
      'M5 7.5A2.5 2.5 0 0 1 7.5 5h8A2.5 2.5 0 0 1 18 7.5v5A2.5 2.5 0 0 1 15.5 15H11l-3.2 2.6V15H7.5A2.5 2.5 0 0 1 5 12.5v-5z',
      'M8.5 9.2h6M8.5 12h4'
    ]
  },
  'rag-node': {
    color: '#5ec8ff',
    paths: [
      'M6 4.5h10.5A1.5 1.5 0 0 1 18 6v13.2a.5.5 0 0 1-.78.41L15 18.1l-2.22 1.51a.5.5 0 0 1-.56 0L10 18.1 7.78 19.6A.5.5 0 0 1 7 19.2V6a1.5 1.5 0 0 1 1.5-1.5',
      'M9 8.5h6M9 11.5h6M9 14.5h3.5'
    ]
  },
  'tool-node': {
    color: '#f0a060',
    paths: [
      'M14.7 6.3a3.8 3.8 0 0 0-5.2 5.2L4.2 16.8 7.2 19.8l5.3-5.3a3.8 3.8 0 0 0 5.2-5.2l-2.6 2.6-2.5-2.5 2.6-2.6z'
    ]
  },
  'condition-node': {
    color: '#d080ff',
    paths: [
      'M12 3.5 20 12l-8 8.5L4 12 12 3.5z',
      'M12 8.5v4.2l2.2 2.2'
    ]
  },
  'memory-node': {
    color: '#6ec6ff',
    paths: [
      'M6.5 5.5h11A1.5 1.5 0 0 1 19 7v10.5a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 17.5V7A1.5 1.5 0 0 1 6.5 5.5z',
      'M8.5 9h7M8.5 12h7M8.5 15h4'
    ]
  },
  'assign-node': {
    color: '#8fd46a',
    paths: [
      'M5 16.8V19.5h2.7L16.8 10.9 14.1 8.2 5 16.8z',
      'M13 7.1l2.7 2.7 1.6-1.6a1.4 1.4 0 0 0 0-2l-.7-.7a1.4 1.4 0 0 0-2 0L13 7.1z'
    ]
  },
  'end-node': {
    color: '#ff7a7a',
    filled: true,
    paths: ['M7 7h10v10H7z']
  }
}

export function nodeTypeLabel(type, fallback) {
  return NODE_TYPE_LABELS[type] || fallback || type || ''
}

export function nodeCategoryLabel(category) {
  return NODE_CATEGORY_LABELS[category] || category || ''
}

export function nodeTypeHint(type) {
  return NODE_TYPE_HINTS[type] || ''
}

export function nodeIcon(type) {
  return NODE_ICONS[type] || {
    color: '#9aa4b2',
    paths: ['M12 4a8 8 0 1 1 0 16 8 8 0 0 1 0-16z', 'M12 8.5v4M12 16h.01']
  }
}
