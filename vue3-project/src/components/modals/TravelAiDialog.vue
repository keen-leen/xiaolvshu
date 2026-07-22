<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import travelAiApi from '@/api/ai'
import { useTravelAiStream } from '@/composables/useTravelAiStream'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import SvgIcon from '@/components/SvgIcon.vue'
import FloatingActionButton from '@/components/FloatingActionButton.vue'
import { useTravelAiStore } from '@/stores/travelAi'
import { useUserStore } from '@/stores/user'
import defaultAvatar from '@/assets/imgs/avatar.png'

const route = useRoute()
const router = useRouter()
const travelAiStore = useTravelAiStore()
const userStore = useUserStore()

const loading = ref(false)
const userInput = ref('')
const currentStatusText = ref('')
const stream = useTravelAiStream()
const messages = ref([
  {
    role: 'assistant',
    content: '你好，我是小旅书旅行助手。告诉我你的目的地、天数和预算，我会结合社区笔记为你做攻略。',
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

const hiddenOnRoute = computed(() => route.path.startsWith('/admin') || route.path.startsWith('/travel-ai'))
const showStandaloneTrigger = computed(() => !route.path.startsWith('/explore') && !route.path.startsWith('/search_result'))
const open = computed(() => travelAiStore.visible)

const canSend = computed(() => !loading.value && userInput.value.trim().length > 0 && userInput.value.length <= 2000)
const userAvatar = computed(() => userStore.userInfo?.avatar || defaultAvatar)
const userName = computed(() => userStore.userInfo?.nickname || '你')
const hasConversation = computed(() => messages.value.some((item) => item.role === 'user'))
const userQuestionCount = computed(() => messages.value.filter((item) => item.role === 'user').length)

const openDialog = () => {
  travelAiStore.openAssistant()
}

const closeDialog = () => {
  // 关闭弹窗意味着用户已离开当前输出视图，必须同时中止网络流，避免后台继续消耗模型额度。
  if (loading.value) {
    stream.stop()
  }
  travelAiStore.closeAssistant()
}

const openFullPage = () => {
  closeDialog()
  router.push('/travel-ai')
}

const scrollToBottom = () => {
  if (!messageListRef.value) {
    return
  }
  messageListRef.value.scrollTop = messageListRef.value.scrollHeight
}

const handleGlobalKeydown = (event) => {
  if (event.key === 'Escape' && open.value) {
    closeDialog()
  }
}

onMounted(() => window.addEventListener('keydown', handleGlobalKeydown))
onUnmounted(() => window.removeEventListener('keydown', handleGlobalKeydown))

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

watch(open, (visible) => {
  // 发现页和搜索页的共享浮动按钮直接通过 store 打开弹窗。
  // 把滚动复位放在可见状态监听中，才能保证所有入口的打开行为一致。
  if (visible) {
    nextTick(scrollToBottom)
  }
})

const buildHistory = () => {
  return messages.value
    .filter((item) => (item.role === 'user' || item.role === 'assistant') && item.content?.trim())
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
  // 必须在追加本轮用户问题和空助手占位前构建历史，避免当前问题重复进入模型上下文。
  const history = buildHistory()
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
  currentStatusText.value = ''
  nextTick(scrollToBottom)
  const signal = stream.begin((text) => {
    // composable 已把高频 token 合并为约 40ms 一批，此处只做一次响应式写入和滚动。
    messages.value[assistantIndex].content += text
    nextTick(scrollToBottom)
  })

  try {
    await travelAiApi.chat(
      {
        message: content,
        topK: 5,
        history
      },
      {
        onChunk: (chunk) => {
          stream.append(extractChunkText(chunk))
        },
        onRefs: (refs) => {
          // refs 是正文后的独立事件；先冲刷剩余 token，保证引用卡片不会先于最后一段正文出现。
          stream.flush()
          messages.value[assistantIndex].references = refs || []
          nextTick(scrollToBottom)
        },
        onStep: (step) => {
          // thought 是后端生成的安全状态文案；浮窗与完整页面保持同一数据源，不用 action 或工具名拼装替代文字。
          currentStatusText.value = typeof step?.thought === 'string' ? step.thought.trim() : ''
          nextTick(scrollToBottom)
        },
        onError: (error) => {
          stream.flush()
          messages.value[assistantIndex].content = error?.message || '流式响应失败，请稍后重试'
        }
      },
      { signal }
    )

    stream.flush()
    if (!messages.value[assistantIndex].content) {
      messages.value[assistantIndex].content = '已完成攻略生成。'
    }
  } catch (e) {
    stream.flush()
    if (stream.isAbortError(e)) {
      // 主动停止不是故障：保留已经生成的部分答案，仅在尚无正文时显示停止提示。
      if (!messages.value[assistantIndex].content) {
        messages.value[assistantIndex].content = '已停止生成。'
      }
    } else {
      messages.value[assistantIndex].content = `流式请求失败：${e?.message || '请稍后重试'}`
      messages.value[assistantIndex].references = []
    }
  } finally {
    stream.finish()
    loading.value = false
    currentStatusText.value = ''
    nextTick(scrollToBottom)
  }
}

const submitOrStop = () => {
  // 发送按钮在生成期间复用为停止按钮，避免另设入口造成移动端布局跳动。
  if (loading.value) {
    stream.stop()
    currentStatusText.value = ''
    return
  }
  sendMessage()
}

const handleAvatarError = (event) => {
  event.target.onerror = null
  event.target.src = defaultAvatar
}
</script>

<template>
  <div v-if="!hiddenOnRoute && (open || showStandaloneTrigger)" class="travel-ai-widget">
    <FloatingActionButton
      v-if="!open && showStandaloneTrigger"
      icon="magic"
      label="旅行助手"
      featured
      @click="openDialog"
    />

    <div v-if="open" class="dialog-mask" @click="closeDialog">
      <div class="dialog-card" role="dialog" aria-modal="true" aria-labelledby="travel-ai-dialog-title" @click.stop>
        <header class="dialog-header">
          <div class="journey-label">
            <span class="route-mark" aria-hidden="true"><i></i><i></i><i></i></span>
            <span>
              <strong id="travel-ai-dialog-title">{{ hasConversation ? '这趟旅程正在慢慢清晰' : '从一个想法开始' }}</strong>
              <small>{{ hasConversation ? `已经聊了 ${userQuestionCount} 个问题` : '不用想好再开口' }}</small>
            </span>
          </div>
          <div class="header-actions">
            <button type="button" class="page-btn" @click="openFullPage">
              <span>完整规划</span><SvgIcon name="right" width="13" height="13" color="currentColor" />
            </button>
            <button type="button" class="close-btn" aria-label="关闭旅行助手" @click="closeDialog">
              <SvgIcon name="close" width="14" height="14" color="currentColor" />
            </button>
          </div>
        </header>

        <div ref="messageListRef" class="message-list" aria-live="polite">
          <div v-for="(item, idx) in messages" :key="idx" :class="['message-item', item.role]">
            <img
              class="message-avatar"
              :src="item.role === 'assistant' ? defaultAvatar : userAvatar"
              :alt="item.role === 'assistant' ? '小旅书头像' : `${userName}的头像`"
              @error="handleAvatarError"
            />
            <div class="message-content">
              <div
                v-if="item.role === 'assistant' && idx === messages.length - 1 && loading"
                :class="['stage-status', { waiting: !currentStatusText }]"
                :aria-label="currentStatusText || '等待后端返回当前步骤'"
                aria-live="polite"
                role="status"
              >
                <span class="stage-compass" aria-hidden="true">
                  <SvgIcon name="magic" width="13" height="13" color="currentColor" />
                  <i></i>
                </span>
                <span v-if="currentStatusText" class="stage-copy">{{ currentStatusText }}</span>
                <span v-else class="stage-placeholder" aria-hidden="true"><i></i></span>
                <span class="stage-route" aria-hidden="true"><i></i><i></i><i></i></span>
              </div>
              <div v-if="item.role === 'assistant' && item.content" class="message-bubble markdown-body" v-html="renderAssistantMarkdown(item.content)"></div>
              <div v-else-if="item.role === 'user'" class="message-bubble">{{ item.content }}</div>

              <div v-if="item.role === 'assistant' && item.references && item.references.length > 0" class="source-list">
                <p><span>社区参考</span><small>{{ item.references.length }} 篇笔记</small></p>
                <a
                  v-for="(ref, refIndex) in item.references"
                  :key="ref.post_id"
                  class="source-item"
                  :href="ref.link"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <span class="source-index">{{ ref.source_id || `S${refIndex + 1}` }}</span>
                  <span class="source-content"><strong>{{ ref.title }}</strong><small>{{ ref.author }}</small></span>
                  <SvgIcon name="right" width="13" height="13" color="currentColor" />
                </a>
              </div>
            </div>
          </div>
        </div>

        <footer class="input-area">
          <div class="input-shell">
            <textarea
              v-model="userInput"
              class="chat-input"
              maxlength="2000"
              placeholder="写下目的地、天数、预算和你在意的事…"
              rows="2"
              @keydown.enter.exact.prevent="submitOrStop"
            />
            <button type="button" class="send-btn" :disabled="!loading && !canSend" :aria-label="loading ? '停止生成' : '发送旅行需求'" @click="submitOrStop">
              <SvgIcon :name="loading ? 'close' : 'right'" width="18" height="18" color="white" />
            </button>
          </div>
          <div class="input-meta"><span>Enter 发送</span><span>回复将参考社区笔记</span></div>
        </footer>
      </div>
    </div>
  </div>
</template>

<style scoped>
.travel-ai-widget {
  --travel-accent-soft: color-mix(in srgb, var(--primary-color) 11%, var(--bg-color-primary));
  --travel-accent-faint: color-mix(in srgb, var(--primary-color) 5%, var(--bg-color-primary));
  position: fixed;
  right: 12px;
  bottom: 22px;
  z-index: 2100;
}

.dialog-mask {
  position: fixed;
  inset: 0;
  background: color-mix(in srgb, var(--overlay-bg) 86%, transparent);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  padding: 20px;
}

.dialog-card {
  width: min(466px, calc(100vw - 24px));
  height: min(740px, calc(100dvh - 40px));
  background: var(--bg-color-primary);
  border: 1px solid color-mix(in srgb, var(--primary-color) 12%, var(--border-color-primary));
  border-radius: 24px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.24);
  animation: dialogEnter 0.25s ease-out;
}

.dialog-header {
  position: relative;
  min-height: 58px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 9px 14px;
  box-sizing: border-box;
  border-bottom: 1px solid var(--border-color-primary);
  background: var(--bg-color-primary);
}

.journey-label {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.route-mark {
  position: relative;
  width: 40px;
  height: 24px;
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
.route-mark i:nth-child(2) { left: 17px; bottom: 5px; }
.route-mark i:nth-child(3) { right: 0; }

.journey-label > span:last-child {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.journey-label strong {
  overflow: hidden;
  color: var(--text-color-primary);
  font-size: 13px;
  font-weight: 720;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.journey-label small {
  margin-top: 3px;
  color: var(--text-color-tertiary);
  font-size: 10px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-btn {
  height: 30px;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 0 9px;
  border: 1px solid var(--border-color-primary);
  border-radius: 999px;
  background: var(--bg-color-secondary);
  color: var(--text-color-primary);
  font-size: 10px;
  cursor: pointer;
}

.close-btn {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 50%;
  background: var(--bg-color-secondary);
  color: var(--text-color-secondary);
  cursor: pointer;
}

.page-btn:hover,
.close-btn:hover {
  color: var(--primary-color);
  background: var(--travel-accent-soft);
}

.message-list {
  position: relative;
  isolation: isolate;
  flex: 1;
  padding: 17px 14px;
  overflow-y: auto;
  background-color: var(--bg-color-secondary);
  background-image:
    radial-gradient(circle at 8% 17%, var(--travel-accent-soft) 0, transparent 28%),
    radial-gradient(circle at 92% 76%, var(--travel-accent-soft) 0, transparent 31%),
    radial-gradient(circle at 76% 6%, var(--travel-accent-faint) 0, transparent 22%);
}

.message-list::before {
  position: absolute;
  z-index: 0;
  top: 12%;
  left: 12%;
  width: 76%;
  height: 54%;
  content: '';
  pointer-events: none;
  border: 1px dashed color-mix(in srgb, var(--primary-color) 18%, transparent);
  border-right-color: transparent;
  border-left-color: transparent;
  border-radius: 50%;
  transform: rotate(-13deg);
}

.message-list::after {
  position: absolute;
  z-index: 0;
  inset: 0;
  content: '';
  pointer-events: none;
  opacity: 0.42;
  background-image: radial-gradient(circle, color-mix(in srgb, var(--primary-color) 22%, transparent) 1px, transparent 1.5px);
  background-size: 34px 34px;
  -webkit-mask-image: linear-gradient(120deg, transparent 6%, #000 38%, #000 64%, transparent 94%);
  mask-image: linear-gradient(120deg, transparent 6%, #000 38%, #000 64%, transparent 94%);
}

.message-list > * {
  position: relative;
  z-index: 1;
}

.message-item {
  margin-bottom: 15px;
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  display: block;
  border: 2px solid var(--bg-color-primary);
  border-radius: 50%;
  object-fit: cover;
  box-shadow: 0 3px 10px color-mix(in srgb, var(--shadow-color) 68%, transparent);
}

.message-content {
  max-width: calc(90% - 36px);
}

.message-bubble {
  border-radius: 5px 15px 15px 15px;
  padding: 9px 11px;
  white-space: pre-wrap;
  line-height: 1.62;
  font-size: 13px;
}

.markdown-body {
  white-space: normal;
}

.markdown-body :deep(p) {
  margin: 0 0 7px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 12px 0 7px;
  line-height: 1.35;
  color: var(--text-color-primary);
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 6px 0 9px;
  padding-left: 18px;
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
  border-radius: 15px 5px 15px 15px;
}

.message-item.assistant .message-bubble {
  color: var(--text-color-primary);
  background: var(--bg-color-primary);
  border: 1px solid var(--border-color-primary);
}

.stage-status {
  position: relative;
  min-width: min(250px, 66vw);
  min-height: 38px;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 7px;
  padding: 6px 9px;
  overflow: hidden;
  box-sizing: border-box;
  border-radius: 12px;
  color: var(--text-color-secondary);
  background: color-mix(in srgb, var(--bg-color-primary) 86%, var(--travel-accent-soft));
}

.stage-status::after {
  position: absolute;
  inset: 0;
  content: '';
  pointer-events: none;
  background: linear-gradient(105deg, transparent 28%, var(--travel-accent-soft) 48%, transparent 68%);
  transform: translateX(-110%);
  animation: statusShimmer 2.4s infinite ease-in-out;
}

.stage-compass {
  position: relative;
  width: 26px;
  height: 26px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: var(--primary-color);
  background: var(--travel-accent-soft);
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
  top: 1px;
  left: 2px;
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
  font-size: 12px;
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
  min-width: 86px;
  height: 8px;
  flex: 1;
  overflow: hidden;
  border-radius: 999px;
  background: color-mix(in srgb, var(--text-color-tertiary) 12%, transparent);
}

.stage-placeholder i {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, var(--travel-accent-soft), transparent);
  transform: translateX(-110%);
  animation: placeholderLight 1.7s infinite ease-in-out;
}

.stage-route {
  position: relative;
  z-index: 1;
  width: 32px;
  height: 10px;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stage-route::before {
  position: absolute;
  top: 50%;
  right: 3px;
  left: 3px;
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

.source-list {
  margin-top: 9px;
}

.source-list > p {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 0 0 6px;
  color: var(--text-color-secondary);
  font-size: 12px;
  font-weight: 700;
}

.source-list > p small {
  color: var(--text-color-tertiary);
  font-size: 10px;
  font-weight: 500;
}

.source-item {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) 14px;
  align-items: center;
  gap: 8px;
  margin-top: 5px;
  text-decoration: none;
  border: 1px solid var(--border-color-primary);
  border-radius: 11px;
  padding: 7px 8px;
  color: var(--text-color-secondary);
  background: var(--bg-color-primary);
  transition: border-color 0.18s ease, transform 0.18s ease;
}

.source-item:hover {
  border-color: color-mix(in srgb, var(--primary-color) 36%, var(--border-color-primary));
  transform: translateX(2px);
}

.source-index {
  display: grid;
  width: 27px;
  height: 27px;
  place-items: center;
  border-radius: 8px;
  color: var(--primary-color);
  background: var(--travel-accent-soft);
  font-size: 8px;
  font-weight: 800;
}

.source-content {
  min-width: 0;
}

.source-content strong,
.source-content small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-content strong {
  color: var(--text-color-primary);
  font-size: 12px;
}

.source-content small {
  margin-top: 2px;
  color: var(--text-color-tertiary);
  font-size: 10px;
}

.input-area {
  border-top: 1px solid var(--border-color-primary);
  padding: 11px 13px 12px;
  background: var(--bg-color-primary);
}

.input-shell {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 6px 6px 6px 11px;
  border: 1px solid transparent;
  border-radius: 15px;
  background: var(--bg-color-secondary);
}

.chat-input {
  flex: 1;
  min-width: 0;
  max-height: 100px;
  border: none;
  resize: none;
  padding: 4px 0;
  box-sizing: border-box;
  background: transparent;
  color: var(--text-color-primary);
  font-size: 13px;
  line-height: 1.55;
  outline: none;
}

.send-btn {
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 11px;
  background: var(--primary-color);
  cursor: pointer;
  transition: background 0.2s ease, transform 0.2s ease;
}

.send-btn:hover:not(:disabled) {
  background: var(--primary-color-dark);
  transform: translateY(-1px);
}

.send-btn:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.input-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  color: var(--text-color-tertiary);
  font-size: 10px;
}

button:focus-visible,
a:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
}

@keyframes dialogEnter {
  from { opacity: 0; transform: translateY(18px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
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

.spinning {
  animation: iconSpin 0.9s linear infinite;
}

@media (max-width: 768px) {
  .travel-ai-widget {
    right: 14px;
    bottom: 62px;
  }

  .dialog-mask {
    align-items: flex-end;
    padding: 0;
  }

  .dialog-card {
    width: 100vw;
    height: min(760px, 94dvh);
    border-right: none;
    border-bottom: none;
    border-left: none;
    border-radius: 24px 24px 0 0;
    padding-bottom: env(safe-area-inset-bottom);
  }

  .dialog-card::before {
    width: 38px;
    height: 4px;
    flex: 0 0 auto;
    align-self: center;
    margin-top: 7px;
    content: '';
    border-radius: 999px;
    background: var(--border-color-primary);
  }

  .dialog-header {
    padding-top: 10px;
  }

  .message-list {
    padding-bottom: 24px;
  }
}

@media (max-width: 420px) {
  .travel-ai-widget {
    right: 12px;
  }

  .page-btn span {
    display: none;
  }

  .page-btn {
    width: 30px;
    justify-content: center;
    padding: 0;
  }

  .message-content {
    max-width: calc(91% - 32px);
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
