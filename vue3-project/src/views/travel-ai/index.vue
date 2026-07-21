<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import SvgIcon from '@/components/SvgIcon.vue'
import travelAiApi from '@/api/ai'
import { useUserStore } from '@/stores/user'
import defaultAvatar from '@/assets/imgs/avatar.png'

const userStore = useUserStore()

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

const messages = ref([])
const chatInput = ref('')
const loading = ref(false)
const messageListRef = ref(null)
const currentStatusText = ref('')

const quickPrompts = [
  '周末想去个离我不太远的地方',
  '帮我安排三天成都美食之旅',
  '带孩子去杭州，怎么玩不累',
  '想找一条小众又适合拍照的路线'
]

const hasConversation = computed(() => messages.value.some(item => item.role === 'user'))
const inputLength = computed(() => chatInput.value.length)
const canSend = computed(() => !loading.value && chatInput.value.trim().length > 0 && inputLength.value <= 2000)
const userAvatar = computed(() => userStore.userInfo?.avatar || defaultAvatar)
const userName = computed(() => userStore.userInfo?.nickname || '你')
const userQuestionCount = computed(() => messages.value.filter(item => item.role === 'user').length)

onMounted(() => {
  // 侧边栏在桌面端通常已经恢复过用户信息；移动端或直达本页时可能尚未恢复。
  // 这里只读取 localStorage 中的已有会话，不为了显示头像额外发起网络请求。
  if (!userStore.userInfo) {
    userStore.initUserInfo()
  }
})

const clearCurrentStage = () => {
  currentStatusText.value = ''
}

const normalizeMarkdownText = (raw) => {
  if (!raw) {
    return ''
  }

  let text = String(raw)
    .replace(/\r\n/g, '\n')
    .replace(/\\n/g, '\n')
    .replace(/\u00a0/g, ' ')

  text = text.replace(/(^|\n)[ \t]{0,3}＃{1,6}/g, match => match.replace(/＃/g, '#'))
  text = text.replace(/(^|\n)([ \t]{0,3}#{1,6})([^\s#])/g, '$1$2 $3')
  return text
}

const renderMarkdown = (content) => {
  if (!content) {
    return ''
  }

  return DOMPurify.sanitize(md.render(normalizeMarkdownText(content)), {
    USE_PROFILES: { html: true },
    ALLOWED_ATTR: ['href', 'target', 'rel', 'title']
  })
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
        for (const key of ['content', 'chunk', 'text', 'delta', 'answer', 'message']) {
          if (typeof parsed[key] === 'string') {
            return parsed[key]
          }
        }
      }
    } catch (_) {
      // 普通文本片段可能恰好以大括号开头，解析失败时应保留原文。
    }
  }

  return chunk
}

const scrollToBottom = () => {
  if (!messageListRef.value) {
    return
  }
  messageListRef.value.scrollTop = messageListRef.value.scrollHeight
}

const buildHistory = () => {
  // history 只包含已完成的旧消息。必须在追加本次用户问题和空助手占位之前调用，
  // 否则当前问题会同时出现在 message 与 history 中，并增加无意义的模型上下文。
  return messages.value
    .filter(item => (item.role === 'user' || item.role === 'assistant') && item.content?.trim())
    .slice(-8)
    .map(item => ({ role: item.role, content: item.content }))
}

