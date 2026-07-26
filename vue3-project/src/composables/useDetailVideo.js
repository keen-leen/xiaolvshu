import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'

/**
 * 笔记详情视频生命周期。
 *
 * 该逻辑同时服务桌面和移动端播放器，负责尺寸计算、自动播放、进度/音量恢复以及事件
 * 解绑。把它从 DetailCard 抽离后，组件只保留业务编排，媒体监听器也有单一清理入口。
 */
export function useDetailVideo({ imageSectionWidth, getVideoUrl, isPageMode }) {
  const videoPlayer = ref(null)
  const mobileVideoPlayer = ref(null)
  const isVideoLoaded = ref(false)
  const mediaHandlers = new WeakMap()

  const storageKeys = url => ({
    timeKey: `video_progress_${url ? encodeURIComponent(url) : 'unknown'}`,
    volumeKey: 'video_volume_global'
  })

  const autoPlayVideo = () => {
    const player = window.innerWidth <= 768 ? mobileVideoPlayer.value : videoPlayer.value
    if (!player) return

    try {
      const playPromise = player.play()
      playPromise?.catch(error => {
        // 浏览器可能因缺少用户交互拒绝自动播放，此时保留原生播放控件供用户继续操作。
        console.info('视频自动播放被浏览器阻止:', error.message)
      })
    } catch (error) {
      console.info('视频自动播放失败:', error.message)
    }
  }

  const handleVideoLoad = event => {
    const video = event.target
    const aspectRatio = video.videoWidth / video.videoHeight

    if (window.innerWidth > 768 && Number.isFinite(aspectRatio)) {
      const minWidth = 300
      const maxWidth = isPageMode() ? 500 : 750
      const idealWidth = Math.min(window.innerHeight * 0.9, 1020) * aspectRatio
      let optimalWidth = Math.max(minWidth, Math.min(maxWidth, idealWidth))

      if (aspectRatio <= 0.6) optimalWidth = Math.min(optimalWidth, 500)
      else if (aspectRatio <= 0.8) optimalWidth = Math.min(optimalWidth, 600)
      else if (aspectRatio >= 2) optimalWidth = Math.max(optimalWidth, 600)
      else if (aspectRatio >= 1.5) optimalWidth = Math.max(optimalWidth, 550)

      imageSectionWidth.value = optimalWidth
    }

    isVideoLoaded.value = true
    window.setTimeout(autoPlayVideo, 100)
  }

  const restoreState = (player, url) => {
    if (!player) return
    const { timeKey, volumeKey } = storageKeys(url)

    try {
      const savedVolume = localStorage.getItem(volumeKey)
      const parsedVolume = savedVolume === null ? 0.5 : Number(savedVolume)
      player.volume = Math.max(0, Math.min(1, Number.isFinite(parsedVolume) ? parsedVolume : 0.5))

      const savedTime = Number(localStorage.getItem(timeKey))
      if (!Number.isFinite(savedTime)) return
      if (player.readyState >= 1) {
        player.currentTime = savedTime
        return
      }

      const seekAfterMetadata = () => {
        player.currentTime = savedTime
        player.removeEventListener('loadedmetadata', seekAfterMetadata)
      }
      player.addEventListener('loadedmetadata', seekAfterMetadata)
    } catch {
      // 隐私模式或浏览器策略可能禁用 localStorage；不影响视频本身播放。
    }
  }

  const bindPersistence = (player, url) => {
    if (!player) return
    const { timeKey, volumeKey } = storageKeys(url)
    const persist = (key, value) => {
      try {
        localStorage.setItem(key, value)
      } catch {
        // 存储不可用时停止记忆状态，但不能让媒体事件冒泡为运行时错误。
      }
    }
    const handlers = {
      timeupdate: () => persist(timeKey, String(player.currentTime || 0)),
      volumechange: () => persist(volumeKey, String(player.volume))
    }

    player.addEventListener('timeupdate', handlers.timeupdate)
    player.addEventListener('volumechange', handlers.volumechange)
    mediaHandlers.set(player, handlers)
  }

  const unbindPersistence = player => {
    const handlers = player && mediaHandlers.get(player)
    if (!handlers) return
    player.removeEventListener('timeupdate', handlers.timeupdate)
    player.removeEventListener('volumechange', handlers.volumechange)
    mediaHandlers.delete(player)
  }

  const teardown = () => {
    unbindPersistence(videoPlayer.value)
    unbindPersistence(mobileVideoPlayer.value)
  }

  const setup = () => {
    const url = getVideoUrl()
    for (const player of [videoPlayer.value, mobileVideoPlayer.value]) {
      restoreState(player, url)
      try {
        bindPersistence(player, url)
      } catch {
        // 存储不可用时仍保留播放器，只是不记忆进度和音量。
      }
    }
  }

  watch(isVideoLoaded, loaded => {
    if (!loaded) return
    teardown()
    setup()
  })

  watch(getVideoUrl, async () => {
    teardown()
    isVideoLoaded.value = false
    await nextTick()
    setup()
  })

  onMounted(() => {
    // 新用户默认音量由 restoreState 统一设为 0.5。
    setup()
  })
  onUnmounted(teardown)

  return {
    autoPlayVideo,
    handleVideoLoad,
    isVideoLoaded,
    mobileVideoPlayer,
    videoPlayer
  }
}
