import { createRouter, createWebHistory } from 'vue-router'

/**
 * 路由表。
 * meta.requiresAuth：需要登录；meta.bare：不显示全局导航（登录页）；
 * 管理后台各页挂在 AdminLayout 下，共用左侧管理导航。
 */
const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/LoginRegister.vue'), meta: { bare: true } },
  { path: '/', name: 'TaskList', component: () => import('@/task/TaskListView.vue') },
  { path: '/tasks/publish', name: 'TaskPublish', component: () => import('@/task/TaskPublishView.vue'), meta: { requiresAuth: true } },
  { path: '/tasks/:id', name: 'TaskDetail', component: () => import('@/task/TaskDetailView.vue') },
  { path: '/my-tasks', name: 'MyTasks', component: () => import('@/task/MyTasksView.vue'), meta: { requiresAuth: true } },
  { path: '/my-orders', name: 'MyOrders', component: () => import('@/order/MyOrdersView.vue'), meta: { requiresAuth: true } },
  { path: '/notifications', redirect: '/chat' },
  { path: '/profile', name: 'Profile', component: () => import('@/views/Profile.vue'), meta: { requiresAuth: true } },
  { path: '/chat', name: 'Chat', component: () => import('@/chat/ChatView.vue'), meta: { requiresAuth: true } },
  { path: '/chat/:convId', name: 'ChatConv', component: () => import('@/chat/ChatView.vue'), meta: { requiresAuth: true } },
  {
    path: '/admin',
    component: () => import('@/admin/AdminLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'AdminDashboard', component: () => import('@/admin/AdminDashboard.vue') },
      { path: 'tasks', name: 'AdminTasks', component: () => import('@/admin/TaskManagement.vue') },
      { path: 'users', name: 'AdminUsers', component: () => import('@/admin/UserManagement.vue') },
      { path: 'categories', name: 'AdminCategories', component: () => import('@/admin/CategoryManagement.vue') },
      { path: 'notifications', name: 'AdminNotifications', component: () => import('@/admin/NotificationManagement.vue') },
      { path: 'conversations', name: 'AdminConversations', component: () => import('@/admin/ConversationManagement.vue') },
      { path: 'finance', name: 'Finance', component: () => import('@/admin/FinanceManagement.vue') },
      { path: 'complaint/:id', name: 'ComplaintDetail', component: () => import('@/admin/ComplaintDetail.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  // 切换页面回到顶部；浏览器前进后退恢复原位置
  scrollBehavior: (to, from, saved) => saved || { top: 0 }
})

/**
 * 全局前置导航守卫。
 * 负责会话持久化恢复、受限账号（冻结用户仅允许访问订单与个人资料页）隔离与管理员路径越权拦截。
 * 未登录访问受保护页面时带上 redirect，登录后回到原页面。
 */
router.beforeEach(async (to) => {
  if (to.path === '/login') return true

  const { useUserStore } = await import('@/user/userStore')
  const userStore = useUserStore()

  if (!userStore.isLoggedIn) userStore.restoreSession()

  if (to.matched.some(r => r.meta.requiresAuth) && !userStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (userStore.restricted && !['/my-orders', '/profile'].includes(to.path)) return '/my-orders'
  if (to.path.startsWith('/admin') && !userStore.isAdmin) return '/'
  return true
})

export default router
