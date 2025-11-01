import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAccountSecurityStore = defineStore('accountSecurity', () => {
  // 模态框显示状态
  const showAccountSecurityModal = ref(false)
  // 打开账号与安全模态框
  const openAccountSecurityModal = () => {
    showAccountSecurityModal.value = true
  }
  // 关闭账号与安全模态框
  const closeAccountSecurityModal = () => {
    showAccountSecurityModal.value = false
  }
  return {
    showAccountSecurityModal,
    openAccountSecurityModal,
    closeAccountSecurityModal
  }
})