import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 认证模态框状态管理
 * 集中管理认证模态框的显示状态
 */
export const useVerifiedStore = defineStore('verified', () => {
  // 模态框显示状态
  const showVerifiedModal = ref(false)

  // 打开认证模态框
  const openVerifiedModal = () => {
    showVerifiedModal.value = true
  }

  // 关闭模态框
  const closeVerifiedModal = () => {
    showVerifiedModal.value = false
  }

  return {
    showVerifiedModal,
    openVerifiedModal,
    closeVerifiedModal
  }
})