const sendMessage = async (preset = '') => {
  const content = String(preset || chatInput.value).trim()
  if (loading.value || !content || content.length > 2000) {
    return
  }

  const history = buildHistory()
  chatInput.value = ''
  messages.value.push({ role: 'user', content, references: [] })

  const assistantMessage = {
    role: 'assistant',
    content: '',
    references: []
  }
  messages.value.push(assistantMessage)
  const assistantIndex = messages.value.length - 1

  loading.value = true
  // 首个 step 返回前只展示无文字等待动效，不在前端推测 Agent 尚未执行的步骤。
  clearCurrentStage()
  await nextTick()
  scrollToBottom()

  try {
    await travelAiApi.chat(
      {
        message: content,
        topK: 5,
        history
      },
      {
        onChunk: (chunk) => {
          messages.value[assistantIndex].content += extractChunkText(chunk)
          nextTick(scrollToBottom)
        },
        onStep: (step) => {
          // thought 是后端专门生成的安全状态文案，不是模型原始推理。
          // 页面原样展示该字段，缺失时继续保留无文字等待态，不用 action 拼装替代文案。
          currentStatusText.value = typeof step?.thought === 'string' ? step.thought.trim() : ''
        },
        onRefs: (refs) => {
          messages.value[assistantIndex].references = refs || []
          nextTick(scrollToBottom)
        },
        onError: (errorText) => {
          messages.value[assistantIndex].content = errorText || '这次没接上，稍后再试试吧。'
        }
      }
    )

    if (!messages.value[assistantIndex].content) {
      messages.value[assistantIndex].content = '我整理好了，你还想改改哪里？'
    }
  } catch (error) {
    messages.value[assistantIndex].content = `这次没接上：${error?.message || '稍后再试试吧'}`
    messages.value[assistantIndex].references = []
  } finally {
    loading.value = false
    clearCurrentStage()
    nextTick(scrollToBottom)
  }
}

const clearConversation = () => {
  if (loading.value) {
    return
  }
  messages.value = []
  chatInput.value = ''
  clearCurrentStage()
}

const handleAvatarError = (event) => {
  // 不论远程头像失效还是用户信息不完整，聊天布局都应保持稳定。
  // 先移除 error 回调再替换默认图，避免默认资源自身异常时循环触发。
  event.target.onerror = null
  event.target.src = defaultAvatar
}
</script>

