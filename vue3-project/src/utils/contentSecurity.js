import DOMPurify from 'dompurify'

const SAFE_USER_ID_PATTERN = /^[A-Za-z0-9_]{1,50}$/

/**
 * 进一步约束 DOMPurify 放行的 a 标签：只有结构完整的站内 mention 才保留为链接。
 * mention 的跳转地址由点击处理器根据 data-user-id 重新构造，因此持久化内容无需保留 href。
 */
const normalizeMentionLinks = container => {
  container.querySelectorAll('a').forEach(link => {
    const userId = link.getAttribute('data-user-id') || ''
    if (!link.classList.contains('mention-link') || !SAFE_USER_ID_PATTERN.test(userId)) {
      link.replaceWith(document.createTextNode(link.textContent || ''))
      return
    }

    const text = link.textContent || ''
    Array.from(link.attributes).forEach(attribute => link.removeAttribute(attribute.name))
    link.className = 'mention-link'
    link.setAttribute('data-user-id', userId)
    link.textContent = text
  })
}

/**
 * 编辑器会产生 div/p 换行。持久化前统一转换成 br，避免不同入口保存出不同 HTML 结构。
 */
const flattenTextBlocks = container => {
  container.querySelectorAll('div, p').forEach(block => {
    const fragment = document.createDocumentFragment()
    while (block.firstChild) {
      fragment.appendChild(block.firstChild)
    }
    fragment.appendChild(document.createElement('br'))
    block.replaceWith(fragment)
  })
}

const sanitizeMarkup = (content, allowImages) => {
  if (!content) return ''

  const sanitized = DOMPurify.sanitize(String(content), {
    ALLOWED_TAGS: allowImages ? ['a', 'br', 'div', 'p', 'img'] : ['a', 'br', 'div', 'p'],
    ALLOWED_ATTR: allowImages
      ? ['class', 'data-user-id', 'src', 'alt']
      : ['class', 'data-user-id']
  })
  const container = document.createElement('div')
  container.innerHTML = sanitized
  normalizeMentionLinks(container)
  flattenTextBlocks(container)

  if (allowImages) {
    container.querySelectorAll('img').forEach(image => {
      const source = image.getAttribute('src') || ''
      try {
        const url = new URL(source, window.location.origin)
        if (!['http:', 'https:', 'blob:'].includes(url.protocol)) {
          image.remove()
        }
      } catch {
        image.remove()
      }
    })
  }

  return container.innerHTML
    .replace(/(?:<br>\s*){2,}/gi, '<br>')
    .replace(/(?:<br>)+$/i, '')
    .trim()
}

/**
 * 用户可编辑内容的持久化净化：仅保留换行和合法的站内 mention。
 */
export const sanitizeContent = content => sanitizeMarkup(content, false)

/**
 * 帖子展示净化：在 sanitizeContent 的规则上额外允许安全图片。
 * ContentRenderer 必须先调用本函数，再进行图片提取和 v-html 渲染。
 */
export const sanitizeDisplayContent = content => sanitizeMarkup(content, true)

/**
 * 后台详情可能展示富文本，但仍必须移除脚本、事件属性和危险 URL。
 */
export const sanitizeRichHtml = content => {
  if (!content) return ''
  return DOMPurify.sanitize(String(content), {
    USE_PROFILES: { html: true }
  })
}

/**
 * 验证码由后端返回 SVG 文本。SVG profile 保留绘图元素，同时明确禁止可执行或嵌套 HTML。
 */
export const sanitizeSvg = content => {
  if (!content) return ''
  return DOMPurify.sanitize(String(content), {
    USE_PROFILES: { svg: true, svgFilters: true },
    FORBID_TAGS: ['script', 'foreignObject'],
    FORBID_ATTR: ['style']
  })
}

/**
 * 纯文本场景移除全部 HTML。
 */
export const sanitizeText = content => {
  if (!content) return ''
  const container = document.createElement('div')
  container.innerHTML = DOMPurify.sanitize(String(content), { ALLOWED_TAGS: [] })
  return container.textContent || ''
}

export const hasDangerousTags = content => {
  if (!content) return false
  return /<\/?(?:script|iframe|object|embed|form|input|button|link|meta|style|base|applet|frame|frameset)\b/i
    .test(content)
}

export const hasDangerousAttributes = content => {
  if (!content) return false
  return /\s(?:on[a-z]+|srcdoc)\s*=|javascript\s*:/i.test(content)
}

export const securityCheck = content => {
  if (!content) {
    return {
      isSafe: true,
      sanitizedContent: '',
      warnings: []
    }
  }

  const warnings = []
  if (hasDangerousTags(content)) warnings.push('检测到危险HTML标签')
  if (hasDangerousAttributes(content)) warnings.push('检测到危险HTML属性')
  return {
    isSafe: warnings.length === 0,
    sanitizedContent: sanitizeContent(content),
    warnings
  }
}
