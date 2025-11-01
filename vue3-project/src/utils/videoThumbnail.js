/**
 * 视频缩略图生成工具
 * 使用Canvas API从视频文件中提取第一帧作为缩略图
 */

/**
 * 生成视频缩略图
 * @param {File} videoFile - 视频文件
 * @param {Object} options - 配置选项
 * @param {number} options.width - 缩略图宽度，默认640
 * @param {number} options.height - 缩略图高度，默认360
 * @param {boolean} options.useOriginalSize - 是否使用视频原始尺寸，默认false
 * @param {number} options.quality - 图片质量，默认0.8
 * @param {string} options.format - 图片格式，默认'image/jpeg'
 * @param {number} options.seekTime - 截取时间点（秒），默认1秒
 * @returns {Promise<{success: boolean, blob?: Blob, dataUrl?: string, error?: string}>}
 */
export async function generateVideoThumbnail(videoFile, options = {}) {
  const {
    width = 640,
    height = 360,
    useOriginalSize = false,
    quality = 0.8,
    format = 'image/jpeg',
    seekTime = 1
  } = options

  return new Promise((resolve) => {
    try {
      // 创建video元素
      const video = document.createElement('video')
      video.crossOrigin = 'anonymous'
      video.muted = true
      video.playsInline = true
      
      // 创建canvas元素
      const canvas = document.createElement('canvas')
      const ctx = canvas.getContext('2d')
      
      // 设置canvas尺寸
      canvas.width = width
      canvas.height = height

      // 视频加载完成后的处理
      video.addEventListener('loadedmetadata', () => {
        // 如果使用原始尺寸，则设置canvas为视频的原始尺寸
        if (useOriginalSize) {
          canvas.width = video.videoWidth
          canvas.height = video.videoHeight
        } else {
          // 使用指定尺寸
          canvas.width = width
          canvas.height = height
        }

        // 设置视频时间点
        const targetTime = Math.min(seekTime, video.duration - 0.1)
        video.currentTime = targetTime
      })

      // 视频可以播放时截取帧
      video.addEventListener('seeked', () => {
        try {
          if (useOriginalSize) {
            // 使用原始尺寸，直接绘制视频帧
            ctx.drawImage(video, 0, 0, video.videoWidth, video.videoHeight)
          } else {
            // 使用指定尺寸，需要计算比例和位置
            ctx.fillStyle = '#000000'
            ctx.fillRect(0, 0, canvas.width, canvas.height)
            
            const videoAspectRatio = video.videoWidth / video.videoHeight
            const canvasAspectRatio = canvas.width / canvas.height
            
            let drawWidth = canvas.width
            let drawHeight = canvas.height
            let offsetX = 0
            let offsetY = 0
            
            if (videoAspectRatio > canvasAspectRatio) {
              drawHeight = canvas.height
              drawWidth = canvas.height * videoAspectRatio
              offsetX = (canvas.width - drawWidth) / 2
            } else {
              drawWidth = canvas.width
              drawHeight = canvas.width / videoAspectRatio
              offsetY = (canvas.height - drawHeight) / 2
            }
            
            ctx.drawImage(video, offsetX, offsetY, drawWidth, drawHeight)
          }
          
          // 转换为Blob
          canvas.toBlob((blob) => {
            if (blob) {
              // 生成DataURL用于预览
              const dataUrl = canvas.toDataURL(format, quality)
              
              resolve({
                success: true,
                blob: blob,
                dataUrl: dataUrl
              })
            } else {
              resolve({
                success: false,
                error: '无法生成缩略图Blob'
              })
            }
            
            // 清理资源
            URL.revokeObjectURL(video.src)
          }, format, quality)
          
        } catch (error) {
          console.error('绘制视频帧失败:', error)
          resolve({
            success: false,
            error: '绘制视频帧失败: ' + error.message
          })
          URL.revokeObjectURL(video.src)
        }
      })

      // 错误处理
      video.addEventListener('error', (e) => {
        console.error('视频加载失败:', e)
        resolve({
          success: false,
          error: '视频加载失败'
        })
        URL.revokeObjectURL(video.src)
      })

      // 加载超时处理
      const timeout = setTimeout(() => {
        resolve({
          success: false,
          error: '视频加载超时'
        })
        URL.revokeObjectURL(video.src)
      }, 10000) // 10秒超时

      // 清理超时定时器
      video.addEventListener('loadedmetadata', () => {
        clearTimeout(timeout)
      })

      // 开始加载视频
      video.src = URL.createObjectURL(videoFile)
      video.load()
      
    } catch (error) {
      console.error('生成视频缩略图失败:', error)
      resolve({
        success: false,
        error: '生成视频缩略图失败: ' + error.message
      })
    }
  })
}

/**
 * 将Blob转换为File对象
 * @param {Blob} blob - Blob对象
 * @param {string} filename - 文件名
 * @returns {File} File对象
 */
export function blobToFile(blob, filename) {
  return new File([blob], filename, { type: blob.type })
}

/**
 * 生成缩略图文件名
 * @param {string} videoFilename - 视频文件名
 * @returns {string} 缩略图文件名
 */
export function generateThumbnailFilename(videoFilename) {
  const nameWithoutExt = videoFilename.replace(/\.[^/.]+$/, '')
  return `${nameWithoutExt}_thumbnail.jpg`
}