<template>
  <main class="travel-chat-page">
    <section class="chat-room" aria-label="小旅书旅行对话">
      <header class="room-toolbar">
        <div class="journey-label">
          <span class="route-mark" aria-hidden="true"><i></i><i></i><i></i></span>
          <span>
            <strong>{{ hasConversation ? '这趟旅程正在慢慢清晰' : '从一个想法开始' }}</strong>
            <small>{{ hasConversation ? `已经聊了 ${userQuestionCount} 个问题` : '不用想好再开口' }}</small>
          </span>
        </div>

        <button
          v-if="hasConversation"
          type="button"
          class="clear-button"
          :disabled="loading"
          aria-label="清空当前对话"
          @click="clearConversation"
        >
          <SvgIcon name="clear" width="15" height="15" color="currentColor" />
          <span>重新聊聊</span>
        </button>
      </header>

      <div ref="messageListRef" class="conversation" aria-live="polite">
        <div v-if="!hasConversation" class="welcome-panel">
          <div class="welcome-avatar">
            <img :src="defaultAvatar" alt="小旅书头像" @error="handleAvatarError" />
          </div>
          <h1>诗和远方，我都要</h1>
          <p class="welcome-note">目的地、天数、同行人或预算，想到什么就说什么。</p>

          <div class="prompt-list" aria-label="快捷问题">
            <button
              v-for="prompt in quickPrompts"
              :key="prompt"
              type="button"
              :disabled="loading"
              @click="sendMessage(prompt)"
            >
              <span>{{ prompt }}</span>
              <SvgIcon name="right" width="14" height="14" color="currentColor" />
            </button>
          </div>
        </div>

        <template v-else>
          <article
            v-for="(message, index) in messages"
            :key="index"
            :class="['message-row', message.role]"
          >
            <img
              class="message-avatar"
              :src="message.role === 'assistant' ? defaultAvatar : userAvatar"
              :alt="message.role === 'assistant' ? '小旅书头像' : `${userName}的头像`"
              @error="handleAvatarError"
            />

            <div class="message-column">
              <span class="message-author">{{ message.role === 'assistant' ? '小旅书' : userName }}</span>

              <div
                v-if="message.role === 'assistant' && index === messages.length - 1 && loading"
                :class="['stage-status', { waiting: !currentStatusText }]"
                :aria-label="currentStatusText || '等待后端返回当前步骤'"
                aria-live="polite"
                role="status"
              >
                <span class="stage-compass" aria-hidden="true">
                  <SvgIcon name="magic" width="14" height="14" color="currentColor" />
                  <i></i>
                </span>
                <span v-if="currentStatusText" class="stage-copy">{{ currentStatusText }}</span>
                <span v-else class="stage-placeholder" aria-hidden="true"><i></i></span>
                <span class="stage-route" aria-hidden="true"><i></i><i></i><i></i></span>
              </div>
              <div
                v-if="message.role === 'assistant' && message.content"
                class="message-bubble markdown-body"
                v-html="renderMarkdown(message.content)"
              ></div>
              <div v-else-if="message.role === 'user'" class="message-bubble">{{ message.content }}</div>

              <div v-if="message.role === 'assistant' && message.references?.length" class="reference-list">
                <p>这些笔记也许对你有用</p>
                <a
                  v-for="(reference, referenceIndex) in message.references"
                  :key="reference.post_id"
                  :href="reference.link"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="reference-item"
                >
                  <span class="reference-number">{{ String(referenceIndex + 1).padStart(2, '0') }}</span>
                  <span class="reference-copy">
                    <strong>{{ reference.title }}</strong>
                    <small>{{ reference.author || '小旅书用户' }}</small>
                  </span>
                  <SvgIcon name="right" width="14" height="14" color="currentColor" />
                </a>
              </div>
            </div>
          </article>
        </template>
      </div>

      <footer class="composer-area">
        <div class="composer-shell">
          <textarea
            v-model="chatInput"
            maxlength="2000"
            rows="2"
            placeholder="说说你想去哪里，比如：周末去苏州，不想赶行程"
            aria-label="输入旅行问题"
            @keydown.enter.exact.prevent="sendMessage()"
          />
          <button
            type="button"
            class="send-button"
            :disabled="!canSend"
            aria-label="发送消息"
            @click="sendMessage()"
          >
            <SvgIcon :name="loading ? 'loading' : 'right'" :class="{ spinning: loading }" width="19" height="19" color="white" />
          </button>
        </div>
        <div class="composer-meta">
          <span>Enter 发送 · Shift + Enter 换行</span>
          <span :class="{ warning: inputLength > 1800 }">{{ inputLength }} / 2000</span>
        </div>
      </footer>
    </section>
  </main>
</template>

<style scoped>
.travel-chat-page {
  --chat-accent-soft: color-mix(in srgb, var(--primary-color) 10%, var(--bg-color-primary));
  --chat-accent-faint: color-mix(in srgb, var(--primary-color) 4%, var(--bg-color-primary));
  width: 100%;
  height: calc(100dvh - 72px);
  margin-top: 72px;
  padding: 0;
  overflow: hidden;
  box-sizing: border-box;
  background: var(--bg-color-primary);
}

.chat-room {
  width: 100%;
  height: 100%;
  min-height: 0;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--bg-color-primary);
}

.room-toolbar {
  min-height: 58px;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px clamp(18px, 4vw, 44px);
  box-sizing: border-box;
  border-bottom: 1px solid var(--border-color-primary);
  background: var(--bg-color-primary);
}

.journey-label {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 11px;
}

.route-mark {
  position: relative;
  width: 44px;
  height: 26px;
  flex: 0 0 auto;
  border-bottom: 1px dashed color-mix(in srgb, var(--primary-color) 42%, var(--border-color-primary));
  transform: rotate(-7deg);
}

.route-mark i {
  position: absolute;
  bottom: -4px;
  width: 7px;
  height: 7px;
  border: 2px solid var(--bg-color-primary);
  border-radius: 50%;
  background: var(--primary-color);
}

