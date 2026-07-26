import { readFileSync, readdirSync } from 'node:fs'
import path from 'node:path'

const VIRTUAL_MODULE_ID = 'virtual:svg-icons-register'
const RESOLVED_MODULE_ID = `\0${VIRTUAL_MODULE_ID}`

/**
 * 将图标目录编译为单个 SVG sprite。
 *
 * 设计原因：原 vite-plugin-svg-icons 依赖未声明的 fast-glob，并引入了停止维护的
 * svg-baker 依赖链。项目只需要“文件名 -> icon-{name}”这一项能力，本地实现可以保持
 * 现有 SvgIcon 调用协议不变，同时让构建依赖更少、更可控。
 */
function createSprite(iconDir, symbolPrefix) {
  return readdirSync(iconDir)
    .filter(fileName => fileName.endsWith('.svg'))
    .sort()
    .map(fileName => {
      const source = readFileSync(path.join(iconDir, fileName), 'utf8')
      const svgMatch = source.match(/<svg\b([^>]*)>([\s\S]*?)<\/svg>/i)

      if (!svgMatch) {
        throw new Error(`无法解析 SVG 图标: ${fileName}`)
      }

      const viewBox = svgMatch[1].match(/\bviewBox=(['"])(.*?)\1/i)?.[2] || '0 0 24 24'
      const iconName = path.basename(fileName, '.svg')
      return `<symbol id="${symbolPrefix}-${iconName}" viewBox="${viewBox}">${svgMatch[2]}</symbol>`
    })
    .join('')
}

export function svgSpritePlugin({ iconDir, symbolPrefix = 'icon' }) {
  const normalizedIconDir = path.resolve(iconDir)

  return {
    name: 'xiaolvshu-svg-sprite',

    resolveId(id) {
      return id === VIRTUAL_MODULE_ID ? RESOLVED_MODULE_ID : null
    },

    load(id) {
      if (id !== RESOLVED_MODULE_ID) {
        return null
      }

      const sprite = createSprite(normalizedIconDir, symbolPrefix)
      return `
        const container = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
        container.setAttribute('aria-hidden', 'true')
        container.setAttribute('style', 'position:absolute;width:0;height:0;overflow:hidden')
        container.innerHTML = ${JSON.stringify(sprite)}
        document.body.insertBefore(container, document.body.firstChild)
      `
    },

    configureServer(server) {
      server.watcher.add(normalizedIconDir)
      server.watcher.on('change', changedPath => {
        if (changedPath.startsWith(normalizedIconDir) && changedPath.endsWith('.svg')) {
          server.ws.send({ type: 'full-reload' })
        }
      })
    }
  }
}
