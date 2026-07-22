import { onUnmounted, ref } from 'vue'

/**
 * 管理旅行 Agent 的浏览器端流生命周期。
 *
 * SSE token 到达频率通常高于 Vue/Markdown 的合理刷新频率。这里用 40ms 小窗口合并片段，
 * 既保留肉眼可见的流式反馈，又避免每个 token 都触发 Markdown 全量渲染和 DOM 更新。
 * AbortController 同时作为页面离开、弹窗关闭和用户主动停止时的唯一取消入口。
 */
export const useTravelAiStream = () => {
  const controller = ref(null)
  let pendingText = ''
  let flushTimer = null
  let flushHandler = null

  // flush 既由 40ms 定时器触发，也会在 refs/error/finally 前同步触发，保证事件顺序不会被缓冲打乱。
  const flush = () => {
    if (flushTimer) {
      clearTimeout(flushTimer)
      flushTimer = null
    }
    if (!pendingText || !flushHandler) {
      return
    }
    const text = pendingText
    pendingText = ''
    flushHandler(text)
  }

  const begin = (handler) => {
    // 理论上 UI 会阻止并发发送；这里仍先取消旧请求，避免未来入口复用时产生双流写入。
    stop()
    flushHandler = handler
    controller.value = new AbortController()
    return controller.value.signal
  }

  const append = (text) => {
    pendingText += text || ''
    if (!flushTimer) {
      flushTimer = setTimeout(flush, 40)
    }
  }

  const stop = () => {
    // 先 abort 再 flush：立即阻止新网络片段进入，同时保留用户已经收到但尚未渲染的尾部文本。
    controller.value?.abort()
    controller.value = null
    flush()
  }

  const finish = () => {
    // finish 只清理本轮本地状态，不再次 abort 已正常结束的响应，避免制造无意义的 AbortError。
    flush()
    controller.value = null
    flushHandler = null
  }

  const isAbortError = (error) => error?.name === 'AbortError'

  onUnmounted(stop)

  return { append, begin, finish, flush, isAbortError, stop }
}