.route-mark i:nth-child(1) { left: 0; }
.route-mark i:nth-child(2) { left: 19px; bottom: 5px; }
.route-mark i:nth-child(3) { right: 0; }

.journey-label > span:last-child {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.journey-label strong {
  color: var(--text-color-primary);
  font-size: 14px;
  font-weight: 720;
}

.journey-label small {
  margin-top: 3px;
  color: var(--text-color-tertiary);
  font-size: 11px;
}

.message-avatar,
.welcome-avatar img {
  width: 100%;
  height: 100%;
  display: block;
  border-radius: 50%;
  object-fit: cover;
}

.clear-button {
  height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 11px;
  border: 1px solid var(--border-color-primary);
  border-radius: 999px;
  color: var(--text-color-secondary);
  background: var(--bg-color-secondary);
  cursor: pointer;
  font-size: 12px;
  transition: color 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}

.clear-button:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--primary-color) 34%, var(--border-color-primary));
  color: var(--primary-color);
  background: var(--chat-accent-soft);
}

.clear-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.conversation {
  position: relative;
  isolation: isolate;
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 22px clamp(18px, 4vw, 44px);
  box-sizing: border-box;
  scrollbar-width: thin;
  scrollbar-color: var(--border-color-primary) transparent;
  background-color: var(--bg-color-primary);
  background-image:
    radial-gradient(circle at 13% 17%, var(--chat-accent-soft) 0, transparent 24%),
    radial-gradient(circle at 84% 76%, var(--chat-accent-soft) 0, transparent 27%),
    radial-gradient(circle at 72% 12%, var(--chat-accent-faint) 0, transparent 20%);
}

.conversation::before {
  position: absolute;
  z-index: 0;
  top: 10%;
  left: 17%;
  width: 68%;
  height: 58%;
  content: '';
  pointer-events: none;
  border: 1px dashed color-mix(in srgb, var(--primary-color) 18%, transparent);
  border-right-color: transparent;
  border-left-color: transparent;
  border-radius: 50%;
  transform: rotate(-11deg);
}

.conversation::after {
  position: absolute;
  z-index: 0;
  inset: 0;
  content: '';
  pointer-events: none;
  opacity: 0.48;
  background-image: radial-gradient(
    circle,
    color-mix(in srgb, var(--primary-color) 22%, transparent) 1px,
    transparent 1.5px
  );
  background-size: 42px 42px;
  -webkit-mask-image: linear-gradient(115deg, transparent 8%, #000 38%, #000 62%, transparent 92%);
  mask-image: linear-gradient(115deg, transparent 8%, #000 38%, #000 62%, transparent 92%);
}

.conversation > * {
  position: relative;
  z-index: 1;
}

.welcome-panel {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.welcome-avatar {
  width: 70px;
  height: 70px;
  padding: 4px;
  box-sizing: border-box;
  border-radius: 50%;
  background: var(--chat-accent-soft);
  box-shadow: 0 7px 18px color-mix(in srgb, var(--shadow-color) 60%, transparent);
}

.welcome-kicker {
  margin: 15px 0 5px;
  color: var(--primary-color);
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0.04em;
}

.welcome-panel h1 {
  max-width: 600px;
  margin: 0;
  color: var(--text-color-primary);
  font-size: clamp(22px, 3vw, 32px);
  line-height: 1.35;
  letter-spacing: -0.035em;
}

.welcome-note {
  margin: 9px 0 0;
  color: var(--text-color-tertiary);
  font-size: 14px;
}

.prompt-list {
  width: min(100%, 680px);
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 22px;
}

.prompt-list button {
  min-width: 0;
  min-height: 46px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 13px;
  border: 1px solid var(--border-color-primary);
  border-radius: 13px;
  color: var(--text-color-secondary);
  background: var(--bg-color-primary);
  cursor: pointer;
  text-align: left;
  font-size: 13px;
  transition: color 0.18s ease, border-color 0.18s ease, transform 0.18s ease;
}

.prompt-list button span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prompt-list button svg {
  flex: 0 0 auto;
}

.prompt-list button:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--primary-color) 36%, var(--border-color-primary));
  color: var(--primary-color);
  transform: translateY(-2px);
}

