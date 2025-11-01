import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 键盘快捷键模态框状态管理
 * 集中管理键盘快捷键模态框的显示状态
 */
export const useKeyboardShortcutsStore = defineStore('keyboardShortcuts', () => {
  // 模态框显示状态
  const showKeyboardShortcutsModal = ref(false)

  // 打开键盘快捷键模态框
  const openKeyboardShortcutsModal = () => {
    showKeyboardShortcutsModal.value = true
  }

  // 关闭键盘快捷键模态框
  const closeKeyboardShortcutsModal = () => {
    showKeyboardShortcutsModal.value = false
  }

  return {
    showKeyboardShortcutsModal,
    openKeyboardShortcutsModal,
    closeKeyboardShortcutsModal
  }
})