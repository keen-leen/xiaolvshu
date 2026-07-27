<template>
  <div class="layout-container">
    <Sidebar v-if="showSidebar" />
    <div class="main-content" :class="{ 'with-sidebar': showSidebar }">
      <LayoutHeader />
      <div class="content-wrapper">
        <router-view />
      </div>
      <!--
        网站备案信息必须在公开页面底部持续可见，并直接链接工信部备案官网。
        独立于桌面侧栏和移动端导航放置，避免响应式切换后备案号消失。
      -->
      <footer class="site-record-footer">
        <a
          href="https://beian.miit.gov.cn/"
          target="_blank"
          rel="noopener noreferrer"
          class="site-record-link"
        >
          晋ICP备2026000975号
        </a>
      </footer>
      <LayoutFooter v-if="!showSidebar" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import Sidebar from './components/Sidebar.vue'
import LayoutHeader from './components/LayoutHeader.vue'
import LayoutFooter from './components/LayoutFooter.vue'

const showSidebar = ref(window.innerWidth > 960)
const handleResize = () => {
  showSidebar.value = window.innerWidth > 960
}
onMounted(() => {
  window.addEventListener('resize', handleResize)
})
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.layout-container {
  display: flex;
  min-height: 100vh;
  background-color: var(--bg-color-primary);
  min-width: 320px;
  margin: 0;
  width: 100%;
  overflow-x: hidden;
  position: relative;
  box-sizing: border-box;
  transition: background-color 0.2s ease;

}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 100vh;
  transition: margin-left 0.3s;
  width: 100%;
  overflow-x: hidden;
}

/* 大屏模式下主内容区域留出侧边栏空间 */
.main-content.with-sidebar {
  margin-left: 228px;
  width: calc(100% - 228px);
}

.content-wrapper {
  flex: 1;
  margin: 0 auto;
  width: 100%;
  max-width: 1200px;
  padding: 0;
  box-sizing: border-box;
  padding-bottom: 48px;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  background-color: var(--bg-color-primary);
  transition: background-color 0.2s ease;
}

.site-record-footer {
  position: fixed;
  right: 20px;
  bottom: 12px;
  z-index: 98;
  width: auto;
  padding: 5px 10px;
  box-sizing: border-box;
  text-align: center;
  border: 1px solid var(--border-color, rgba(128, 128, 128, 0.16));
  border-radius: 999px;
  background-color: var(--bg-color-primary);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.site-record-link {
  color: var(--text-color-tertiary);
  font-size: 12px;
  line-height: 20px;
  text-decoration: none;
  transition: color 0.2s ease;
}

.site-record-link:hover {
  color: var(--text-color-primary);
  text-decoration: underline;
}

.site-record-link:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 3px;
  border-radius: 2px;
}

@media (max-width: 960px) {
  .main-content {
    margin-left: 0;
  }

  .content-wrapper {
    padding-bottom: 48px;
  }

  /*
   * 移动端瀑布流会在触底时继续追加内容，因此备案号不能跟随普通文档流。
   * 固定在底部导航上方，既不会被新内容推走，也不会遮挡五个主要导航入口。
   */
  .site-record-footer {
    left: 0;
    right: 0;
    bottom: calc(48px + env(safe-area-inset-bottom));
    padding: 3px 8px;
    border-right: 0;
    border-bottom: 0;
    border-left: 0;
    border-radius: 0;
    box-shadow: none;
  }
}

@media (max-width: 768px) {
  .content-wrapper {
    padding-bottom: 48px;
  }
}

@media (max-width: 480px) {
  .content-wrapper {
    padding-bottom: 48px;
  }
}

@media (min-width: 961px) {
  .content-wrapper {
    padding-bottom: 0;
  }
}
</style>
