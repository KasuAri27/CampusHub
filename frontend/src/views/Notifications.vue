<template>
  <div class="notifications-container">
    <el-card class="notifications-card">
      <template #header>
        <div class="card-header">
          <span>我的通知</span>
          <el-badge
              :value="notificationStore.unreadCount"
              :hidden="notificationStore.unreadCount === 0"
              class="badge"
          />
          <el-button size="small" type="primary" @click="handleMarkAllRead" style="margin-left: auto;">
            全部已读
          </el-button>
        </div>
      </template>
      <el-table :data="notificationStore.notifications" v-loading="notificationStore.loading">
        <el-table-column prop="title" label="标题" min-width="150" />
        <el-table-column prop="content" label="内容" min-width="250" show-overflow-tooltip />
        <el-table-column prop="createTime" label="时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag v-if="!row.isRead" type="danger" size="small">未读</el-tag>
            <el-tag v-else type="info" size="small">已读</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button
                v-if="!row.isRead"
                link
                type="primary"
                @click="handleMarkRead(row)"
            >
              标记已读
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination
            v-model:current-page="notificationStore.currentPage"
            v-model:page-size="notificationStore.pageSize"
            :total="notificationStore.total"
            layout="prev, pager, next"
            @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useNotificationStore } from '@/notification/notificationStore'

const router = useRouter()
const notificationStore = useNotificationStore()

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth()+1}/${d.getDate()} ${d.getHours().toString().padStart(2,'0')}:${d.getMinutes().toString().padStart(2,'0')}`
}

const handleMarkRead = async (row) => {
  await notificationStore.markAsRead(row.id)
  const match = row.content?.match(/complaintId=(\d+)/)
  if (match) router.push(`/admin/complaint/${match[1]}`)
}

const handleMarkAllRead = async () => {
  await notificationStore.markAllAsRead()
  await notificationStore.refresh()
}

const handlePageChange = (page) => {
  notificationStore.fetchNotifications(page)
}

onMounted(() => {
  notificationStore.refresh()
})
</script>

<style scoped>
.notifications-container {
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px;
}
.notifications-card {
  border-radius: 12px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 18px;
  font-weight: 600;
}
.badge {
  margin-left: 8px;
}
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
</style>