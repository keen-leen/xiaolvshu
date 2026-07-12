import apiConfig from '@/config/api'

export const travelAiApi = {
  async chat(payload, handlers = {}) {
    const token = localStorage.getItem('token')
    const adminToken = localStorage.getItem('admin_token')

    const response = await fetch(`${apiConfig.baseURL}/ai/travel/chat`, {
      method: 'POST',
      headers: {
        'Accept': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(!token && adminToken ? { Authorization: `Bearer ${adminToken}` } : {})
      },
      body: JSON.stringify(payload)
    })

    if (!response.ok || !response.body) {
      throw new Error(`流式请求失败(${response.status})`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    const dispatchEvent = (eventName, data) => {
      if (eventName === 'chunk' && handlers.onChunk) {
        handlers.onChunk(data)
        return
      }
      if (eventName === 'refs' && handlers.onRefs) {
        try {
          handlers.onRefs(JSON.parse(data || '[]'))
        } catch (e) {
          handlers.onRefs([])
        }
        return
      }
      if (eventName === 'step' && handlers.onStep) {
        try {
          handlers.onStep(JSON.parse(data || '{}'))
        } catch (e) {
          handlers.onStep({ raw: data })
        }
        return
      }
      if (eventName === 'tool' && handlers.onTool) {
        try {
          handlers.onTool(JSON.parse(data || '{}'))
        } catch (e) {
          handlers.onTool({ raw: data })
        }
        return
      }
      if (eventName === 'error' && handlers.onError) {
        handlers.onError(data)
      }
    }

    const extractNextBlock = (text) => {
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
      }

      const data = dataLines.join('\n')
      if (eventName === 'done') {
        if (handlers.onDone) {
          handlers.onDone()
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

    if (handlers.onDone) {
      handlers.onDone()
    }
  }
}

export default travelAiApi
