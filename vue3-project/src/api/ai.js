import apiConfig from '@/config/api'

const authHeaders = () => {
  const token = localStorage.getItem('token')
  const adminToken = localStorage.getItem('admin_token')
  return {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(!token && adminToken ? { Authorization: `Bearer ${adminToken}` } : {})
  }
}

export const travelAiApi = {
  /**
   * 使用 fetch 读取 POST SSE。原生 EventSource 只能可靠支持 GET，无法携带当前 JSON 请求体，
   * 因此这里保留手动协议解析，并通过 options.signal 接收页面级 AbortController。
   */
  async chat(payload, handlers = {}, options = {}) {
    const response = await fetch(`${apiConfig.baseURL}/ai/travel/chat`, {
      method: 'POST',
      headers: {
        // 成功时返回 SSE；参数校验或限流发生在建立事件流之前，需要允许后端返回 JSON 错误体。
        'Accept': 'text/event-stream, application/json',
        'Cache-Control': 'no-cache',
        'Content-Type': 'application/json',
        ...authHeaders()
      },
      body: JSON.stringify(payload),
      signal: options.signal
    })

    if (!response.ok || !response.body) {
      let message = `流式请求失败(${response.status})`
      try {
        // Agent 的参数校验、限流和依赖故障发生在 SSE 建立前，此时后端返回普通 JSON 错误体。
        const errorBody = await response.json()
        if (errorBody?.message) {
          message = errorBody.message
        }
      } catch (e) {
        // 代理或网关可能返回非 JSON 错误页，保留包含 HTTP 状态的默认文案。
      }
      throw new Error(message)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    // 网络分片可能从任意 UTF-8 字节或 SSE 行中间断开，必须用流式 decoder 和跨 read 缓冲区拼接。
    let buffer = ''

    // done 与 error 都是协议终态；仅依赖 ReadableStream 的 done 会把代理截断误判为正常完成。
    let terminalEventReceived = false

    const parseJson = (data, fallback) => {
      try {
        return JSON.parse(data || '')
      } catch (_) {
        return fallback
      }
    }

    const dispatchEvent = (eventName, data) => {
      if (eventName === 'meta' && handlers.onMeta) {
        handlers.onMeta(parseJson(data, {}))
        return
      }
      if (eventName === 'chunk' && handlers.onChunk) {
        handlers.onChunk(data)
        return
      }
      if (eventName === 'refs' && handlers.onRefs) {
        handlers.onRefs(parseJson(data, []))
        return
      }
      if (eventName === 'status' && handlers.onStatus) {
        handlers.onStatus(parseJson(data, { code: 'working', message: data }))
        return
      }
      if (eventName === 'error' && handlers.onError) {
        terminalEventReceived = true
        handlers.onError(parseJson(data, { code: 'STREAM_ERROR', message: data }))
      }
    }

    const extractNextBlock = (text) => {
      // 同时兼容 LF 和 CRLF；一个 SSE 事件以空行结束，data 允许出现多行。
      const match = text.match(/\r?\n\r?\n/)
      if (!match || match.index == null) {
        return null
      }
      const end = match.index
      const sepLen = match[0].length
      return {
        block: text.slice(0, end),
        rest: text.slice(end + sepLen)
      }
    }

    const handleBlock = (rawBlock) => {
      const block = rawBlock.trim()
      if (!block) {
        return false
      }

      const lines = block.split(/\r?\n/)
      let eventName = 'message'
      const dataLines = []

      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trimStart())
        }
        // v4 不发送无重连语义的 id；冒号开头的 heartbeat 注释不会派发为业务事件。
      }

      const data = dataLines.join('\n')
      if (eventName === 'done') {
        terminalEventReceived = true
        if (handlers.onDone) {
          handlers.onDone(parseJson(data, {}))
        }
        return true
      }

      dispatchEvent(eventName, data)
      return false
    }

    while (true) {
      const { value, done } = await reader.read()
      if (done) {
        break
      }

      buffer += decoder.decode(value, { stream: true })

      let extracted = extractNextBlock(buffer)
      while (extracted) {
        buffer = extracted.rest
        if (handleBlock(extracted.block)) {
          return
        }
        extracted = extractNextBlock(buffer)
      }
    }

    const remaining = buffer.trim()
    if (remaining) {
      handleBlock(remaining)
    }

    // 正常协议必须由 done 收口；若代理在事件前截断连接，交给调用方按异常处理，
    // 避免把不完整答案误标为成功。error 也是终态，因此不会再次抛出截断异常。
    if (!terminalEventReceived) {
      throw new Error('流式连接提前结束，请重试')
    }
  },

  async messages(conversationId) {
    const response = await fetch(
      `${apiConfig.baseURL}/ai/travel/conversations/${encodeURIComponent(conversationId)}/messages`,
      { headers: { Accept: 'application/json', ...authHeaders() } }
    )
    if (!response.ok) {
      throw new Error(`恢复会话失败(${response.status})`)
    }
    const body = await response.json()
    return body?.data || { conversation_id: conversationId, messages: [] }
  },

  async clear(conversationId) {
    const response = await fetch(
      `${apiConfig.baseURL}/ai/travel/conversations/${encodeURIComponent(conversationId)}`,
      { method: 'DELETE', headers: { Accept: 'application/json', ...authHeaders() } }
    )
    if (!response.ok) {
      throw new Error(`清空会话失败(${response.status})`)
    }
  }
}

export default travelAiApi
