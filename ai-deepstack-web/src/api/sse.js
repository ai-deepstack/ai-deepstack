import { getToken } from './http'

/**
 * POST SSE reader. Yields { event, data } for each frame.
 * @param {string} path
 * @param {object} body
 * @param {{ signal?: AbortSignal }} [opts]
 */
export async function* streamSse(path, body, opts = {}) {
  const res = await fetch(path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      Authorization: `Bearer ${getToken()}`
    },
    body: JSON.stringify(body ?? {}),
    signal: opts.signal
  })
  if (!res.ok) {
    let msg = `流式请求失败 (${res.status})`
    try {
      const j = await res.json()
      if (j.message) msg = j.message
    } catch (_) { /* ignore */ }
    throw new Error(msg)
  }
  if (!res.body) throw new Error('浏览器不支持流式响应')

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split(/\n\n/)
    buffer = parts.pop() || ''
    for (const part of parts) {
      if (!part.trim() || part.startsWith(':')) continue
      let event = 'message'
      const dataLines = []
      for (const raw of part.split(/\n/)) {
        const line = raw.replace(/\r$/, '')
        if (line.startsWith('event:')) event = line.slice(6).trim()
        else if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
      }
      yield { event, data: dataLines.join('\n') }
    }
  }
}

export function tryParseJson(data) {
  if (data == null || data === '') return null
  try {
    return JSON.parse(data)
  } catch {
    return data
  }
}
