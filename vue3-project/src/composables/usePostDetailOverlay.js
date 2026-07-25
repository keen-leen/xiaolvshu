import { onMounted, onUnmounted, ref } from 'vue'
import { getPostDetail } from '@/api/posts.js'

let overlaySequence = 0

/**
 * 复用站内笔记详情的覆盖层行为。
 *
 * 详情卡片只是展示组件，当前页面的 URL、标题和浏览器后退行为需要由入口管理。
 * 将这些状态集中在这里，可以避免旅行 Agent 的完整页与悬浮窗出现两套不同交互。
 */
export const usePostDetailOverlay = ({ beforeShow, afterClose } = {}) => {
  const selectedPost = ref(null)
  const showDetailCard = ref(false)
  const clickPosition = ref({ x: 0, y: 0 })
  const openingPostId = ref(null)

  const overlayId = `post-detail-overlay-${++overlaySequence}`
  let previousUrl = ''
  let previousHistoryState = null
  let originalTitle = ''
  let disposed = false

  const resetOverlay = ({ restoreLocation = true, notifyClose = true } = {}) => {
    if (!showDetailCard.value && !selectedPost.value) {
      return
    }

    showDetailCard.value = false
    selectedPost.value = null

    if (originalTitle) {
      document.title = originalTitle
    }

    if (restoreLocation && previousUrl) {
      window.history.replaceState(previousHistoryState, '', previousUrl)
    }

    previousUrl = ''
    previousHistoryState = null
    originalTitle = ''

    if (notifyClose) {
      afterClose?.()
    }
  }

  const openPostDetail = async (postId, event) => {
    const normalizedPostId = String(postId || '').trim()
    if (!normalizedPostId || openingPostId.value !== null) {
      return false
    }

    openingPostId.value = normalizedPostId

    try {
      const postDetail = await getPostDetail(normalizedPostId)
      if (!postDetail) {
        throw new Error(`笔记 ${normalizedPostId} 不存在`)
      }
      if (disposed) {
        return false
      }

      previousUrl = window.location.pathname + window.location.search + window.location.hash
      previousHistoryState = window.history.state
      originalTitle = document.title
      clickPosition.value = {
        x: event?.clientX ?? window.innerWidth / 2,
        y: event?.clientY ?? window.innerHeight / 2
      }

      beforeShow?.()
      selectedPost.value = postDetail
      showDetailCard.value = true
      document.title = postDetail.title || '笔记详情'

      window.history.pushState(
        {
          previousUrl,
          showDetailCard: true,
          postId: normalizedPostId,
          originalTitle,
          detailOverlayId: overlayId
        },
        postDetail.title || '笔记详情',
        `/post?id=${encodeURIComponent(normalizedPostId)}`
      )
      return true
    } catch (error) {
      resetOverlay({ restoreLocation: false })
      console.error('打开引用笔记失败:', error)
      return false
    } finally {
      openingPostId.value = null
    }
  }

  const closePostDetail = () => {
    resetOverlay()
  }

  const handlePopState = event => {
    if (!showDetailCard.value) {
      return
    }

    if (event.state?.showDetailCard && event.state?.detailOverlayId === overlayId) {
      return
    }

    // 浏览器已经完成后退，此处只收起覆盖层，不能再次改写历史记录。
    resetOverlay({ restoreLocation: false })
  }

  onMounted(() => {
    disposed = false
    window.addEventListener('popstate', handlePopState)
  })
  onUnmounted(() => {
    disposed = true
    window.removeEventListener('popstate', handlePopState)
    resetOverlay({ restoreLocation: false, notifyClose: false })
  })

  return {
    selectedPost,
    showDetailCard,
    clickPosition,
    openingPostId,
    openPostDetail,
    closePostDetail
  }
}
