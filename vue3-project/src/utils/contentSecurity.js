/**
 * 内容安全过滤工具函数
 * 统一管理危险标签过滤，防止XSS攻击
 */

/**
 * 内容安全过滤函数
 * 保留mention链接和<br>标签，移除其他危险标签
 * @param {string} content - 需要过滤的内容
 * @returns {string} - 过滤后的安全内容
 */
export const sanitizeContent = (content) => {
  if (!content) return ''

  // 保留mention链接和<br>标签，但移除其他危险标签
  // 先保存mention链接
  const mentionLinks = []
  let processedContent = content.replace(/<a[^>]*class="mention-link"[^>]*>.*?<\/a>/g, (match) => {
    const placeholder = `__MENTION_${mentionLinks.length}__`
    mentionLinks.push(match)
    return placeholder
  })

  // 将其他换行元素转换为<br>标签
  processedContent = processedContent.replace(/<\/div><div[^>]*>/gi, '<br>')
  processedContent = processedContent.replace(/<\/p><p[^>]*>/gi, '<br>')
  processedContent = processedContent.replace(/<div[^>]*>/gi, '')
  processedContent = processedContent.replace(/<\/div>/gi, '')
  processedContent = processedContent.replace(/<p[^>]*>/gi, '')
  processedContent = processedContent.replace(/<\/p>/gi, '')

  // 移除其他HTML标签，但保留<br>标签
  processedContent = processedContent.replace(/<(?!br\s*\/?)[^>]*>/gi, '').replace(/&nbsp;/g, ' ')

  // 恢复mention链接
  mentionLinks.forEach((link, index) => {
    processedContent = processedContent.replace(`__MENTION_${index}__`, link)
  })

  // 清理多余的<br>标签
  processedContent = processedContent.replace(/(<br\s*\/?\s*){2,}/gi, '<br>')

  return processedContent.trim()
}

/**
 * 简单的文本内容过滤函数
 * 移除所有HTML标签，只保留纯文本
 * @param {string} content - 需要过滤的内容
 * @returns {string} - 过滤后的纯文本内容
 */
export const sanitizeText = (content) => {
  if (!content) return ''
  return content.replace(/<[^>]*>/g, '')
}

/**
 * 验证内容是否包含危险标签
 * @param {string} content - 需要验证的内容
 * @returns {boolean} - 是否包含危险标签
 */
export const hasDangerousTags = (content) => {
  if (!content) return false

  // 危险标签列表
  const dangerousTags = [
    'script', 'iframe', 'object', 'embed', 'form', 'input', 'button',
    'link', 'meta', 'style', 'base', 'applet', 'frame', 'frameset'
  ]

  const tagRegex = new RegExp(`<\/?(?:${dangerousTags.join('|')})[^>]*>`, 'gi')
  return tagRegex.test(content)
}

/**
 * 验证内容是否包含危险属性
 * @param {string} content - 需要验证的内容
 * @returns {boolean} - 是否包含危险属性
 */
export const hasDangerousAttributes = (content) => {
  if (!content) return false

  // 危险属性列表
  const dangerousAttrs = [
    'onclick', 'onload', 'onerror', 'onmouseover', 'onmouseout',
    'onfocus', 'onblur', 'onchange', 'onsubmit', 'javascript:'
  ]

  return dangerousAttrs.some(attr =>
    content.toLowerCase().includes(attr.toLowerCase())
  )
}

/**
 * 完整的内容安全检查和过滤
 * @param {string} content - 需要处理的内容
 * @returns {object} - 包含是否安全和过滤后内容的对象
 */
export const securityCheck = (content) => {
  if (!content) {
    return {
      isSafe: true,
      sanitizedContent: '',
      warnings: []
    }
  }

  const warnings = []

  // 检查危险标签
  if (hasDangerousTags(content)) {
    warnings.push('检测到危险HTML标签')
  }

  // 检查危险属性
  if (hasDangerousAttributes(content)) {
    warnings.push('检测到危险HTML属性')
  }

  // 过滤内容
  const sanitizedContent = sanitizeContent(content)

  return {
    isSafe: warnings.length === 0,
    sanitizedContent,
    warnings
  }
}