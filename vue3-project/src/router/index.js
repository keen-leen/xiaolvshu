import { createRouter, createWebHistory } from 'vue-router'
import { getValidChannelPaths } from '@/config/channels'

// 页面级组件统一按路由懒加载，避免普通用户首屏加载完整后台和大型业务页面。
const layout = () => import('@/views/layout/index.vue')
const explore = () => import('@/views/explore/index.vue')
const publish = () => import('@/views/publish/index.vue')
const notification = () => import('@/views/notification/index.vue')
const user = () => import('@/views/user/index.vue')
const userProfile = () => import('@/views/user/UserProfile.vue')
const FollowList = () => import('@/views/user/FollowList.vue')
const ChannelPage = () => import('@/views/explore/ChannelPage.vue')
const PostDetail = () => import('@/views/PostDetail.vue')
const SearchResult = () => import('@/views/search/SearchResult.vue')
const PostManagementPage = () => import('@/views/post-management/index.vue')
const DraftBoxPage = () => import('@/views/draft-box/index.vue')
const TravelAiPage = () => import('@/views/travel-ai/index.vue')
const NotFound = () => import('@/views/NotFound.vue')

const AdminLogin = () => import('@/views/admin/AdminLogin.vue')
const AdminLayout = () => import('@/views/admin/AdminLayout.vue')
const ApiDocs = () => import('@/views/admin/ApiDocs.vue')
const AdminMonitor = () => import('@/views/admin/AdminMonitor.vue')
const UserManagement = () => import('@/views/admin/UserManagement.vue')
const PostManagement = () => import('@/views/admin/PostManagement.vue')
const CommentManagement = () => import('@/views/admin/CommentManagement.vue')
const CategoryManagement = () => import('@/views/admin/CategoryManagement.vue')
const TagManagement = () => import('@/views/admin/TagManagement.vue')
const LikeManagement = () => import('@/views/admin/LikeManagement.vue')
const CollectionManagement = () => import('@/views/admin/CollectionManagement.vue')
const FollowManagement = () => import('@/views/admin/FollowManagement.vue')
const NotificationManagement = () => import('@/views/admin/NotificationManagement.vue')
const SessionManagement = () => import('@/views/admin/SessionManagement.vue')
const AdminManagement = () => import('@/views/admin/AdminManagement.vue')
const AuditManagement = () => import('@/views/admin/AuditManagement.vue')
const SearchIndexManagement = () => import('@/views/admin/SearchIndexManagement.vue')

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: layout,
      redirect: '/explore',
      children: [
        {
          path: '/explore',
          name: 'explore',
          component: explore,
          children: [
            {
              path: '',
              name: 'recommend',
              component: ChannelPage
            },
            {
              path: '/explore/:channel',
              name: 'channel',
              component: ChannelPage,
              beforeEnter: (to, from, next) => {
                // 验证频道是否有效
                const validChannelPaths = getValidChannelPaths()
                if (validChannelPaths.includes(to.params.channel)) {
                  to.name = to.params.channel
                  next()
                } else {
                  // 无效频道重定向到推荐页
                  next('/explore')
                }
              }
            }
          ]
        },
        {
          path: '/post',
          name: 'post_detail',
          component: PostDetail
        },
        {
          path: 'publish',
          name: 'publish',
          component: publish,
        },
        {
          path: 'notification',
          name: 'notification',
          component: notification,
        },
        {
          path: 'user',
          name: 'user',
          component: user,
        },
        {
          path: 'user/:userId',
          name: 'user_profile',
          component: userProfile,
        },
        {
          path: 'follow/:type',
          name: 'follow_list',
          component: FollowList,
          beforeEnter: (to, from, next) => {
            // 验证type参数是否有效
            const validTypes = ['mutual', 'following', 'followers']
            if (validTypes.includes(to.params.type)) {
              next()
            } else {
              // 无效type重定向到following
              next({
                name: 'follow_list',
                params: { type: 'following' }
              })
            }
          }
        },
        {
          path: 'search_result',
          name: 'search_result',
          component: SearchResult,
          beforeEnter: (to, from, next) => {
            // 自动重定向到 "全部" tab
            next({
              name: 'search_result_tab',
              params: { tab: 'all' },
              query: to.query // 保持查询参数（如keyword）
            })
          }
        },
        {
          path: 'search_result/:tab',
          name: 'search_result_tab',
          component: SearchResult,
          beforeEnter: (to, from, next) => {
            // 验证tab参数是否有效
            const validTabs = ['all', 'post', 'video', 'user']
            if (validTabs.includes(to.params.tab)) {
              next()
            } else {
              // 无效tab重定向到all
              next({
                name: 'search_result_tab',
                params: { tab: 'all' },
                query: to.query
              })
            }
          }
        },
        {
          path: 'post-management',
          name: 'post_management',
          component: PostManagementPage
        },
        {
          path: 'draft-box',
          name: 'draft_box',
          component: DraftBoxPage
        },
        {
          path: 'travel-ai',
          name: 'travel_ai',
          component: TravelAiPage
        },
        // 404页面 - 捕获所有未匹配的路由
        {
          path: '/:pathMatch(.*)*',
          name: 'not_found',
          component: NotFound
        }
      ]
    },
    // Admin登录页面
    {
      path: '/admin/login',
      name: 'admin_login',
      component: AdminLogin
    },
    // 后台管理系统路由
    {
      path: '/admin',
      component: AdminLayout,
      beforeEnter: (to, from, next) => {
        // 如果访问的是/admin根路径，重定向到api-docs
        if (to.path === '/admin') {
          next('/admin/api-docs')
        } else {
          next()
        }
      },
      children: [
        {
          path: 'api-docs',
          name: 'admin_api_docs',
          component: ApiDocs
        },
        {
          path: 'monitor',
          name: 'admin_monitor',
          component: AdminMonitor
        },
        {
          path: 'search-index',
          name: 'admin_search_index',
          component: SearchIndexManagement
        },
        {
          path: 'users',
          name: 'admin_users',
          component: UserManagement
        },
        {
          path: 'posts',
          name: 'admin_posts',
          component: PostManagement
        },
        {
          path: 'comments',
          name: 'admin_comments',
          component: CommentManagement
        },
        {
          path: 'categories',
          name: 'admin_categories',
          component: CategoryManagement
        },
        {
          path: 'tags',
          name: 'admin_tags',
          component: TagManagement
        },
        {
          path: 'likes',
          name: 'admin_likes',
          component: LikeManagement
        },
        {
          path: 'collections',
          name: 'admin_collections',
          component: CollectionManagement
        },
        {
          path: 'follows',
          name: 'admin_follows',
          component: FollowManagement
        },
        {
          path: 'notifications',
          name: 'admin_notifications',
          component: NotificationManagement
        },
        {
          path: 'sessions',
          name: 'admin_sessions',
          component: SessionManagement
        },
        {
          path: 'admins',
          name: 'admin_admins',
          component: AdminManagement
        },
        {
          path: 'audit',
          name: 'admin_audit',
          component: AuditManagement
        }
      ]
    }
  ],
})

// 前端守卫只负责登录页体验，真实权限仍由后端 ROLE_ADMIN 强制执行。
router.beforeEach((to) => {
  if (to.path.startsWith('/admin')
      && to.path !== '/admin/login'
      && !localStorage.getItem('admin_token')) {
    return {
      name: 'admin_login',
      query: { redirect: to.fullPath }
    }
  }
  return true
})

export default router
