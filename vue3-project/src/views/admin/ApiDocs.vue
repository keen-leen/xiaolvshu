<template>
  <section class="api-docs">
    <header>
      <div>
        <h1>{{ document.title || '小旅书 API' }}</h1>
        <p>OpenAPI {{ document.openapi || '-' }} · {{ endpointCount }} 个接口</p>
      </div>
      <button type="button" :disabled="loading" @click="loadDocument">
        {{ loading ? '加载中…' : '刷新' }}
      </button>
    </header>

    <p v-if="error" class="error">{{ error }}</p>
    <div v-else-if="loading" class="empty">正在从后端生成接口文档…</div>
    <div v-else class="groups">
      <article v-for="group in groups" :key="group.name">
        <h2>{{ group.name }}</h2>
        <div v-for="item in group.items" :key="`${item.method}-${item.path}`" class="endpoint">
          <span class="method" :class="item.method.toLowerCase()">{{ item.method }}</span>
          <code>{{ item.path }}</code>
          <span>{{ item.summary }}</span>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import request from '@/api/request.js'

const document = ref({})
const loading = ref(false)
const error = ref('')

const operations = computed(() => Object.entries(document.value.paths || {}).flatMap(([path, methods]) =>
  Object.entries(methods)
    .filter(([method]) => ['get', 'post', 'put', 'patch', 'delete'].includes(method))
    .map(([method, operation]) => ({
      path,
      method: method.toUpperCase(),
      summary: operation.summary || operation.operationId || '未命名接口',
      tag: operation.tags?.[0] || '其他'
    }))
))

const endpointCount = computed(() => operations.value.length)
const groups = computed(() => {
  const grouped = new Map()
  operations.value.forEach(item => {
    if (!grouped.has(item.tag)) grouped.set(item.tag, [])
    grouped.get(item.tag).push(item)
  })
  return [...grouped.entries()]
    .map(([name, items]) => ({ name, items }))
    .sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
})

async function loadDocument() {
  loading.value = true
  error.value = ''
  try {
    const response = await request.get('/v3/api-docs')
    if (!response?.paths) throw new Error(response?.message || 'OpenAPI 文档格式无效')
    document.value = {
      ...response,
      title: response.info?.title
    }
  } catch (cause) {
    error.value = cause.message || '接口文档加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadDocument)
</script>

<style scoped>
.api-docs {
  padding: 28px;
}

header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

h1,
h2,
p {
  margin: 0;
}

header p {
  margin-top: 8px;
  color: var(--text-secondary, #666);
}

button {
  padding: 8px 18px;
  border: 0;
  border-radius: 8px;
  color: white;
  background: #ff2442;
  cursor: pointer;
}

article {
  margin-bottom: 24px;
  padding: 18px;
  border: 1px solid var(--border-color, #eee);
  border-radius: 12px;
}

article h2 {
  margin-bottom: 12px;
  font-size: 18px;
}

.endpoint {
  display: grid;
  grid-template-columns: 72px minmax(260px, 1fr) 1fr;
  gap: 12px;
  align-items: center;
  padding: 10px 0;
  border-top: 1px solid var(--border-color, #eee);
}

.method {
  font-weight: 700;
  color: #1677ff;
}

.method.post {
  color: #16a34a;
}

.method.put,
.method.patch {
  color: #d97706;
}

.method.delete,
.error {
  color: #dc2626;
}

.empty {
  padding: 60px;
  text-align: center;
  color: var(--text-secondary, #666);
}

@media (max-width: 800px) {
  .endpoint {
    grid-template-columns: 64px 1fr;
  }

  .endpoint > :last-child {
    grid-column: 2;
  }
}
</style>
