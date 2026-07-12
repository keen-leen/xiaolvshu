import { defineStore } from 'pinia'

export const useTravelAiStore = defineStore('travelAi', {
  state: () => ({
    visible: false,
    initialPrompt: '',
    sourceContext: null
  }),

  actions: {
    openAssistant(prompt = '', context = null) {
      this.initialPrompt = prompt || ''
      this.sourceContext = context
      this.visible = true
    },

    closeAssistant() {
      this.visible = false
    },

    consumeInitialPrompt() {
      const prompt = this.initialPrompt
      this.initialPrompt = ''
      return prompt
    }
  }
})
