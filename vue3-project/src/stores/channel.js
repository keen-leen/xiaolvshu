import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getChannels, loadChannelsFromAPI, getChannelIdByPath, getChannelPath } from '@/config/channels'

export const useChannelStore = defineStore('channel', () => {
  // 频道列表
  const channels = ref(getChannels())
  const isLoading = ref(false)

  // 当前活跃的频道ID
  const activeChannelId = ref('recommend')

  // 动态加载频道数据
  const loadChannels = async () => {
    isLoading.value = true
    try {
      await loadChannelsFromAPI()
      channels.value = getChannels()
    } finally {
      isLoading.value = false
    }
  }

  // 设置活跃频道
  const setActiveChannel = (channelId) => {
    activeChannelId.value = channelId
  }

  // 根据路径获取频道ID
  const getChannelIdByPathFn = getChannelIdByPath

  // 根据频道ID获取路径
  const getChannelPathFn = getChannelPath

  return {
    channels,
    activeChannelId,
    isLoading,
    setActiveChannel,
    loadChannels,
    getChannelIdByPath: getChannelIdByPathFn,
    getChannelPath: getChannelPathFn
  }
})