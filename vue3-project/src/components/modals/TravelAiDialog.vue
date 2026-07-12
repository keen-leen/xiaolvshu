<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import travelAiApi from '@/api/ai'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import SvgIcon from '@/components/SvgIcon.vue'
import { useTravelAiStore } from '@/stores/travelAi'

const route = useRoute()
const router = useRouter()
const travelAiStore = useTravelAiStore()

const loading = ref(false)
const userInput = ref('')
const messages = ref([
  {
    role: 'assistant',
    content: '你好，我是小旅书旅行助手。告诉我你的目的地、天数和预算，我会结合社区笔记为你做攻略。',
    references: [],
    agentSteps: [],
    toolResults: []
  }
])

const messageListRef = ref(null)

const md = new MarkdownIt({
  breaks: true,
  linkify: true,
  html: false
})

const defaultLinkOpen = md.renderer.rules.link_open || ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options))
md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  tokens[idx].attrSet('target', '_blank')
  tokens[idx].attrSet('rel', 'noopener noreferrer nofollow')
  return defaultLinkOpen(tokens, idx, options, env, self)
}

const normalizeMarkdownText = (raw) => {
  if (!raw) {
    return ''
  }

  let text = String(raw)
    .replace(/\\r\\n/g, '\n')
    .replace(/\\n/g, '\n')
    .replace(/\u00a0/g, ' ')

  // 兼容全角井号标题（＃＃＃）
  text = text.replace(/(^|\n)[ \t]{0,3}＃{1,6}/g, (m) => m.replace(/＃/g, '#'))

  // 兼容“###标题”这种缺少空格的写法
  text = text.replace(/(^|\n)([ \t]{0,3}#{1,6})([^\s#])/g, '$1$2 $3')

  return text
}

const extractChunkText = (chunk) => {
  if (typeof chunk !== 'string') {
    return String(chunk || '')
  }

  const trimmed = chunk.trim()
  if (!trimmed) {
    return ''
  }

  if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
    try {
      const parsed = JSON.parse(trimmed)
      if (typeof parsed === 'string') {
        return parsed
      }

      if (parsed && typeof parsed === 'object') {
        const candidateKeys = ['content', 'chunk', 'text', 'delta', 'answer', 'message']
        for (const key of candidateKeys) {
          if (typeof parsed[key] === 'string') {
            return parsed[key]
          }
        }
      }
    } catch (_) {
      // ignore JSON parse failures and fallback to original chunk
    }
  }

  return chunk
}

const renderAssistantMarkdown = (content) => {
  if (!content) {
    return ''
  }

  const rendered = md.render(normalizeMarkdownText(content))
  return DOMPurify.sanitize(rendered, {
    USE_PROFILES: { html: true },
    ALLOWED_ATTR: ['href', 'target', 'rel', 'title']
  })
}

const hiddenOnRoute = computed(() => route.path.startsWith('/admin') || route.path.startsWith('/travel-ai'))
const open = computed(() => travelAiStore.visible)

const canSend = computed(() => !loading.value && userInput.value.trim().length > 0)

const quickPrompts = [
  '帮我做一份3天成都美食+景点路线',
  '想去北京亲子游4天，预算3000以内',
  '西安周末2天怎么玩最省时间'
]

const openDialog = () => {
  travelAiStore.openAssistant()
  nextTick(scrollToBottom)
}

const closeDialog = () => {
  travelAiStore.closeAssistant()
}

const openFullPage = () => {
  travelAiStore.closeAssistant()
  router.push('/travel-ai')
}

const scrollToBottom = () => {
  if (!messageListRef.value) {
    return
  }
  messageListRef.value.scrollTop = messageListRef.value.scrollHeight
}

const applyPrompt = (prompt) => {
  userInput.value = prompt
}

watch(
  () => travelAiStore.initialPrompt,
  (prompt) => {
    if (!prompt) {
      return
    }
    userInput.value = prompt
    travelAiStore.consumeInitialPrompt()
    nextTick(scrollToBottom)
  }
)

const buildHistory = () => {
  return messages.value
    .filter((item) => item.role === 'user' || item.role === 'assistant')
    .slice(-8)
    .map((item) => ({
      role: item.role,
      content: item.content
    }))
}

const sendMessage = async () => {
  if (!canSend.value) {
    return
  }

  const content = userInput.value.trim()
  userInput.value = ''

  messages.value.push({
    role: 'user',
    content,
    references: []
  })

  const assistantMessage = {
    role: 'assistant',
    content: '',
    references: [],
    agentSteps: [],
    toolResults: []
  }
  messages.value.push(assistantMessage)
  const assistantIndex = messages.value.length - 1

  loading.value = true
  nextTick(scrollToBottom)

  try {
    await travelAiApi.chat(
      {
        message: content,
        topK: 5,
        history: buildHistory()
      },
      {
        onChunk: (chunk) => {
          messages.value[assistantIndex].content += extractChunkText(chunk)
          nextTick(scrollToBottom)
        },
        onRefs: (refs) => {
          messages.value[assistantIndex].references = refs || []
          nextTick(scrollToBottom)
        },
        onStep: (step) => {
          messages.value[assistantIndex].agentSteps.push(step)
          nextTick(scrollToBottom)
        },
        onTool: (tool) => {
          messages.value[assistantIndex].toolResults.push(tool)
          nextTick(scrollToBottom)
        },
        onError: (errorText) => {
          messages.value[assistantIndex].content = errorText || '流式响应失败，请稍后重试'
        }
      }
    )

    if (!messages.value[assistantIndex].content) {
      messages.value[assistantIndex].content = '已完成攻略生成。'
    }
  } catch (e) {
    messages.value[assistantIndex].content = `流式请求失败：${e?.message || '请稍后重试'}`
    messages.value[assistantIndex].references = []
    messages.value[assistantIndex].agentSteps = []
    messages.value[assistantIndex].toolResults = []
  }

  loading.value = false
  nextTick(scrollToBottom)
}
</script>

<template>
  <div v-if="!hiddenOnRoute" class="travel-ai-widget">
    <button v-if="!open" class="floating-trigger" @click="openDialog">
      <SvgIcon name="magic" width="18" height="18" color="white" />
      <span>旅行助手</span>
    </button>

    <div v-if="open" class="dialog-mask" @click="closeDialog">
      <div class="dialog-card" @click.stop>
        <header class="dialog-header">
          <div>
            <div class="header-title">旅行助手</div>
            <div class="header-subtitle">结合社区笔记生成路线</div>
          </div>
          <div class="header-actions">
            <button class="page-btn" @click="openFullPage">完整页面</button>
            <button class="close-btn" @click="closeDialog">×</button>
          </div>
        </header>

        <div class="quick-prompts">
          <button v-for="prompt in quickPrompts" :key="prompt" class="prompt-chip" @click="applyPrompt(prompt)">
            {{ prompt }}
          </button>
        </div>

        <div ref="messageListRef" class="message-list">
          <div v-for="(item, idx) in messages" :key="idx" :class="['message-item', item.role]">
            <div v-if="item.role === 'assistant'" class="message-bubble markdown-body" v-html="renderAssistantMarkdown(item.content)"></div>
            <div v-else class="message-bubble">{{ item.content }}</div>
            <div v-if="item.role === 'assistant' && item.agentSteps && item.agentSteps.length > 0" class="agent-step-list">
              <span v-for="step in item.agentSteps" :key="step.step" class="agent-step-item">
                {{ step.action === 'tool' ? step.tool_call?.tool_name : '生成答案' }}
              </span>
            </div>
            <div v-if="item.role === 'assistant' && item.references && item.references.length > 0" class="source-list">
              <a
                v-for="ref in item.references"
                :key="ref.post_id"
                class="source-item"
                :href="ref.link"
                target="_blank"
                rel="noopener noreferrer"
              >
                <span class="source-title">{{ ref.title }}</span>
                <span class="source-meta">作者: {{ ref.author }}</span>
              </a>
            </div>
          </div>

          <div v-if="loading" class="message-item assistant">
            <div class="message-bubble loading-bubble">Agent 正在判断工具并生成攻略...</div>
          </div>
        </div>

        <footer class="input-area">
          <textarea
            v-model="userInput"
            class="chat-input"
            placeholder="输入你的旅行需求，例如：杭州2天亲子游，预算2000"
            rows="3"
            @keydown.enter.exact.prevent="sendMessage"
          />
          <button class="send-btn" :disabled="!canSend" @click="sendMessage">发送</button>
        </footer>
      </div>
    </div>
  </div>
</template>

<style scoped>
.travel-ai-widget {
  position: fixed;
  right: 22px;
  bottom: 22px;
  z-index: 2100;
}

.floating-trigger {
  border: none;
  border-radius: 999px;
  background: var(--primary-color);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  padding: 12px 16px;
  box-shadow: 0 8px 20px var(--primary-color-shadow);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.dialog-mask {
  position: fixed;
  inset: 0;
  background: var(--overlay-bg);
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  padding: 20px;
}

.dialog-card {
  width: min(420px, calc(100vw - 20px));
  height: min(680px, calc(100vh - 40px));
  background: var(--bg-color-primary);
  border: 1px solid var(--border-color-primary);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 24px 48px rgba(0, 0, 0, 0.18);
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-color-primary);
}

.header-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-color-primary);
}