.message-row {
  width: min(100%, 1180px);
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin: 0 auto 20px;
}

.message-row.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  border: 2px solid var(--bg-color-primary);
  box-shadow: 0 4px 12px var(--shadow-color);
}

.message-row.assistant .message-avatar {
  outline: 2px solid var(--chat-accent-soft);
}

.message-column {
  max-width: min(72%, 720px);
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.message-row.user .message-column {
  align-items: flex-end;
}

.message-author {
  margin: 0 3px 5px;
  color: var(--text-color-tertiary);
  font-size: 11px;
}

.message-bubble {
  max-width: 100%;
  padding: 11px 14px;
  box-sizing: border-box;
  border: 1px solid var(--border-color-primary);
  border-radius: 5px 16px 16px 16px;
  color: var(--text-color-primary);
  background: var(--bg-color-primary);
  font-size: 15px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  box-shadow: 0 5px 16px color-mix(in srgb, var(--shadow-color) 55%, transparent);
}

.message-row.user .message-bubble {
  border-color: var(--primary-color);
  border-radius: 16px 5px 16px 16px;
  color: #fff;
  background: var(--primary-color);
  box-shadow: 0 7px 18px var(--primary-color-shadow);
}

.markdown-body {
  white-space: normal;
}

.markdown-body :deep(p) {
  margin: 0 0 9px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 15px 0 7px;
  color: var(--text-color-primary);
  font-size: 1.08em;
  line-height: 1.4;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 7px 0 10px;
  padding-left: 20px;
}

.markdown-body :deep(li) {
  margin: 4px 0;
}

.markdown-body :deep(a) {
  color: var(--primary-color);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.markdown-body :deep(blockquote) {
  margin: 10px 0;
  padding: 8px 11px;
  border-left: 3px solid var(--primary-color);
  border-radius: 0 8px 8px 0;
  color: var(--text-color-secondary);
  background: var(--chat-accent-faint);
}

.markdown-body :deep(code) {
  padding: 2px 5px;
  border-radius: 5px;
  background: var(--bg-color-secondary);
  font-size: 0.92em;
}

.markdown-body :deep(pre) {
  max-width: 100%;
  overflow-x: auto;
  padding: 12px;
  border-radius: 10px;
  background: var(--bg-color-secondary);
}

.markdown-body :deep(pre code) {
  padding: 0;
  background: transparent;
}

.markdown-body :deep(table) {
  width: 100%;
  margin: 10px 0;
  border-collapse: collapse;
  font-size: 13px;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 7px;
  border: 1px solid var(--border-color-primary);
  text-align: left;
}

.stage-status {
  position: relative;
  min-width: min(360px, 70vw);
  min-height: 42px;
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 8px;
  padding: 7px 11px;
  overflow: hidden;
  box-sizing: border-box;
  border-radius: 12px;
  color: var(--text-color-secondary);
  background: var(--bg-color-secondary);
}

.stage-status::after {
  position: absolute;
  inset: 0;
  content: '';
  pointer-events: none;
  background: linear-gradient(105deg, transparent 28%, var(--chat-accent-soft) 48%, transparent 68%);
  transform: translateX(-110%);
  animation: statusShimmer 2.4s infinite ease-in-out;
}

.stage-compass {
  position: relative;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: var(--primary-color);
  background: var(--chat-accent-soft);
}

.stage-compass > i {
  position: absolute;
  inset: -3px;
  border: 1px dashed color-mix(in srgb, var(--primary-color) 44%, transparent);
  border-radius: 50%;
  animation: compassOrbit 3.2s infinite linear;
}

.stage-compass > i::after {
  position: absolute;
  left: 2px;
  top: 1px;
  width: 5px;
  height: 5px;
  content: '';
  border-radius: 50%;
  background: var(--primary-color);
}

.stage-copy {
  position: relative;
  z-index: 1;
  min-width: 0;
  flex: 1;
  color: var(--text-color-secondary);
  font-size: 13px;
  font-weight: 650;
  background-image: linear-gradient(
    90deg,
    var(--text-color-secondary) 0%,
    var(--text-color-secondary) 34%,
    var(--primary-color) 48%,
    var(--text-color-primary) 54%,
    var(--text-color-secondary) 68%,
    var(--text-color-secondary) 100%
  );
  background-position: 110% 0;
  background-size: 260% 100%;
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: statusTextLight 2.2s infinite linear;
}

.stage-placeholder {
  position: relative;
  z-index: 1;
  min-width: 116px;
  height: 9px;
  flex: 1;
  overflow: hidden;
  border-radius: 999px;
  background: color-mix(in srgb, var(--text-color-tertiary) 12%, transparent);
}

.stage-placeholder i {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, var(--chat-accent-soft), transparent);
  transform: translateX(-110%);
  animation: placeholderLight 1.7s infinite ease-in-out;
}

.stage-route {
  position: relative;
  z-index: 1;
  width: 38px;
  height: 10px;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stage-route::before {
  position: absolute;
  left: 3px;
  right: 3px;
  top: 50%;
  content: '';
  border-top: 1px dashed color-mix(in srgb, var(--primary-color) 38%, var(--border-color-primary));
}

.stage-route i {
  position: relative;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--primary-color);
  animation: routePoint 1.35s infinite ease-in-out;
}

.stage-route i:nth-child(2) { animation-delay: 0.18s; }
.stage-route i:nth-child(3) { animation-delay: 0.36s; }

.reference-list {
  width: min(100%, 560px);
  margin-top: 9px;
}

.reference-list > p {
  margin: 0 2px 6px;
  color: var(--text-color-tertiary);
  font-size: 11px;
}

.reference-item {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) 16px;
  align-items: center;
  gap: 9px;
  margin-top: 5px;
  padding: 8px 10px;
  border: 1px solid var(--border-color-primary);
  border-radius: 11px;
  color: var(--text-color-secondary);
  background: var(--bg-color-primary);
  text-decoration: none;
  transition: border-color 0.18s ease, transform 0.18s ease;
}

