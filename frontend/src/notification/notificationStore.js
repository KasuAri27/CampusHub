import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '@/common/http'
import { ElMessage } from 'element-plus'

export const useNotificationStore = defineStore('notification', () => {
  const notifications = ref([])
  const unreadCount = ref(0)
  const total = ref(0)
  const loading = ref(false)
  const currentPage = ref(1)
  const pageSize = ref(10)

  async function fetchNotifications(page = currentPage.value, size = pageSize.value, unreadOnly = false) {
    loading.value = true
    try {
      const res = await http.get('/notifications', {
        params: { pageNum: page, pageSize: size, unreadOnly }
      })
      const pageData = res.data.data
      notifications.value = pageData.items || []
      total.value = pageData.pagination?.total || 0
      currentPage.value = page
    } catch (error) {
      ElMessage.error(error.message || '获取通知失败')
    } finally {
      loading.value = false
    }
  }

  async function fetchUnreadCount() {
    try {
      const res = await http.get('/notifications/unread-count')
      let count = res.data.data
      // 兼容对象格式
      if (typeof count === 'object' && count !== null) {
        count = count.count || count.unreadCount || count.total || 0
      }
      unreadCount.value = Number(count) || 0
      console.log('[Notification] 未读数:', unreadCount.value)
    } catch (error) {
      console.error('获取未读数量失败', error)
    }
  }

  async function markAsRead(notificationId) {
    try {
      await http.put(`/notifications/${notificationId}/read`)
      // 更新本地列表状态（可选）
      const idx = notifications.value.findIndex(n => n.id === notificationId)
      if (idx !== -1 && !notifications.value[idx].isRead) {
        notifications.value[idx].isRead = true
      }
      // 重新拉取未读数，保证准确
      await fetchUnreadCount()
      ElMessage.success('已标记为已读')
    } catch (error) {
      ElMessage.error(error.message || '操作失败')
    }
  }

  async function markAllAsRead() {
    try {
      await http.put('/notifications/read-all')
      notifications.value.forEach(n => { n.isRead = true })
      await fetchUnreadCount()
      ElMessage.success('全部已读')
    } catch (error) {
      ElMessage.error(error.message || '操作失败')
    }
  }

  async function refresh() {
    await fetchNotifications(currentPage.value, pageSize.value)
    await fetchUnreadCount()
  }

  return {
    notifications,
    unreadCount,
    total,
    loading,
    currentPage,
    pageSize,
    fetchNotifications,
    fetchUnreadCount,
    markAsRead,
    markAllAsRead,
    refresh
  }
})