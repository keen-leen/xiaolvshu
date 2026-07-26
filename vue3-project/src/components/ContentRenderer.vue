<template>
  <div class="content-renderer">
    <!-- 文字内容 -->
    <div v-if="renderedText" class="content-text">
      <!-- 内容已由 sanitizeDisplayContent 按 mention 白名单净化。 -->
      <!-- eslint-disable-next-line vue/no-v-html -->
      <span class="mention-text" v-html="renderedText" @click="handleMentionClick"></span>
    </div>

    <!-- 图片内容 -->
    <div v-if="images && images.length > 0" class="content-images">
      <div class="images-grid" :class="getGridClass()">
        <div v-for="(image, index) in images" :key="index" class="image-item"
          @click="$emit('image-click', { images: images, index })">
          <img :src="image" :alt="`图片${index + 1}`" class="content-image" @error="handleImageError" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { sanitizeDisplayContent } from '@/utils/contentSecurity'

const props = defineProps({
  content: {
    type: String,
    default: ''
  },
  // 兼容旧的text属性
  text: {
    type: String,
    default: ''
  }
})

defineEmits(['image-click'])

// 获取实际内容，优先使用content，其次使用text
const actualContent = computed(() => {
  return props.content || props.text || ''
})

// 解析content内容，提取文字和图片
const parsedContent = computed(() => {
  if (!actualContent.value) return { text: '', images: [] }

  // 先按展示白名单净化，再创建 DOM 提取图片，禁止原始服务端内容直接进入 v-html。
  const tempDiv = document.createElement('div')
  tempDiv.innerHTML = sanitizeDisplayContent(actualContent.value)

  // 提取图片
  const imgElements = tempDiv.querySelectorAll('img')
  const images = Array.from(imgElements).map(img => img.src)

  // 移除图片元素，剩余内容只包含安全换行与合法 mention。
  imgElements.forEach(img => img.remove())
  return { text: tempDiv.innerHTML.trim(), images }
})

const renderedText = computed(() => parsedContent.value.text)
const images = computed(() => parsedContent.value.images)

// 处理mention链接点击事件
const handleMentionClick = (event) => {
  const target = event.target

  // 检查点击的是否是mention链接
  if (target.classList.contains('mention-link')) {
    event.preventDefault()
    const userId = target.getAttribute('data-user-id')

    if (userId) {
      // 在新标签页中打开用户主页
      const userUrl = `${window.location.origin}/user/${userId}`
      window.open(userUrl, '_blank')
    }
  }
}

// 根据图片数量决定网格布局
const getGridClass = () => {
  const count = images.value.length
  if (count === 1) return 'single'
  if (count === 2) return 'double'
  if (count === 3) return 'triple'
  if (count === 4) return 'quad'
  return 'multiple'
}

// 图片加载失败处理
const handleImageError = (event) => {
  import('@/assets/imgs/未加载.png').then(module => {
    event.target.src = module.default
  })
}
</script>

<style scoped>
.content-renderer {
  width: 100%;
}

.content-text {
  margin-bottom: 8px;
  line-height: 1.5;
  word-wrap: break-word;
}

.mention-text {
  white-space: pre-wrap;
  word-wrap: break-word;
}

:deep(.mention-link) {
  color: var(--text-color-tag);
  text-decoration: none;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.2s ease;
  background: none;
  border: none;
  padding: 0;
}

:deep(.mention-link:hover) {
  color: var(--text-color-tag);
  opacity: 0.8;
}

:deep(.mention-link:active) {
  color: var(--text-color-tag);
  opacity: 0.6;
}

:deep(.mention-link:focus) {
  outline: none;
  box-shadow: none;
  border: none;
}

.content-images {
  margin-top: 8px;
}

.images-grid {
  display: grid;
  gap: 4px;
  border-radius: 8px;
  overflow: hidden;
}

.images-grid.single {
  grid-template-columns: 1fr;
  max-width: 200px;
}

.images-grid.double {
  grid-template-columns: 1fr 1fr;
  max-width: 200px;
}

.images-grid.triple {
  grid-template-columns: 1fr 1fr 1fr;
  max-width: 240px;
}

.images-grid.quad {
  grid-template-columns: 1fr 1fr;
  max-width: 200px;
}

.images-grid.multiple {
  grid-template-columns: repeat(3, 1fr);
  max-width: 240px;
}

.image-item {
  position: relative;
  aspect-ratio: 1;
  cursor: pointer;
  border-radius: 4px;
  overflow: hidden;
  transition: transform 0.2s ease;
}

.image-item:hover {
  transform: scale(1.02);
}

.content-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .images-grid.single {
    max-width: 150px;
  }

  .images-grid.double {
    max-width: 150px;
  }

  .images-grid.triple {
    max-width: 180px;
  }

  .images-grid.quad {
    max-width: 150px;
  }

  .images-grid.multiple {
    max-width: 180px;
  }
}
</style>
