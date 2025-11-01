<script setup>
import { ref, onMounted, watch, computed, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useNavigationStore } from '@/stores/navigation'
import { useEventStore } from '@/stores/event'
import TabContainer from '@/components/TabContainer.vue'
import TagContainer from './components/TagContainer.vue'
import UserList from './components/UserList.vue'
import WaterfallFlow from '@/components/WaterfallFlow.vue'
import LoadingSpinner from '@/components/spinner/LoadingSpinner.vue'
import SearchFloatingBtn from './components/SearchFloatingBtn.vue'
import apiConfig from '@/config/api.js'

const route = useRoute()
const router = useRouter()
const navigationStore = useNavigationStore()
const eventStore = useEventStore()

const keyword = ref('')
const selectedTag = ref('')
const activeTab = ref('all')


const searchTabs = [
    { id: 'all', label: '全部' },
    { id: 'posts', label: '图文' },
    { id: 'videos', label: '视频' },
    { id: 'users', label: '用户' }
]

const searchResults = ref({})
const userResults = ref([])
const postResults = ref([])
const tagStats = ref([])
const loading = ref(false)

const cachedAllPosts = ref([])
const cachedKeyword = ref('')
const cachedPostsData = ref([])  // 缓存图文数据
const cachedVideosData = ref([]) // 缓存视频数据

const isTagLoading = ref(false)
let eventListenerKey = null

async function searchContent(type = 'all', page = 1, limit = 20) {
    if (!keyword.value.trim() && !selectedTag.value.trim()) {
        console.warn('搜索关键词和标签都为空')
        return
    }

    // 检查缓存数据
    if (keyword.value.trim() && keyword.value === cachedKeyword.value && !selectedTag.value.trim()) {
        if (type === 'all' && cachedAllPosts.value.length > 0) {
            postResults.value = cachedAllPosts.value
            return
        } else if (type === 'posts' && cachedPostsData.value.length > 0) {
            postResults.value = cachedPostsData.value
            return
        } else if (type === 'videos' && cachedVideosData.value.length > 0) {
            postResults.value = cachedVideosData.value
            return
        }
    }

    // 处理标签过滤的缓存情况
    if (selectedTag.value.trim() && keyword.value === cachedKeyword.value) {
        if (type === 'all' && cachedAllPosts.value.length > 0) {
            filterPostsByTag()
            return
        } else if (type === 'posts' && cachedPostsData.value.length > 0) {
            filterPostsByTag()
            return
        } else if (type === 'videos' && cachedVideosData.value.length > 0) {
            filterPostsByTag()
            return
        }
    }

    loading.value = true
    try {

        const params = new URLSearchParams({
            type,
            page: page.toString(),
            limit: limit.toString()
        })

        if (keyword.value.trim()) {
            params.append('keyword', keyword.value.trim())
        }

        if (selectedTag.value.trim() && (type === 'users' || !keyword.value.trim())) {
            params.append('tag', selectedTag.value.trim())
        }

        const response = await fetch(`${apiConfig.baseURL}/search?${params.toString()}`, {
            headers: {
                'Authorization': `Bearer ${localStorage.getItem('token')}`
            }
        }).then(res => res.json())



        if (response && response.code === 200 && response.data) {
            searchResults.value = response.data

            if (response.data.tagStats) {
                tagStats.value = response.data.tagStats
            } else if (response.data.posts && response.data.posts.tagStats) {
                tagStats.value = response.data.posts.tagStats
            } else {
                tagStats.value = []
            }

            if (type === 'users' || (type === 'all' && response.data.users)) {
                handleUserResults(response.data.users)
            }

            if (type === 'posts' || type === 'videos' || (type === 'all' && response.data.data)) {
                // 对于all类型，数据直接在response.data中；对于posts/videos类型，数据在response.data.posts中
                const postsData = type === 'all' ? response.data : response.data.posts
                handlePostResults(postsData)

                if (keyword.value.trim() && !selectedTag.value.trim() && postsData && postsData.data && postsData.data.length > 0) {
                    // 根据类型分别缓存数据（只有当有数据时才缓存）
                    if (type === 'all') {
                        cachedAllPosts.value = postsData.data
                    } else if (type === 'posts') {
                        cachedPostsData.value = postsData.data
                    } else if (type === 'videos') {
                        cachedVideosData.value = postsData.data
                    }
                    cachedKeyword.value = keyword.value
                }
            }

            if (selectedTag.value && keyword.value === cachedKeyword.value && cachedAllPosts.value.length > 0 && (type === 'all' || type === 'posts' || type === 'videos')) {
                filterPostsByTag()
            }
        } else {
            console.error('搜索失败:', response)
            searchResults.value = {}
            userResults.value = []
            postResults.value = []
            tagStats.value = []
            // 清空缓存，避免显示旧数据
            cachedAllPosts.value = []
            cachedPostsData.value = []
            cachedVideosData.value = []
            cachedKeyword.value = ''
        }
    } catch (error) {
        console.error('搜索失败:', error)
        searchResults.value = {}
        userResults.value = []
        postResults.value = []
        tagStats.value = []
        // 清空缓存，避免显示旧数据
        cachedAllPosts.value = []
        cachedPostsData.value = []
        cachedVideosData.value = []
        cachedKeyword.value = ''
    } finally {
        loading.value = false
    }
}

