import js from '@eslint/js'
import globals from 'globals'
import pluginVue from 'eslint-plugin-vue'

// 首次接入 lint 时先把历史规则违规作为告警展示；新增代码仍能立即看到问题，
// 但不会要求在同一个重构中机械改写数百处无关模板格式。
const warnOnly = config => ({
  ...config,
  rules: Object.fromEntries(
    Object.entries(config.rules || {}).map(([name, setting]) => [
      name,
      Array.isArray(setting) ? ['warn', ...setting.slice(1)] : 'warn'
    ])
  )
})

export default [
  {
    ignores: ['dist/**', 'node_modules/**']
  },
  warnOnly(js.configs.recommended),
  ...pluginVue.configs['flat/recommended'].map(warnOnly),
  {
    files: ['**/*.{js,vue}'],
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.node
      }
    },
    rules: {
      // 先把历史存量作为可见告警纳入 CI，后续按模块消减，避免一次格式化重写全仓。
      'no-unused-vars': 'warn',
      'no-undef': 'warn',
      'vue/multi-word-component-names': 'off',
      'vue/max-attributes-per-line': 'off',
      'vue/html-indent': 'off',
      'vue/html-self-closing': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/first-attribute-linebreak': 'off'
    }
  }
]
