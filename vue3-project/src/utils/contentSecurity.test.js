// @vitest-environment jsdom

import { describe, expect, it } from 'vitest'
import {
  sanitizeContent,
  sanitizeDisplayContent,
  sanitizeRichHtml,
  sanitizeSvg
} from './contentSecurity.js'

describe('content security boundaries', () => {
  it('removes executable markup and event attributes', () => {
    const result = sanitizeContent(
      '<div onclick="alert(1)">正文<script>alert(2)</script><br>下一行</div>'
    )

    expect(result).toContain('正文')
    expect(result).toContain('下一行')
    expect(result).not.toMatch(/script|onclick|alert/i)
  })

  it('keeps only structurally valid mention links without persisted href', () => {
    const valid = sanitizeContent(
      '<a href="javascript:alert(1)" class="mention-link extra" '
      + 'data-user-id="traveller_1" onclick="alert(2)">@旅行者</a>'
    )
    const invalid = sanitizeContent(
      '<a class="mention-link" data-user-id="../admin">@非法用户</a>'
    )

    expect(valid).toBe(
      '<a class="mention-link" data-user-id="traveller_1">@旅行者</a>'
    )
    expect(valid).not.toContain('href')
    expect(invalid).toBe('@非法用户')
  })

  it('keeps safe post images and removes dangerous sources', () => {
    const result = sanitizeDisplayContent(
      '<img src="https://example.com/photo.jpg" alt="照片">'
      + '<img src="javascript:alert(1)">'
    )

    expect(result).toContain('https://example.com/photo.jpg')
    expect(result).not.toContain('javascript:')
  })

  it('sanitizes rich HTML and captcha SVG before v-html rendering', () => {
    const rich = sanitizeRichHtml('<p onmouseover="alert(1)">详情</p><script>bad()</script>')
    const svg = sanitizeSvg(
      '<svg onload="alert(1)"><text>1234</text><foreignObject>bad</foreignObject></svg>'
    )

    expect(rich).toContain('<p>详情</p>')
    expect(rich).not.toMatch(/onmouseover|script/i)
    expect(svg).toContain('<text>1234</text>')
    expect(svg).not.toMatch(/onload|foreignObject/i)
  })
})
