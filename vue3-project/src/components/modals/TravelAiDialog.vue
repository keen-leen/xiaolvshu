<script setup>
import { computed, nextTick, ref } from 'vue'
import { useRoute } from 'vue-router'
import travelAiApi from '@/api/ai'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

const route = useRoute()

const open = ref(false)
const loading = ref(false)
const userInput = ref('')
const messages = ref([
  {
    role: 'assistant',
    content: '你好，我是小旅书旅行攻略助手。告诉我你的目的地、天数和预算，我会结合社区笔记为你做攻略。',
    references: []
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

const hiddenOnRoute = computed(() => route.path.startsWith('/admin'))

const canSend = computed(() => !loading.value && userInput.value.trim().length > 0)

const quickPrompts = [
  '帮我做一份3天成都美食+景点路线',
  '想去北京亲子游4天，预算3000以内',
  '西安周末2天怎么玩最省时间'
]

const openDialog = () => {
  open.value = true
  nextTick(scrollToBottom)
}

const closeDialog = () => {
  open.value = false
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
    references: []
  }
  messages.value.push(assistantMessage)
  const assistantIndex = messages.value.length - 1

  loading.value = true
  nextTick(scrollToBottom)

  try {
    await travelAiApi.chatStream(
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
        onError: (errorText) => {
          messages.value[assistantIndex].content = errorText || '流式响应失败，请稍后重试'
        }
      }
    )

    if (!messages.value[assistantIndex].content) {
      messages.value[assistantIndex].content = '已完成攻略生成。'
    }
  } catch (e) {
    const response = await travelAiApi.chat({
      message: content,
      topK: 5,
      history: buildHistory()
    })

    if (response?.success && response?.data) {
      messages.value[assistantIndex].content = response.data.answer || '已完成攻略生成。'
      messages.value[assistantIndex].references = response.data.references || []
    } else {
      messages.value[assistantIndex].content = `请求失败：${response?.message || '请稍后重试'}`
      messages.value[assistantIndex].references = []
    }
  }

  loading.value = false
  nextTick(scrollToBottom)
}
</script>

<template>
  <div v-if="!hiddenOnRoute" class="travel-ai-widget">
    <button v-if="!open" class="floating-trigger" @click="openDialog">
      旅行AI
    </button>

    <div v-if="open" class="dialog-mask" @click="closeDialog">
      <div class="dialog-card" @click.stop>
        <header class="dialog-header">
          <div class="header-title">旅行攻略助手</div>
          <button class="close-btn" @click="closeDialog">×</button>
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
            <div class="message-bubble loading-bubble">正在检索笔记并生成攻略...</div>
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
  background: linear-gradient(135deg, #0b8f71, #14a37f);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  padding: 12px 18px;
  box-shadow: 0 8px 20px rgba(8, 113, 90, 0.35);
  cursor: pointer;
}

.dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(9, 18, 16, 0.42);
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  padding: 20px;
}

.dialog-card {
  width: min(420px, calc(100vw - 20px));
  height: min(680px, calc(100vh - 40px));
  background: #fff;
  border-radius: 16px;
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
  border-bottom: 1px solid #edf1ef;
}

.header-title {
  font-size: 16px;
  font-weight: 700;
  color: #18342e;
}

.close-btn {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: #eef5f2;
  cursor: pointer;
}

.quick-prompts {
  padding: 10px 12px;
  display: flex;
  gap: 8px;
  overflow-x: auto;
  border-bottom: 1px solid #edf1ef;
}

.prompt-chip {
  border: 1px solid #c8dfd7;
  color: #156f5b;
  background: #f4fbf8;
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
  background: #f8faf9;
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
  color: #18342e;
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
  background: #eff4f2;
  color: #1d3e36;
  font-size: 12px;
}

.markdown-body :deep(pre) {
  margin: 8px 0;
  padding: 10px;
  border-radius: 8px;
  background: #f1f5f4;
  overflow-x: auto;
}

.markdown-body :deep(pre code) {
  padding: 0;
  background: transparent;
}

.markdown-body :deep(a) {
  color: #0d7b62;
  text-decoration: underline;
}

.markdown-body :deep(blockquote) {
  margin: 8px 0;
  padding: 6px 10px;
  border-left: 3px solid #b8d8cf;
  color: #335a52;
  background: #f4faf8;
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
  border: 1px solid #dce9e4;
  padding: 6px;
  text-align: left;
}

.message-item.user .message-bubble {
  color: #fff;
  background: #108e71;
}

.message-item.assistant .message-bubble {
  color: #1a302b;
  background: #fff;
  border: 1px solid #e6efec;
}

.loading-bubble {
  color: #3b6158;
}

.source-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.source-item {
  display: flex;
  flex-direction: column;
  text-decoration: none;
  border: 1px dashed #bfd6cf;
  border-radius: 10px;
  padding: 8px;
  background: #f6fcf9;
}

.source-title {
  color: #144d3f;
  font-size: 12px;
  font-weight: 600;
}

.source-meta {
  color: #597a72;
  font-size: 11px;
  margin-top: 2px;
}

.input-area {
  border-top: 1px solid #edf1ef;
  padding: 10px;
  background: #fff;
}

.chat-input {
  width: 100%;
  border: 1px solid #d8e7e2;
  border-radius: 10px;
  resize: none;
  padding: 8px;
  box-sizing: border-box;
  font-size: 13px;
  outline: none;
}

.chat-input:focus {
  border-color: #0f8e70;
}

.send-btn {
  margin-top: 8px;
  width: 100%;
  border: none;
  border-radius: 10px;
  padding: 9px;
  color: #fff;
  background: #0f8e70;
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
    bottom: 14px;
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
