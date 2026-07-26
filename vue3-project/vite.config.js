/**
 * 小旅书旅游交流社区 - Vite配置文件
 * 
 * 原作者信息
 * @author ZTMYO
 * @github https://github.com/ZTMYO
 * @description Vue3项目的Vite构建配置
 * @version v1.2.0
 * 
 * 修改者信息
 * @author KeenLi
 * @github 
 */

import { fileURLToPath, URL } from 'node:url'
import path from 'path'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { svgSpritePlugin } from './build/viteSvgSprite.js'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    svgSpritePlugin({
      iconDir: path.resolve(process.cwd(), 'src/assets/icons'),
      symbolPrefix: 'icon'
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  build: {
    rollupOptions: {
      output: {
        /*
         * 稳定的第三方依赖单独缓存。页面已经按路由拆分，但 Vue、编辑器和 Markdown
         * 依赖仍会被 Rollup 汇入共享入口；显式分组可避免一次业务改动让整包缓存失效。
         */
        manualChunks: {
          'vue-vendor': ['vue', 'pinia', 'vue-router', '@vueuse/core', 'axios'],
          'editor-vendor': ['cropperjs', 'vue3-emoji-picker'],
          'markdown-vendor': ['markdown-it', 'dompurify']
        }
      }
    }
  },
  server: {
  /* allowedHosts: [   //添加白名单域名，手动部署取消前面的注释把qq.com替换成你的白名单域名
      'localhost',
      '127.0.0.1',
      'qq.com'
    ],  */
    proxy: {
      '/api': {
        target: 'http://localhost:8080',//后端接口地址
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '/api')
      },
      '/qhimgs1': {
        target: 'https://p3.ssl.qhimgs1.com',
        changeOrigin: true,
        secure: true,
        rewrite: (path) => path.replace(/^\/qhimgs1/, ''),
        configure: (proxy, options) => {
          proxy.on('proxyReq', (proxyReq, req, res) => {
            // 设置更完整的请求头来绕过防盗链
            proxyReq.setHeader('Referer', 'https://www.qhimg.com/')
            proxyReq.setHeader('User-Agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36')
            proxyReq.setHeader('Accept', 'image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8')
            proxyReq.setHeader('Accept-Language', 'zh-CN,zh;q=0.9,en;q=0.8')
            proxyReq.setHeader('Cache-Control', 'no-cache')
            proxyReq.setHeader('Pragma', 'no-cache')
            // 移除可能暴露代理的头部
            proxyReq.removeHeader('x-forwarded-for')
            proxyReq.removeHeader('x-forwarded-proto')
            proxyReq.removeHeader('x-forwarded-host')
          })
        }
      }
    }
  }
});
