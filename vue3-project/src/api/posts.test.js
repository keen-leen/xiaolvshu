import { describe, expect, it } from 'vitest'
import { transformImageAttribution, transformPostData } from './posts.js'

describe('posts API boundary normalization', () => {
  it('normalizes nested image attribution without changing array order', () => {
    const post = transformPostData({
      id: 1,
      title: '测试',
      content: '正文',
      images: ['first.jpg', 'second.jpg'],
      user_avatar: 'avatar.jpg',
      image_attributions: [
        { source_url: 'source-1', photographer_url: 'author-1' },
        { source_url: 'source-2', photographer_url: 'author-2' }
      ]
    })

    expect(post.imageAttributions.map(item => item.sourceUrl))
      .toEqual(['source-1', 'source-2'])
    expect(post.originalData.imageAttributions[1].photographerUrl).toBe('author-2')
  })

  it('handles a missing attribution object safely', () => {
    expect(transformImageAttribution(null)).toEqual({
      imageUrl: undefined,
      provider: undefined,
      providerAssetId: undefined,
      photographer: undefined,
      photographerUrl: undefined,
      sourceUrl: undefined,
      licenseName: undefined,
      licenseUrl: undefined,
      altText: undefined
    })
  })
})