function filterPostsByTag() {
    if (!selectedTag.value) {
        // 如果没有选择标签，根据当前tab显示对应的全部数据
        if (activeTab.value === 'all') {
            postResults.value = cachedAllPosts.value
        } else if (activeTab.value === 'posts') {
            postResults.value = cachedPostsData.value
        } else if (activeTab.value === 'videos') {
            postResults.value = cachedVideosData.value
        }
        return
    }

    // 根据当前活跃的标签页选择对应的缓存数据进行过滤
    let sourceData = []
    if (activeTab.value === 'all') {
        sourceData = cachedAllPosts.value
    } else if (activeTab.value === 'posts') {
        // 如果没有图文缓存数据，从全部数据中过滤出图文类型
        if (cachedPostsData.value.length > 0) {
            sourceData = cachedPostsData.value
        } else {
            sourceData = cachedAllPosts.value.filter(post => post.type === 1)
        }
    } else if (activeTab.value === 'videos') {
        // 如果没有视频缓存数据，从全部数据中过滤出视频类型
        if (cachedVideosData.value.length > 0) {
            sourceData = cachedVideosData.value
        } else {
            sourceData = cachedAllPosts.value.filter(post => post.type === 2)
        }
    }

    const filteredPosts = sourceData.filter(post => {
        return post.tags && post.tags.some(tag => tag.name === selectedTag.value)
    })

    postResults.value = filteredPosts
}

function handleUserResults(usersData) {
    if (usersData && usersData.data) {
        userResults.value = usersData.data.map(user => {
            const transformedUser = {
                id: user.id,
                nickname: user.nickname,
                userId: user.user_id,
                avatar: user.avatar,
                verified: user.verified || 0,
                followers: user.fans_count || 0,
                posts: user.post_count || 0,
                isFollowing: user.isFollowing || false,
                buttonType: user.buttonType || 'follow',
                bio: user.bio,
                location: user.location
            }

            return transformedUser
        })
    } else {
        userResults.value = []
    }
}

function handlePostResults(postsData) {
    if (postsData && postsData.data && postsData.data.length > 0) {
        postResults.value = postsData.data
    } else {
        postResults.value = []
        // 如果搜索结果为空，清空对应的缓存
        if (activeTab.value === 'all') {
            cachedAllPosts.value = []
        } else if (activeTab.value === 'posts') {
            cachedPostsData.value = []
        } else if (activeTab.value === 'videos') {
            cachedVideosData.value = []
        }
    }
}

function handleTabChange(item) {
    activeTab.value = item.id
    navigationStore.scrollToTop('instant')

    if (item.id === 'users') {
        searchContent(activeTab.value)
    } else if (item.id === 'videos') {
        // 视频tab：检查是否有视频缓存数据
        if (cachedVideosData.value.length > 0 && cachedKeyword.value === keyword.value) {
            if (selectedTag.value && selectedTag.value.trim()) {
                filterPostsByTag()
            } else {
                postResults.value = [...cachedVideosData.value]
            }
        } else {
            // 没有视频缓存或关键词不匹配，重新搜索
            searchContent('videos')
        }
    } else if (item.id === 'posts') {
        // 图文tab：检查是否有图文缓存数据
        if (cachedPostsData.value.length > 0 && cachedKeyword.value === keyword.value) {
            if (selectedTag.value && selectedTag.value.trim()) {
                filterPostsByTag()
            } else {
                postResults.value = [...cachedPostsData.value]
            }
        } else {
            // 没有图文缓存或关键词不匹配，重新搜索
            searchContent('posts')
        }
    } else {
        // 全部tab
        if (cachedAllPosts.value.length > 0 && cachedKeyword.value === keyword.value) {
            if (selectedTag.value && selectedTag.value.trim()) {
                filterPostsByTag()
            } else {
                postResults.value = [...cachedAllPosts.value] // 使用数组复制触发变化检测
            }
        } else {
            // 全部tab使用all类型
            searchContent('all')
        }
    }
}

