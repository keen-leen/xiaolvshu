import { defineStore } from 'pinia'
import travelAiApi from '@/api/ai'

const CONVERSATION_STORAGE_KEY = 'travel_ai_conversation_id'
const WELCOME_MESSAGE = {
  role: 'assistant',
  content: '你好，我是小旅书旅行助手。告诉我你的目的地、天数和预算，我会结合社区笔记为你做攻略。',
  references: []
}

let controller = null
let pendingText = ''
let flushTimer = null
let activeAssistantIndex = -1

const initialMessages = () => [{ ...WELCOME_MESSAGE }]

/**
 * 完整页与浮窗共享的旅行 Agent 会话状态。
 *
 * 后端已经使用 Spring AI ChatMemory 保存模型上下文，前端只负责展示最近窗口和持有后端签发的
 * conversationId。网络流也集中在这里，避免两个入口分别维护一套 history、AbortController 和
 * SSE 回调，造成协议升级时只修到其中一处。
 */
export const useTravelAiStore = defineStore('travelAi', {
  state: () => ({
    visible: false,
    initialPrompt: '',
    conversationId: localStorage.getItem(CONVERSATION_STORAGE_KEY) || '',
    messages: initialMessages(),
    loading: false,
    statusText: '',
    hydrated: false
  }),

  actions: {
    openAssistant(prompt = '') {
      this.initialPrompt = prompt || ''
      this.visible = true
      this.hydrate()
    },

    closeAssistant() {
      if (this.loading) {
        this.stop()
      }
      this.hideAssistant()
    },

    /**
     * 仅隐藏浮窗，不终止共享请求。用于跳转完整页面时让同一条 SSE 流继续输出；
     * 普通关闭仍调用 closeAssistant，并明确停止用户已经离开的生成任务。
     */
    hideAssistant() {
      this.visible = false
    },

    consumeInitialPrompt() {
      const prompt = this.initialPrompt
      this.initialPrompt = ''
      return prompt
    },

    async hydrate() {
      if (this.hydrated || !this.conversationId) {
        this.hydrated = true
        return
      }
      try {
        const data = await travelAiApi.messages(this.conversationId)
        const restored = (data?.messages || [])
          .filter(item => item?.role && item?.content)
          .map(item => ({ role: item.role, content: item.content, references: [] }))
        this.messages = restored.length ? restored : initialMessages()
      } catch (_) {
        // 会话可能已过期或属于另一个登录身份。保留本地新会话，不阻塞用户继续提问。
        this.conversationId = ''
        localStorage.removeItem(CONVERSATION_STORAGE_KEY)
        this.messages = initialMessages()
      } finally {
        this.hydrated = true
      }
    },

    /**
     * 发送一轮对话。请求不再上传 history，Spring AI MessageChatMemoryAdvisor 会按
     * conversationId 自动加载最近消息，并在完整流成功后保存新的 user/assistant 轮次。
     */
    async send(content) {
      const text = String(content || '').trim()
      if (this.loading || !text || text.length > 2000) {
        return
      }

      this.messages.push({ role: 'user', content: text, references: [] })
      this.messages.push({ role: 'assistant', content: '', references: [] })
      activeAssistantIndex = this.messages.length - 1
      this.loading = true
      this.statusText = ''
      controller = new AbortController()

      try {
        await travelAiApi.chat(
          {
            message: text,
            topK: 5,
            conversationId: this.conversationId || null
          },
          {
            onMeta: meta => {
              if (meta?.conversation_id) {
                this.conversationId = meta.conversation_id
                localStorage.setItem(CONVERSATION_STORAGE_KEY, meta.conversation_id)
              }
            },
            onStatus: status => {
              this.statusText = typeof status?.message === 'string' ? status.message.trim() : ''
            },
            onChunk: chunk => this.appendChunk(chunk),
            onRefs: refs => {
              this.flushChunks()
              if (this.messages[activeAssistantIndex]) {
                this.messages[activeAssistantIndex].references = refs || []
              }
            },
            onError: error => {
              this.flushChunks()
              if (this.messages[activeAssistantIndex]) {
                this.messages[activeAssistantIndex].content =
                  error?.message || '这次没接上，稍后再试试吧。'
              }
            }
          },
          { signal: controller.signal }
        )
        this.flushChunks()
        if (this.messages[activeAssistantIndex] && !this.messages[activeAssistantIndex].content) {
          this.messages[activeAssistantIndex].content = '我整理好了，你还想改改哪里？'
        }
      } catch (error) {
        this.flushChunks()
        const current = this.messages[activeAssistantIndex]
        if (current) {
          if (error?.name === 'AbortError') {
            current.content ||= '已停止生成。'
          } else {
            current.content = `这次没接上：${error?.message || '稍后再试试吧'}`
            current.references = []
          }
        }
      } finally {
        this.finishStream()
      }
    },

    appendChunk(chunk) {
      pendingText += typeof chunk === 'string' ? chunk : String(chunk || '')
      if (!flushTimer) {
        // 40ms 合并窗口避免每个 token 都触发 Markdown 全量渲染。
        flushTimer = setTimeout(() => this.flushChunks(), 40)
      }
    },

    flushChunks() {
      if (flushTimer) {
        clearTimeout(flushTimer)
        flushTimer = null
      }
      if (!pendingText || !this.messages[activeAssistantIndex]) {
        pendingText = ''
        return
      }
      this.messages[activeAssistantIndex].content += pendingText
      pendingText = ''
    },

    stop() {
      controller?.abort()
      controller = null
      this.flushChunks()
      this.loading = false
      this.statusText = ''
    },

    finishStream() {
      this.flushChunks()
      controller = null
      activeAssistantIndex = -1
      this.loading = false
      this.statusText = ''
    },

    async clearConversation() {
      if (this.loading) {
        return
      }
      if (this.conversationId) {
        await travelAiApi.clear(this.conversationId)
      }
      this.conversationId = ''
      localStorage.removeItem(CONVERSATION_STORAGE_KEY)
      this.messages = initialMessages()
      this.statusText = ''
      this.hydrated = true
    }
  }
})