.reference-item:hover {
  border-color: color-mix(in srgb, var(--primary-color) 36%, var(--border-color-primary));
  transform: translateX(2px);
}

.reference-number {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  color: var(--primary-color);
  background: var(--chat-accent-soft);
  font-size: 8px;
  font-weight: 800;
}

.reference-copy {
  min-width: 0;
}

.reference-copy strong,
.reference-copy small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reference-copy strong {
  color: var(--text-color-primary);
  font-size: 13px;
}

.reference-copy small {
  margin-top: 2px;
  color: var(--text-color-tertiary);
  font-size: 10px;
}

.composer-area {
  flex: 0 0 auto;
  padding: 12px 16px 10px;
  border-top: 1px solid var(--border-color-primary);
  background: var(--bg-color-primary);
}

.composer-shell {
  width: min(100%, 900px);
  margin: 0 auto;
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 7px 7px 7px 14px;
  border: 1px solid transparent;
  border-radius: 16px;
  background: var(--bg-color-secondary);
}

.composer-shell textarea {
  min-width: 0;
  min-height: 40px;
  max-height: 100px;
  flex: 1;
  padding: 5px 0;
  overflow-y: auto;
  border: none;
  outline: none;
  resize: none;
  color: var(--text-color-primary);
  background: transparent;
  font-size: 14px;
  line-height: 1.55;
}

.composer-shell textarea::placeholder {
  color: var(--text-color-quaternary);
}

.send-button {
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 13px;
  background: var(--primary-color);
  cursor: pointer;
  transition: background 0.18s ease, transform 0.18s ease, opacity 0.18s ease;
}

