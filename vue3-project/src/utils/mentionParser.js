/**
 * Mention文本解析工具
 * 将[@nickname:user_id]格式的文本转换为可点击的超链接
 */

/**
 * HTML转义函数，防止XSS攻击
 * @param {string} text - 需要转义的文本
 * @returns {string} - 转义后的文本
 */
function escapeHtml(text) {
  if (!text) return ''
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

/**
 * 解析文本中的mention标记，转换为HTML超链接
 * @param {string} text - 包含mention标记的文本
 * @returns {string} - 转换后的HTML字符串
 */
export function parseMentions(text) {
  if (!text) return ''

  // 先提取并保护已存在的mention链接
  const mentionLinkRegex = /<a[^>]*class="[^"]*mention-link[^"]*"[^>]*data-user-id="[^"]*"[^>]*>@[^<]*<\/a>/g
  const mentionLinks = []
  let protectedText = text.replace(mentionLinkRegex, (match) => {
    const placeholder = `__MENTION_LINK_${mentionLinks.length}__`
    mentionLinks.push(match)
    return placeholder
  })

  // 提取并保护data-at-marker的span标签
  const atMarkerRegex = /<span[^>]*data-at-marker[^>]*>@<\/span>/g
  const atMarkers = []
  protectedText = protectedText.replace(atMarkerRegex, (match) => {
    const placeholder = `__AT_MARKER_${atMarkers.length}__`
    atMarkers.push(match)
    return placeholder
  })

  // 先处理换行符，将其转换为占位符保护
  const lineBreaks = []
  protectedText = protectedText.replace(/\n/g, () => {
    const placeholder = `__LINE_BREAK_${lineBreaks.length}__`
    lineBreaks.push('<div></div>')
    return placeholder
  })

  // 对剩余文本进行HTML转义，防止用户输入的HTML标签被渲染
  const escapedText = escapeHtml(protectedText)

  // 匹配[@nickname:user_id]格式的正则表达式
  const mentionRegex = /\[@([^:]+):([^\]]+)\]/g

  let result = escapedText.replace(mentionRegex, (match, nickname, userId) => {
    // 生成用户主页链接，使用小石榴号作为路由参数
    return `<a href="/user/${userId}" class="mention-link" data-user-id="${userId}" contenteditable="false">@${nickname}</a>`
  })

  // 恢复换行符为div标签
  lineBreaks.forEach((lineBreak, index) => {
    result = result.replace(`__LINE_BREAK_${index}__`, lineBreak)
  })

  // 恢复data-at-marker的span标签
  atMarkers.forEach((marker, index) => {
    result = result.replace(`__AT_MARKER_${index}__`, marker)
  })

  // 恢复已存在的mention链接
  mentionLinks.forEach((link, index) => {
    result = result.replace(`__MENTION_LINK_${index}__`, link)
  })

  // 如果结果包含换行div，需要将第一行内容包装在div中以保持一致性
  if (result.includes('<div></div>')) {
    const lines = result.split('<div></div>')
    if (lines.length > 1 && lines[0]) {
      result = lines.join('<div></div>')
    }
  }

  return result
}

/**
 * 从文本中提取所有被@的用户ID
 * @param {string} text - 包含mention标记的文本
 * @returns {Array} - 用户ID数组
 */
export function extractMentionedUsers(text) {
  if (!text) return []

  const mentionRegex = /\[@([^:]+):([^\]]+)\]/g
  const mentionedUsers = []
  let match

  while ((match = mentionRegex.exec(text)) !== null) {
    const [, nickname, userId] = match
    mentionedUsers.push({
      nickname,
      userId
    })
  }

  return mentionedUsers
}

/**
 * 检查文本是否包含mention标记
 * @param {string} text - 要检查的文本
 * @returns {boolean} - 是否包含mention
 */
export function hasMentions(text) {
  if (!text) return false
  // 检查[@nickname:user_id]格式
  const mentionRegex = /\[@([^:]+):([^\]]+)\]/
  // 检查HTML格式的mention链接（匹配mention-link或mention class）
  const htmlMentionRegex = /<a[^>]*class="mention[^"]*"[^>]*data-user-id[^>]*>[^<]*@[^<]*<\/a>/
  return mentionRegex.test(text) || htmlMentionRegex.test(text)
}

/**
 * 清理文本中的mention标记，只保留昵称
 * @param {string} text - 包含mention标记的文本
 * @returns {string} - 清理后的文本
 */
export function cleanMentions(text) {
  if (!text) return ''

  // 清理[@nickname:user_id]格式
  const mentionRegex = /\[@([^:]+):([^\]]+)\]/g
  let cleanedText = text.replace(mentionRegex, '@$1')

  // 清理HTML格式的mention链接，提取@昵称部分（匹配mention-link或mention class）
  const htmlMentionRegex = /<a[^>]*class="mention[^"]*"[^>]*data-user-id[^>]*>([^<]*@[^<]*)<\/a>/g
  cleanedText = cleanedText.replace(htmlMentionRegex, '$1')

  // 移除所有HTML标签，只保留文本内容
  cleanedText = cleanedText.replace(/<[^>]*>/g, '')

  // 移除&nbsp;等HTML实体
  cleanedText = cleanedText.replace(/&nbsp;/g, ' ')

  return cleanedText
}