function handleTagReload() {
    isTagLoading.value = true

    setTimeout(() => {
        isTagLoading.value = false
    }, 700)
}

function handleFloatingBtnReload() {
    isTagLoading.value = true

    eventStore.triggerFloatingBtnReload()

    // 触发强制重新检查图片加载事件
    setTimeout(() => {
        document.dispatchEvent(new CustomEvent('force-recheck'))
    }, 100)

    setTimeout(() => {
        isTagLoading.value = false
    }, 700)
}

function handleFloatingBtnReloadRequest() {
    // 清除缓存并重新搜索
    cachedAllPosts.value = []
    cachedKeyword.value = ''

    // 调用现有的刷新逻辑
    handleFloatingBtnReload()

    // 重新搜索当前内容
    setTimeout(() => {
        searchContent(activeTab.value)
    }, 100)
}


function handleUserClick(user) {
    const userUrl = `${window.location.origin}/user/${user.userId}`
    window.open(userUrl, '_blank')
}

function handleUserFollow(user) {
    console.log('关注用户:', user)
}

function handleUserUnfollow(user) {
    console.log('取消关注用户:', user)
}



watch(() => route.query, (newQuery, oldQuery) => {
    const newKeyword = newQuery.keyword || ''
    const newTag = newQuery.tag || ''

    const keywordChanged = newKeyword !== keyword.value
    const tagChanged = newTag !== selectedTag.value

    // 只有当关键词或标签真正发生变化时才触发搜索
    if ((keywordChanged || tagChanged) && oldQuery) {
        keyword.value = newKeyword
        selectedTag.value = newTag

        if (keywordChanged) {
            cachedAllPosts.value = []
            cachedPostsData.value = []
            cachedVideosData.value = []
            cachedKeyword.value = ''
        }
        navigationStore.scrollToTop('instant')
        searchContent(activeTab.value)
    } else if (!oldQuery) {
        // 初始化时只更新值，不触发搜索
        keyword.value = newKeyword
        selectedTag.value = newTag
    }
}, { immediate: true })

watch(() => route.params.tab, (newTab) => {
    if (newTab && ['all', 'posts', 'videos', 'users'].includes(newTab)) {
        // 只更新activeTab，不触发搜索，避免与handleTabChange重复
        activeTab.value = newTab
    }
}, { immediate: true })

onMounted(() => {
    keyword.value = route.query.keyword || ''
    selectedTag.value = route.query.tag || ''
    activeTab.value = route.params.tab || 'all'

    if (keyword.value || selectedTag.value) {
        searchContent(activeTab.value)
    }

    eventListenerKey = eventStore.addEventListener('floating-btn-reload-request', handleFloatingBtnReload)
})

onUnmounted(() => {
    if (eventListenerKey) {
        eventStore.removeEventListener(eventListenerKey)
    }
})
</script>

<template>
    <div class="search-container">

        <TabContainer :tabs="searchTabs" :activeTab="activeTab" @tab-change="handleTabChange" />


        <TagContainer :tagStats="tagStats" :activeTag="selectedTag" :activeTab="activeTab"
            @tag-reload="handleTagReload" />


        <LoadingSpinner v-if="isTagLoading" />


        <div class="search-main" :class="{ 'with-loading': isTagLoading }">

            <div v-if="activeTab === 'users'">
                <UserList :users="userResults" :loading="loading" @follow="handleUserFollow"
                    @unfollow="handleUserUnfollow" @userClick="handleUserClick" />
            </div>


            <div v-else>
                <WaterfallFlow :searchKeyword="keyword" :searchTag="selectedTag" :preloadedPosts="postResults" :type="activeTab" />
            </div>
        </div>
        <SearchFloatingBtn @reload="handleFloatingBtnReloadRequest" />
    </div>
</template>

<style scoped>
.search-container {
    padding-top: 72px;
    min-height: 100vh;
    background: var(--bg-color-primary);
    transition: background 0.2s ease;
}

.search-main {
    padding: 0px 10px calc(48px + constant(safe-area-inset-bottom)) 10px;
    padding: 0px 10px calc(48px + env(safe-area-inset-bottom)) 10px;
    width: 100%;
    box-sizing: border-box;
    overflow-x: hidden;
    background: var(--bg-color-primary);
    transition: margin-top 0.3s ease, background 0.2s ease;
}

.search-main.with-loading {
    margin-top: 40px;
}


@media (max-width: 768px) {
    .search-main {
        padding: 15px;
    }
}
</style>