.send-button:hover:not(:disabled) {
  background: var(--primary-color-dark);
  transform: translateY(-1px);
}

.send-button:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.composer-meta {
  width: min(100%, 900px);
  box-sizing: border-box;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin: 6px auto 0;
  padding: 0 3px;
  color: var(--text-color-tertiary);
  font-size: 10px;
}

.composer-meta .warning {
  color: var(--danger-color);
}

button:focus-visible,
a:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
}

.spinning {
  animation: iconSpin 0.9s linear infinite;
}

@keyframes statusTextLight {
  from { background-position: 110% 0; }
  to { background-position: -45% 0; }
}

@keyframes placeholderLight {
  0%, 18% { transform: translateX(-110%); }
  82%, 100% { transform: translateX(110%); }
}

@keyframes statusShimmer {
  0%, 28% { transform: translateX(-110%); }
  72%, 100% { transform: translateX(110%); }
}

@keyframes compassOrbit {
  to { transform: rotate(360deg); }
}

@keyframes routePoint {
  0%, 60%, 100% { opacity: 0.28; transform: scale(0.72); }
  30% { opacity: 1; transform: scale(1.18); }
}

@keyframes iconSpin {
  to { transform: rotate(360deg); }
}

@media (max-width: 960px) {
  .travel-chat-page {
    /* 移动端底部导航在 48px 之外还包含系统安全区，两者都要从可用高度扣除。 */
    height: calc(100dvh - 120px - env(safe-area-inset-bottom));
    padding: 10px 10px 8px;
  }

  .chat-room {
    border-radius: 0;
  }
}

@media (max-width: 600px) {
  .travel-chat-page {
    padding: 0;
  }

  .chat-room {
    border-right: none;
    border-left: none;
    border-radius: 0;
    box-shadow: none;
  }

  .room-toolbar {
    min-height: 54px;
    padding: 8px 12px;
  }

  .route-mark {
    width: 38px;
  }

  .clear-button span {
    display: none;
  }

  .clear-button {
    width: 34px;
    justify-content: center;
    padding: 0;
  }

  .conversation {
    padding: 16px 12px;
  }

  .welcome-avatar {
    width: 58px;
    height: 58px;
  }

  .welcome-kicker {
    margin-top: 11px;
  }

  .welcome-panel h1 {
    max-width: 340px;
    font-size: 22px;
  }

  .welcome-note {
    max-width: 310px;
    font-size: 12px;
    line-height: 1.55;
  }

  .prompt-list {
    grid-template-columns: 1fr;
    gap: 6px;
    margin-top: 16px;
  }

  .prompt-list button {
    min-height: 40px;
    padding-block: 8px;
  }

  .message-row {
    gap: 8px;
    margin-bottom: 16px;
  }

  .message-avatar {
    width: 30px;
    height: 30px;
  }

  .message-column {
    max-width: calc(88% - 32px);
  }

  .stage-status {
    min-width: min(300px, calc(100vw - 76px));
  }

  .message-bubble {
    padding: 9px 11px;
    font-size: 14px;
  }

  .composer-area {
    padding: 9px 10px 7px;
  }

  .composer-shell {
    padding-left: 11px;
  }

  .composer-meta span:first-child {
    display: none;
  }

  .composer-meta {
    justify-content: flex-end;
  }
}

@media (max-height: 680px) {
  .welcome-avatar {
    width: 50px;
    height: 50px;
  }

  .welcome-kicker {
    margin-top: 8px;
  }

  .welcome-panel h1 {
    font-size: 20px;
  }

  .welcome-note {
    display: none;
  }

  .prompt-list {
    margin-top: 12px;
  }

  .prompt-list button {
    min-height: 36px;
    padding-block: 6px;
  }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }

  .stage-copy {
    color: var(--text-color-secondary);
    background: none;
    -webkit-text-fill-color: currentColor;
  }
}
</style>
