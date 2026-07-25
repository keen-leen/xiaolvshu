import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

const markdown = new MarkdownIt({
  breaks: true,
  linkify: true,
  html: false
})

const defaultLinkOpen = markdown.renderer.rules.link_open ||
  ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options))

markdown.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  tokens[idx].attrSet('target', '_blank')
  tokens[idx].attrSet('rel', 'noopener noreferrer nofollow')
  return defaultLinkOpen(tokens, idx, options, env, self)
}

/**
 * 两个旅行助手入口共享同一套 Markdown 兼容和净化规则，避免浮窗与完整页渲染结果不一致。
 */
export const renderTravelAiMarkdown = content => {
  if (!content) {
    return ''
  }
  let text = String(content)
    .replace(/\r\n/g, '\n')
    .replace(/\\n/g, '\n')
    .replace(/\u00a0/g, ' ')
  text = text.replace(/(^|\n)[ \t]{0,3}＃{1,6}/g, match => match.replace(/＃/g, '#'))
  text = text.replace(/(^|\n)([ \t]{0,3}#{1,6})([^\s#])/g, '$1$2 $3')
  return DOMPurify.sanitize(markdown.render(text), {
    USE_PROFILES: { html: true },
    ALLOWED_ATTR: ['href', 'target', 'rel', 'title']
  })
}