.header-subtitle {
  color: var(--text-color-tertiary);
  font-size: 12px;
  margin-top: 2px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-btn {
  height: 28px;
  border: 1px solid var(--border-color-primary);
  border-radius: 999px;
  background: var(--bg-color-secondary);
  color: var(--text-color-primary);
  font-size: 12px;
  cursor: pointer;
}

.close-btn {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: var(--bg-color-secondary);
  color: var(--text-color-primary);
  cursor: pointer;
}

.quick-prompts {
  padding: 10px 12px;
  display: flex;
  gap: 8px;
  overflow-x: auto;
  border-bottom: 1px solid var(--border-color-primary);
}

.prompt-chip {
  border: 1px solid var(--border-color-primary);
  color: var(--text-color-primary);
  background: var(--bg-color-secondary);
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}

.message-list {
  flex: 1;
  padding: 14px;
  overflow-y: auto;
  background: var(--bg-color-secondary);
}

.message-item {
  margin-bottom: 12px;
}

.message-item.user {
  display: flex;
  justify-content: flex-end;
}

.message-bubble {
  max-width: 88%;
  border-radius: 12px;
  padding: 10px 12px;
  white-space: pre-wrap;
  line-height: 1.45;
  font-size: 13px;
}

.markdown-body {
  white-space: normal;
}

.markdown-body :deep(p) {
  margin: 0 0 8px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 8px 0;
  line-height: 1.35;
  color: var(--text-color-primary);
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 6px 0 8px;
  padding-left: 20px;
}

.markdown-body :deep(li) {
  margin: 3px 0;
}

.markdown-body :deep(code) {
  padding: 1px 4px;
  border-radius: 4px;
  background: var(--bg-color-secondary);
  color: var(--text-color-primary);
  font-size: 12px;
}

.markdown-body :deep(pre) {
  margin: 8px 0;
  padding: 10px;
  border-radius: 8px;
  background: var(--bg-color-secondary);
  overflow-x: auto;
}

.markdown-body :deep(pre code) {
  padding: 0;
  background: transparent;
}

.markdown-body :deep(a) {
  color: var(--primary-color);
  text-decoration: underline;
}

.markdown-body :deep(blockquote) {
  margin: 8px 0;
  padding: 6px 10px;
  border-left: 3px solid var(--primary-color);
  color: var(--text-color-secondary);
  background: var(--bg-color-secondary);
}

.markdown-body :deep(img) {
  display: block;
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  margin: 8px 0;
}

.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 12px;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--border-color-primary);
  padding: 6px;
  text-align: left;
}

