<script setup>
import { computed, nextTick, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import SvgIcon from '@/components/SvgIcon.vue'
import travelAiApi from '@/api/ai'

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

const form = ref({
  destination: '',
  days: 3,
  budget: '',
  travelers: '',
  travel_style: '轻松休闲',
  depart_date: '',
  interests: ['美食', '景点'],
  avoid: '',
  message: ''
})

const styles = ['轻松休闲', '深度文化', '亲子友好', '省钱高效', '拍照出片']
const interests = ['美食', '景点', '住宿', '交通', '购物', '自然风光', '历史文化', '小众路线']
const templates = [
  { title: '周末快闪', desc: '2天高效游玩', data: { days: 2, travel_style: '省钱高效', interests: ['美食', '景点', '交通'] } },
  { title: '亲子慢游', desc: '节奏舒适少折腾', data: { days: 4, travel_style: '亲子友好', interests: ['住宿', '景点', '美食'] } },
  { title: '出片路线', desc: '拍照和小众点位', data: { days: 3, travel_style: '拍照出片', interests: ['小众路线', '自然风光', '美食'] } }
]

const loading = ref(false)
const errorText = ref('')
const planResult = ref(null)
const chatInput = ref('')
const messages = ref([
  {
    role: 'assistant',
    content: '告诉我目的地、天数、预算和偏好，我会结合小旅书社区笔记生成一份可执行的旅行计划。',
    references: [],
    agentSteps: []
  }
])
const messageListRef = ref(null)
const chatLoading = ref(false)

const canGenerate = computed(() => !loading.value && (form.value.destination.trim() || form.value.message.trim()))
const canChat = computed(() => !chatLoading.value && chatInput.value.trim())

const renderMarkdown = (content) => {
  if (!content) {
    return ''
  }

  return DOMPurify.sanitize(md.render(String(content)), {
    USE_PROFILES: { html: true },
    ALLOWED_ATTR: ['href', 'target', 'rel', 'title']
  })
}

const toggleInterest = (item) => {
  const current = form.value.interests
  if (current.includes(item)) {
    form.value.interests = current.filter(value => value !== item)
  } else {
    form.value.interests = [...current, item]
  }
}

const applyTemplate = (template) => {
  form.value = {
    ...form.value,
    ...template.data
  }
}

const buildMessage = () => {
  const parts = []
  if (form.value.destination) parts.push(`目的地：${form.value.destination}`)
  if (form.value.days) parts.push(`天数：${form.value.days}天`)
  if (form.value.budget) parts.push(`预算：${form.value.budget}`)
  if (form.value.travelers) parts.push(`同行人：${form.value.travelers}`)
  if (form.value.travel_style) parts.push(`风格：${form.value.travel_style}`)
  if (form.value.depart_date) parts.push(`出发日期：${form.value.depart_date}`)
  if (form.value.interests.length) parts.push(`兴趣：${form.value.interests.join('、')}`)
  if (form.value.avoid) parts.push(`避开：${form.value.avoid}`)
  if (form.value.message) parts.push(`补充需求：${form.value.message}`)
  return parts.join('；')
}

const generatePlan = async () => {
  if (!canGenerate.value) {
    return
  }

  loading.value = true
  errorText.value = ''

  const result = {
    answer: '',
    references: [],
    agent_steps: [],
    model: 'Travel Agent'
  }
  planResult.value = result

  try {
    await travelAiApi.chat(
      {
        message: buildMessage(),
        topK: 6
      },
      {
        onChunk: (chunk) => {
          result.answer += chunk
          nextTick(scrollMessages)
        },
        onRefs: (refs) => {
          result.references = refs || []
        },
        onStep: (step) => {
          result.agent_steps.push(step)
        },
        onError: (text) => {
          errorText.value = text || '生成失败，请稍后重试'
        }
      }
    )

    messages.value.push({
      role: 'assistant',
      content: result.answer || '已生成旅行计划。',
      references: result.references,
      agentSteps: result.agent_steps
    })
    await nextTick()
    scrollMessages()
  } catch (error) {
    errorText.value = error?.message || '生成失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const scrollMessages = () => {
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}

const buildHistory = () => {
  return messages.value.slice(-8).map(item => ({
    role: item.role,
    content: item.content
  }))
}

const sendChat = async () => {
  if (!canChat.value) {
    return
  }

  const content = chatInput.value.trim()
  chatInput.value = ''
  messages.value.push({ role: 'user', content, references: [] })
  const assistant = { role: 'assistant', content: '', references: [], agentSteps: [] }
  messages.value.push(assistant)
  const index = messages.value.length - 1
  chatLoading.value = true
  await nextTick()
  scrollMessages()

  try {
    await travelAiApi.chat(
      {
        message: content,
        topK: 5,
        history: buildHistory()
      },
      {
        onChunk: (chunk) => {
          messages.value[index].content += chunk
          nextTick(scrollMessages)
        },
        onRefs: (refs) => {
          messages.value[index].references = refs || []
        },
        onStep: (step) => {
          messages.value[index].agentSteps.push(step)
          nextTick(scrollMessages)
        },
        onError: (text) => {
          messages.value[index].content = text || '生成失败，请稍后重试。'
        }
      }
    )
  } catch (error) {
    messages.value[index].content = error?.message || '生成失败，请稍后重试。'
  } finally {
    if (!messages.value[index].content) {
      messages.value[index].content = '已完成回复。'
    }
    chatLoading.value = false
    nextTick(scrollMessages)
  }
}
</script>

<template>
  <div class="travel-ai-page">
    <section class="planner-panel">
      <div class="panel-heading">
        <div>
          <p class="eyebrow">Travel AI</p>
          <h1>旅行助手</h1>
        </div>
        <button class="generate-btn" :disabled="!canGenerate" @click="generatePlan">
          <SvgIcon name="magic" width="18" height="18" color="white" />
          <span>{{ loading ? '生成中...' : '生成攻略' }}</span>
        </button>
      </div>

      <div class="template-row">
        <button v-for="item in templates" :key="item.title" class="template-btn" @click="applyTemplate(item)">
          <strong>{{ item.title }}</strong>
          <span>{{ item.desc }}</span>
        </button>
      </div>

      <div class="form-grid">
        <label>
          <span>目的地</span>
          <input v-model="form.destination" placeholder="例如 成都、杭州、西安" />
        </label>
        <label>
          <span>天数</span>
          <input v-model.number="form.days" type="number" min="1" max="30" />
        </label>
        <label>
          <span>预算</span>
          <input v-model="form.budget" placeholder="例如 3000以内/人均1500" />
        </label>
        <label>
          <span>同行人</span>
          <input v-model="form.travelers" placeholder="例如 亲子、情侣、朋友" />
        </label>
        <label>
          <span>出发日期</span>
          <input v-model="form.depart_date" type="date" />
        </label>
        <label>
          <span>旅行风格</span>
          <select v-model="form.travel_style">
            <option v-for="item in styles" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>
      </div>

      <div class="interest-section">
        <span class="field-title">兴趣偏好</span>
        <div class="chip-row">
          <button
            v-for="item in interests"
            :key="item"
            class="chip"
            :class="{ active: form.interests.includes(item) }"
            @click="toggleInterest(item)"
          >
            {{ item }}
          </button>
        </div>
      </div>

      <label class="wide-field">
        <span>补充需求</span>
        <textarea v-model="form.message" rows="3" placeholder="例如 不想太累、想避开网红排队点、希望公共交通为主" />
      </label>
      <label class="wide-field">
        <span>避开内容</span>
        <input v-model="form.avoid" placeholder="例如 夜生活、爬山、自驾、太贵的餐厅" />
      </label>

      <p v-if="errorText" class="error-text">{{ errorText }}</p>
    </section>

    <section class="result-panel">
      <div v-if="!planResult" class="empty-state">
        <SvgIcon name="magic" width="34" height="34" />
        <h2>先填写你的旅行需求</h2>
        <p>生成后会在这里展示每日路线、预算、提醒和社区笔记引用。</p>
      </div>

      <template v-else>
        <div class="result-header">
          <h2>生成结果</h2>
          <span>{{ planResult.model || 'AI' }}</span>
        </div>

        <div class="markdown-result" v-html="renderMarkdown(planResult.answer)"></div>

        <div v-if="planResult.agent_steps?.length" class="agent-trace">
          <span v-for="step in planResult.agent_steps" :key="step.step">
            {{ step.action === 'tool' ? step.tool_call?.tool_name : '生成答案' }}
          </span>
        </div>

        <div v-if="planResult.itinerary_days?.length" class="structured-block">
          <h3>每日路线</h3>
          <article v-for="day in planResult.itinerary_days" :key="day.day" class="day-card">
            <strong>Day {{ day.day }} {{ day.title }}</strong>
            <p>{{ day.summary }}</p>
            <ul>
              <li v-for="item in day.items" :key="item">{{ item }}</li>
            </ul>
          </article>
        </div>

        <div v-if="planResult.budget_items?.length" class="structured-block compact-list">
          <h3>预算建议</h3>
          <p v-for="item in planResult.budget_items" :key="item.name">
            <strong>{{ item.name }}</strong>
            <span>{{ item.amount }}</span>
            <em>{{ item.note }}</em>
          </p>
        </div>

        <div v-if="planResult.references?.length" class="reference-block">
          <h3>参考笔记</h3>
          <a
            v-for="ref in planResult.references"
            :key="ref.post_id"
            :href="ref.link"
            class="reference-card"
            target="_blank"
            rel="noopener noreferrer"
          >
            <strong>{{ ref.title }}</strong>
            <span>{{ ref.author }}</span>
            <p>{{ ref.summary }}</p>
          </a>
        </div>
      </template>
    </section>

    <section class="chat-panel">
      <div class="chat-heading">
        <h2>继续追问</h2>
        <span>支持流式回复</span>
      </div>
      <div ref="messageListRef" class="message-list">
        <div v-for="(item, index) in messages" :key="index" :class="['message-item', item.role]">
          <div v-if="item.role === 'assistant'" class="message-bubble" v-html="renderMarkdown(item.content)"></div>
          <div v-else class="message-bubble">{{ item.content }}</div>
          <div v-if="item.role === 'assistant' && item.agentSteps?.length" class="agent-trace compact">
            <span v-for="step in item.agentSteps" :key="step.step">
              {{ step.action === 'tool' ? step.tool_call?.tool_name : '生成答案' }}
            </span>
          </div>
        </div>
      </div>
      <div class="chat-input-row">
        <textarea v-model="chatInput" rows="2" placeholder="例如：把行程改得轻松一点" @keydown.enter.exact.prevent="sendChat" />
        <button :disabled="!canChat" @click="sendChat">{{ chatLoading ? '发送中' : '发送' }}</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.travel-ai-page {
  width: 100%;
  min-height: 100vh;
  padding: 92px 18px 28px;
  box-sizing: border-box;
  display: grid;
  grid-template-columns: minmax(280px, 420px) minmax(0, 1fr);
  gap: 16px;
  background: var(--bg-color-primary);
}

.planner-panel,
.result-panel,
.chat-panel {
  border: 1px solid var(--border-color-primary);
  background: var(--bg-color-primary);
  border-radius: 8px;
  box-shadow: 0 8px 22px var(--shadow-color);
}

.planner-panel,
.result-panel {
  padding: 18px;
}

.planner-panel {
  align-self: start;
}

.panel-heading,
.result-header,
.chat-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.eyebrow {
  margin: 0 0 4px;
  color: var(--primary-color);
  font-size: 12px;
  font-weight: 700;
}

h1,
h2,
h3 {
  margin: 0;
  color: var(--text-color-primary);
}

h1 {
  font-size: 28px;
}

h2 {
  font-size: 18px;
}

h3 {
  font-size: 15px;
}

.generate-btn,
.chat-input-row button {
  border: none;
  border-radius: 999px;
  background: var(--primary-color);
  color: #fff;
  height: 38px;
  padding: 0 16px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  cursor: pointer;
}

.generate-btn:disabled,
.chat-input-row button:disabled {
  background: var(--disabled-bg);
  cursor: not-allowed;
}

.template-row,
.chip-row {
  display: flex;
  gap: 8px;
  overflow-x: auto;
}

.template-row {
  margin: 18px 0;
}

.template-btn {
  min-width: 118px;
  border: 1px solid var(--border-color-primary);
  border-radius: 8px;
  background: var(--bg-color-secondary);
  color: var(--text-color-primary);
  padding: 10px;
  text-align: left;
  cursor: pointer;
}

.template-btn span {
  display: block;
  margin-top: 3px;
  color: var(--text-color-tertiary);
  font-size: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

label,
.interest-section {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

label span,
.field-title {
  color: var(--text-color-secondary);
  font-size: 13px;
  font-weight: 700;
}

input,
select,
textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--border-color-primary);
  border-radius: 8px;
  background: var(--bg-color-primary);
  color: var(--text-color-primary);
  padding: 10px 12px;
  outline: none;
}

textarea {
  resize: vertical;
}

.interest-section,
.wide-field {
  margin-top: 14px;
}

.chip {
  border: 1px solid var(--border-color-primary);
  border-radius: 999px;
  background: var(--bg-color-secondary);
  color: var(--text-color-secondary);
  padding: 7px 12px;
  white-space: nowrap;
  cursor: pointer;
}

.chip.active {
  border-color: var(--primary-color);
  background: var(--primary-color);
  color: #fff;
}

.error-text {
  margin: 12px 0 0;
  color: var(--danger-color);
  font-size: 13px;
}

.result-panel {
  min-height: 680px;
  overflow: hidden;
}

.empty-state {
  min-height: 560px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: var(--text-color-secondary);
}

.empty-state h2 {
  margin-top: 14px;
}

.markdown-result {
  margin-top: 16px;
  line-height: 1.7;
  color: var(--text-color-primary);
}

.markdown-result :deep(p) {
  margin: 0 0 10px;
}

.markdown-result :deep(ul),
.markdown-result :deep(ol) {
  padding-left: 22px;
}

.agent-trace {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.agent-trace span {
  border: 1px solid var(--border-color-primary);
  border-radius: 999px;
  background: var(--bg-color-secondary);
  color: var(--text-color-secondary);
  padding: 4px 9px;
  font-size: 12px;
}

.agent-trace.compact {
  margin-top: 6px;
}

.agent-trace.compact span {
  font-size: 11px;
  padding: 3px 8px;
}

.structured-block,
.reference-block {
  margin-top: 18px;
}

.day-card,
.reference-card,
.compact-list p {
  display: block;
  margin-top: 10px;
  border: 1px solid var(--border-color-primary);
  border-radius: 8px;
  padding: 12px;
  color: var(--text-color-primary);
  text-decoration: none;
  background: var(--bg-color-secondary);
}

.day-card p,
.reference-card p {
  margin: 6px 0 0;
  color: var(--text-color-secondary);
  font-size: 13px;
}

.compact-list p {
  display: flex;
  gap: 10px;
  align-items: baseline;
}

.compact-list em {
  color: var(--text-color-tertiary);
  font-style: normal;
  font-size: 12px;
}

.chat-panel {
  grid-column: 1 / -1;
  overflow: hidden;
}

.chat-heading {
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-color-primary);
}

.chat-heading span,
.result-header span {
  color: var(--text-color-tertiary);
  font-size: 12px;
}

.message-list {
  height: 260px;
  overflow-y: auto;
  padding: 14px;
  background: var(--bg-color-secondary);
}

.message-item {
  margin-bottom: 10px;
  display: flex;
}

.message-item.user {
  justify-content: flex-end;
}

.message-bubble {
  max-width: min(720px, 88%);
  border: 1px solid var(--border-color-primary);
  border-radius: 12px;
  padding: 10px 12px;
  background: var(--bg-color-primary);
  color: var(--text-color-primary);
  line-height: 1.55;
  white-space: pre-wrap;
}

.message-item.user .message-bubble {
  border-color: var(--primary-color);
  background: var(--primary-color);
  color: #fff;
}

.chat-input-row {
  display: flex;
  gap: 10px;
  padding: 12px;
}

.chat-input-row textarea {
  flex: 1;
}

@media (max-width: 960px) {
  .travel-ai-page {
    grid-template-columns: 1fr;
    padding: 82px 12px 64px;
  }

  .chat-panel {
    grid-column: auto;
  }
}

@media (max-width: 560px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .panel-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .generate-btn {
    width: 100%;
    justify-content: center;
  }
}
</style>
