<template>
  <div class="index-management">
    <section class="index-card">
      <div>
        <h2>全文索引</h2>
        <p>同步未索引或内容已更新的已发布笔记，不会生成向量。</p>
      </div>
      <button :disabled="searchLoading" @click="syncSearchIndex">
        {{ searchLoading ? '同步中…' : '同步全文索引' }}
      </button>
      <p v-if="searchResult" class="result">{{ searchResult }}</p>
    </section>

    <section class="index-card">
      <div>
        <h2>RAG 向量索引</h2>
        <p>仅增量生成未向量化或内容已更新笔记的 embedding chunks。</p>
      </div>
      <button :disabled="ragLoading" @click="syncRagIndex">
        {{ ragLoading ? '向量化中…' : '增量生成向量' }}
      </button>
      <p v-if="ragResult" class="result">{{ ragResult }}</p>
    </section>

    <MessageToast
      v-if="showToast"
      :message="toastMessage"
      :type="toastType"
      @close="showToast = false"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { adminApi } from '@/api'
import MessageToast from '@/components/MessageToast.vue'

const searchLoading = ref(false)
const ragLoading = ref(false)
const searchResult = ref('')
const ragResult = ref('')
const showToast = ref(false)
const toastMessage = ref('')
const toastType = ref('success')

const notify = (message, type = 'success') => {
  toastMessage.value = message
  toastType.value = type
  showToast.value = true
}

const syncSearchIndex = async () => {
  searchLoading.value = true
  try {
    const response = await adminApi.syncSearchIndex()
    if (!response?.success) {
      notify(response?.message || '全文索引同步失败', 'error')
      return
    }
    const count = response.data?.syncedCount ?? 0
    searchResult.value = `本次同步 ${count} 篇笔记`
    notify(response.message || '全文索引同步完成')
  } finally {
    searchLoading.value = false
  }
}

const syncRagIndex = async () => {
  ragLoading.value = true
  try {
    const response = await adminApi.syncRagIndex()
    if (!response?.success) {
      notify(response?.message || 'RAG 向量同步失败', 'error')
      return
    }
    const count = response.data?.indexedChunkCount ?? 0
    ragResult.value = `本次生成 ${count} 个 RAG chunks`
    notify(response.message || 'RAG 向量增量同步完成')
  } finally {
    ragLoading.value = false
  }
}
</script>

<style scoped>
.index-management {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
  padding: 12px;
}

.index-card {
  padding: 24px;
  background: var(--bg-color-primary);
  border: 1px solid var(--border-color-primary);
  border-radius: 12px;
}

.index-card h2 {
  margin: 0 0 10px;
  color: var(--text-color-primary);
  font-size: 20px;
}

.index-card p {
  margin: 0 0 20px;
  color: var(--text-color-secondary);
  line-height: 1.6;
}

.index-card button {
  padding: 10px 18px;
  border: 0;
  border-radius: 8px;
  color: #fff;
  background: var(--primary-color, #ff2442);
  cursor: pointer;
}

.index-card button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.index-card .result {
  margin: 16px 0 0;
  color: var(--text-color-primary);
  font-weight: 600;
}
</style>