.message-item.user .message-bubble {
  color: #fff;
  background: var(--primary-color);
}

.message-item.assistant .message-bubble {
  color: var(--text-color-primary);
  background: var(--bg-color-primary);
  border: 1px solid var(--border-color-primary);
}

.loading-bubble {
  color: var(--text-color-secondary);
}

.source-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.agent-step-list {
  max-width: 88%;
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.agent-step-item {
  border: 1px solid var(--border-color-primary);
  border-radius: 999px;
  background: var(--bg-color-primary);
  color: var(--text-color-secondary);
  padding: 3px 8px;
  font-size: 11px;
}

.source-item {
  display: flex;
  flex-direction: column;
  text-decoration: none;
  border: 1px dashed var(--border-color-primary);
  border-radius: 8px;
  padding: 8px;
  background: var(--bg-color-secondary);
}

.source-title {
  color: var(--text-color-primary);
  font-size: 12px;
  font-weight: 600;
}

.source-meta {
  color: var(--text-color-tertiary);
  font-size: 11px;
  margin-top: 2px;
}

.input-area {
  border-top: 1px solid var(--border-color-primary);
  padding: 10px;
  background: var(--bg-color-primary);
}

.chat-input {
  width: 100%;
  border: 1px solid var(--border-color-primary);
  border-radius: 10px;
  resize: none;
  padding: 8px;
  box-sizing: border-box;
  background: var(--bg-color-primary);
  color: var(--text-color-primary);
  font-size: 13px;
  outline: none;
}

.chat-input:focus {
  border-color: var(--primary-color);
}

.send-btn {
  margin-top: 8px;
  width: 100%;
  border: none;
  border-radius: 10px;
  padding: 9px;
  color: #fff;
  background: var(--primary-color);
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
}

.send-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

@media (max-width: 768px) {
  .travel-ai-widget {
    right: 14px;
    bottom: 62px;
  }

  .dialog-mask {
    padding: 8px;
  }

  .dialog-card {
    width: calc(100vw - 16px);
    height: min(700px, calc(100vh - 16px));
  }
}
</style>
