import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login',         name: 'Login',       component: () => import('@/views/LoginRegister.vue')},
  { path: '/',              name: 'TaskList',    component: () => import('@/task/TaskListView.vue') },
  { path: '/tasks/:id',     name: 'TaskDetail',  component: () => import('@/task/TaskDetailView.vue') },
  { path: '/tasks/publish', name: 'TaskPublish',  component: () => import('@/task/TaskPublishView.vue'),  meta: { requiresAuth: true } },
  { path: '/my-tasks',      name: 'MyTasks',     component: () => import('@/task/MyTasksView.vue'),     meta: { requiresAuth: true } },
  { path: '/admin',         name: 'AdminDashboard', component: () => import('@/admin/AdminDashboard.vue'), meta: { requiresAuth: true } },
  { path: '/admin/users',   name: 'AdminUsers',     component: () => import('@/admin/UserManagement.vue'), meta: { requiresAuth: true } },
  { path: '/admin/tasks',   name: 'AdminTasks',     component: () => import('@/admin/TaskManagement.vue'), meta: { requiresAuth: true } },
  { path: '/admin/conversations', name: 'AdminConversations', component: () => import('@/admin/ConversationManagement.vue'), meta: { requiresAuth: true } },
  { path: '/admin/complaint/:id', name: 'ComplaintDetail', component: () => import('@/admin/ComplaintDetail.vue'), meta: { requiresAuth: true } },
  { path: '/my-orders',     name: 'MyOrders', component: () => import('@/order/MyOrdersView.vue'), meta: { requiresAuth: true } },
  { path: '/notifications', redirect: '/chat' },
  { path: '/profile',       name: 'Profile',     component: () => import('@/views/Profile.vue'), meta: { requiresAuth: true } },
  { path: '/chat',          name: 'Chat',        component: () => import('@/chat/ChatView.vue'),       meta: { requiresAuth: true } },
  { path: '/chat/:convId',  name: 'ChatConv',    component: () => import('@/chat/ChatView.vue'),       meta: { requiresAuth: true } }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(async (to, from, next) => {
  if (to.path === '/login') { next(); return }

  const { useUserStore } = await import('@/user/userStore')
  const userStore = useUserStore()

  // 先尝试从 localStorage 恢复登录态
  if (!userStore.isLoggedIn) {
    userStore.restoreSession()
  }

  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    next('/login')
  } else {
    next()
  }
})

export default router
