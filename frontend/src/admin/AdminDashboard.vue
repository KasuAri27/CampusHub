<template>
  <div class="admin-dashboard" v-loading="loading">
    <!-- ==================== 待审核需求 ==================== -->
    <div class="review-section" v-if="!loading">
      <div class="section-header">
        <h2 class="section-title">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <path d="M12 8v4M12 16h.01"/>
          </svg>
          待审核需求
        </h2>
        <span class="queue-badge" v-if="pendingTasks.length > 0">
          {{ currentIndex + 1 }} / {{ pendingTasks.length }}
        </span>
      </div>

      <!-- 有待审核需求 -->
      <template v-if="pendingTasks.length > 0 && currentTask">
        <div class="review-card" :key="currentTask.id">
          <!-- 顶部装饰条 - 待审核橙色 -->
          <div class="card-accent review-accent"></div>

          <div class="card-body">
            <!-- 任务信息（仿 TaskCard，可点击查看详情） -->
            <div class="task-info" @click="$router.push(`/tasks/${currentTask.id}`)">
              <!-- 头部：状态标签 + 报酬 -->
              <div class="card-header">
                <span class="status-badge pending">
                  <span class="status-dot"></span>
                  待审核
                </span>
                <span class="reward" v-if="currentTask.reward">
                  <span class="currency">&#xFFE5;</span>{{ currentTask.reward }}
                </span>
                <span class="reward free" v-else>免费互助</span>
              </div>

              <!-- 标题 -->
              <h3 class="card-title">{{ currentTask.title }}</h3>

              <!-- 描述 -->
              <p class="card-desc">{{ truncate(currentTask.description || '', 100) }}</p>

              <!-- 详情行 -->
              <div class="detail-row">
                <span class="detail-item">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M20.59 13.41l-7.17 7.17a2 2 0 01-2.83 0L2 12V2h10l8.59 8.59a2 2 0 010 2.82z"/>
                    <line x1="7" y1="7" x2="7.01" y2="7"/>
                  </svg>
                  {{ currentTask.categoryName || '未分类' }}
                </span>
                <span class="detail-item">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                    <circle cx="9" cy="7" r="4"/>
                  </svg>
                  {{ currentTask.requesterName || '匿名' }}
                </span>
                <span class="detail-item" v-if="currentTask.location">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0118 0z"/>
                    <circle cx="12" cy="10" r="3"/>
                  </svg>
                  {{ currentTask.location }}
                </span>
                <span class="detail-item" v-if="currentTask.deadline">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10"/>
                    <polyline points="12 6 12 12 16 14"/>
                  </svg>
                  {{ formatDate(currentTask.deadline) }}
                </span>
              </div>

              <!-- 发布者额外信息行 -->
              <div class="extra-row" v-if="currentTask.requesterEmail">
                <span class="extra-item">发布者邮箱：{{ currentTask.requesterEmail }}</span>
              </div>
            </div>

            <!-- 操作按钮区 -->
            <div class="action-bar">
              <button
                class="action-btn approve-btn"
                :disabled="actionLoading"
                @click.stop="handleApprove"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
                审核通过
              </button>
              <button
                class="action-btn reject-btn"
                :disabled="actionLoading"
                @click.stop="handleReject"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                  <line x1="18" y1="6" x2="6" y2="18"/>
                  <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
                驳回
              </button>
            </div>
          </div>

          <!-- 队列提示 -->
          <div class="queue-hint" v-if="pendingTasks.length > 1">
            还有 {{ pendingTasks.length - 1 }} 个需求待审核
          </div>
        </div>
      </template>

      <!-- 无待审核需求 -->
      <div class="review-empty" v-else>
        <div class="empty-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M22 11.08V12a10 10 0 11-5.93-9.14"/>
            <polyline points="22 4 12 14.01 9 11.01"/>
          </svg>
        </div>
        <h3>全部审核完毕</h3>
        <p>当前没有待审核的需求，干得漂亮！</p>
      </div>
    </div>

    <!-- ==================== 快捷操作 ==================== -->
    <div class="quick-actions">
      <button class="action-card" @click="$router.push('/admin/users')">
        <div class="action-icon users">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
          </svg>
        </div>
        <div class="action-content">
          <h3>用户管理</h3>
          <p>查看和管理平台用户</p>
        </div>
        <svg class="action-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M5 12h14M12 5l7 7-7 7"/>
        </svg>
      </button>

      <button class="action-card" @click="$router.push('/admin/tasks')">
        <div class="action-icon tasks">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/>
            <rect x="8" y="2" width="8" height="4" rx="1" ry="1"/>
            <line x1="9" y1="10" x2="15" y2="10"/>
            <line x1="9" y1="14" x2="15" y2="14"/>
            <line x1="9" y1="18" x2="12" y2="18"/>
          </svg>
        </div>
        <div class="action-content">
          <h3>需求管理</h3>
          <p>审核和管理所有需求</p>
        </div>
        <svg class="action-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M5 12h14M12 5l7 7-7 7"/>
        </svg>
      </button>
    </div>

    <!-- ==================== 数据概览 ==================== -->
    <div class="stats-section">
      <h2 class="section-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="20" x2="18" y2="10"/>
          <line x1="12" y1="20" x2="12" y2="4"/>
          <line x1="6" y1="20" x2="6" y2="14"/>
        </svg>
        数据概览
      </h2>

      <div class="stats-grid">
        <div class="stat-card clickable" @click="$router.push('/admin/users')">
          <div class="stat-icon users">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-value">{{ stats.totalUsers || 0 }}</span>
            <span class="stat-label">总用户</span>
          </div>
          <div class="stat-trend up">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/>
              <polyline points="17 6 23 6 23 12"/>
            </svg>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon warning">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="8.5" cy="7" r="4"/>
              <line x1="18" y1="8" x2="23" y2="13"/>
              <line x1="23" y1="8" x2="18" y2="13"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-value">{{ stats.bannedUsers || 0 }}</span>
            <span class="stat-label">禁用用户</span>
          </div>
        </div>

        <div class="stat-card clickable" @click="$router.push('/admin/tasks')">
          <div class="stat-icon primary">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/>
              <polyline points="14,2 14,8 20,8"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-value">{{ stats.totalTasks || 0 }}</span>
            <span class="stat-label">总需求</span>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon info">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <polyline points="12 6 12 12 16 14"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-value">{{ stats.publishedTasks || 0 }}</span>
            <span class="stat-label">待接单</span>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon accent">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2v20M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-value">{{ stats.inProgressTasks || 0 }}</span>
            <span class="stat-label">进行中</span>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon success">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 11.08V12a10 10 0 11-5.93-9.14"/>
              <polyline points="22 4 12 14.01 9 11.01"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-value">{{ stats.completedTasks || 0 }}</span>
            <span class="stat-label">已完成</span>
          </div>
        </div>

        <div class="stat-card highlight">
          <div class="stat-icon new">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-value">{{ stats.todayNewTasks || 0 }}</span>
            <span class="stat-label">今日新增</span>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon muted">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <line x1="15" y1="9" x2="9" y2="15"/>
              <line x1="9" y1="9" x2="15" y2="15"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-value">{{ stats.totalOrders || 0 }}</span>
            <span class="stat-label">已取消</span>
          </div>
        </div>

        <!-- 聊天统计 -->
        <div class="stat-card clickable" @click="$router.push('/admin/conversations')">
          <div class="stat-icon chats">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-value">{{ stats.totalConversations || 0 }}</span>
            <span class="stat-label">会话数</span>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon msgs">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-value">{{ stats.totalMessages || 0 }}</span>
            <span class="stat-label">消息数</span>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon today">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="4" width="18" height="18" rx="2"/>
              <line x1="16" y1="2" x2="16" y2="6"/>
              <line x1="8" y1="2" x2="8" y2="6"/>
              <line x1="3" y1="10" x2="21" y2="10"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-value">{{ stats.todaysMessages || 0 }}</span>
            <span class="stat-label">今日消息</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 驳回理由弹窗 ==================== -->
    <div class="dialog-overlay" v-if="rejectDialog.visible" @click.self="cancelReject">
      <div class="dialog-panel">
        <h3 class="dialog-title">驳回需求</h3>
        <p class="dialog-subtitle">
          将驳回「<strong>{{ currentTask?.title }}</strong>」，请填写驳回理由通知发布者。
        </p>
        <textarea
          v-model="rejectDialog.reason"
          class="dialog-textarea"
          placeholder="请输入驳回理由（可选）"
          rows="4"
          maxlength="200"
          @keydown.escape="cancelReject"
        ></textarea>
        <div class="dialog-char-count">{{ rejectDialog.reason.length }}/200</div>
        <div class="dialog-actions">
          <button class="dialog-btn cancel-btn" @click="cancelReject">取消</button>
          <button
            class="dialog-btn confirm-btn"
            :disabled="rejectDialog.submitting"
            @click="confirmReject"
          >
            {{ rejectDialog.submitting ? '处理中...' : '确认驳回' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 管理指南 ==================== -->
    <div class="guide-card">
      <h3>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <path d="M12 16v-4M12 8h.01"/>
        </svg>
        管理操作指南
      </h3>
      <div class="guide-grid">
        <div class="guide-item">
          <h4>需求审核</h4>
          <p>用户发布的需求需经管理员审核后才会公开显示。点击需求卡片可查看详情，审核通过后需求进入公开列表。</p>
        </div>
        <div class="guide-item">
          <h4>用户管理</h4>
          <p>查看全部用户，按角色/关键词筛选，启用或禁用用户账号。</p>
        </div>
        <div class="guide-item">
          <h4>需求管理</h4>
          <p>查看全部需求，按状态筛选，强制删除违规需求。</p>
        </div>
        <div class="guide-item">
          <h4>聊天监控</h4>
          <p>查看平台所有群聊和私聊会话，浏览历史消息记录。</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import adminApi from '@/admin/adminApi'

const loading = ref(false)
const actionLoading = ref(false)
const stats = reactive({})
const pendingTasks = ref([])
const currentIndex = ref(0)

const rejectDialog = reactive({
  visible: false,
  reason: '',
  submitting: false
})

const currentTask = computed(() => {
  if (pendingTasks.value.length === 0) return null
  return pendingTasks.value[currentIndex.value] || null
})

onMounted(async () => {
  loading.value = true
  try {
    const [statsRes, tasksRes] = await Promise.all([
      adminApi.getStats(),
      adminApi.listTasks({ status: 'pending_review', page_size: 100 })
    ])
    Object.assign(stats, statsRes.data?.data || {})
    pendingTasks.value = tasksRes.data?.data?.items || []
  } finally {
    loading.value = false
  }
})

async function handleApprove() {
  if (!currentTask.value) return
  actionLoading.value = true
  try {
    await adminApi.approveTask(currentTask.value.id)
    advanceQueue()
  } finally {
    actionLoading.value = false
  }
}

function handleReject() {
  if (!currentTask.value) return
  rejectDialog.reason = ''
  rejectDialog.visible = true
}

function cancelReject() {
  rejectDialog.visible = false
  rejectDialog.reason = ''
}

async function confirmReject() {
  if (!currentTask.value) return
  rejectDialog.submitting = true
  try {
    await adminApi.rejectTask(currentTask.value.id, rejectDialog.reason || null)
    rejectDialog.visible = false
    rejectDialog.reason = ''
    advanceQueue()
  } finally {
    rejectDialog.submitting = false
  }
}

function advanceQueue() {
  pendingTasks.value.splice(currentIndex.value, 1)
  if (pendingTasks.value.length === 0) {
    currentIndex.value = 0
    // 刷新仪表盘数据
    refreshStats()
  } else if (currentIndex.value >= pendingTasks.value.length) {
    currentIndex.value = 0
  }
}

async function refreshStats() {
  try {
    const res = await adminApi.getStats()
    Object.assign(stats, res.data?.data || {})
  } catch {}
}

function truncate(text, max) {
  return text && text.length > max ? text.slice(0, max) + '...' : text || ''
}

function formatDate(d) {
  if (!d) return ''
  const date = new Date(d)
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.admin-dashboard {
  animation: fade-in 0.5s ease;
}

@keyframes fade-in {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ==================== 待审核需求区域 ==================== */
.review-section {
  margin-bottom: 40px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.section-title svg {
  width: 22px;
  height: 22px;
  color: var(--accent);
}

.queue-badge {
  font-size: 13px;
  font-weight: 600;
  color: var(--accent-dark);
  background: rgba(245, 158, 11, 0.1);
  padding: 4px 12px;
  border-radius: var(--radius-full);
}

/* 审核卡片 */
.review-card {
  background: var(--bg-secondary);
  border-radius: var(--radius-xl);
  overflow: hidden;
  box-shadow: var(--shadow-md);
  border: 1px solid var(--border-color);
  transition: all var(--transition-base);
}

.review-card:hover {
  box-shadow: var(--shadow-lg);
}

.card-accent {
  height: 4px;
  background: linear-gradient(90deg, var(--accent), var(--accent-light));
}

.card-body {
  padding: 20px 24px 24px;
}

/* 任务信息（可点击区域） */
.task-info {
  cursor: pointer;
  border-radius: var(--radius-lg);
  padding: 8px;
  margin: -8px -8px 0;
  transition: background var(--transition-fast);
}

.task-info:hover {
  background: rgba(16, 185, 129, 0.03);
}

/* 卡片头部 */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 600;
  background: rgba(245, 158, 11, 0.1);
  color: var(--accent-dark);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.8); }
}

.reward {
  font-size: 20px;
  font-weight: 700;
  color: var(--accent);
  display: flex;
  align-items: baseline;
}

.reward .currency {
  font-size: 13px;
  font-weight: 600;
  margin-right: 2px;
}

.reward.free {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-muted);
}

/* 标题 */
.card-title {
  margin: 0 0 10px;
  font-size: 17px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--text-primary);
}

/* 描述 */
.card-desc {
  margin: 0 0 14px;
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  min-height: 22px;
}

/* 详情行 */
.detail-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 8px;
}

.detail-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-muted);
}

.detail-item svg {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}

/* 发布者信息行 */
.extra-row {
  padding-top: 10px;
  border-top: 1px solid var(--border-color);
  margin-top: 4px;
}

.extra-item {
  font-size: 12px;
  color: var(--text-muted);
}

/* 操作按钮区 */
.action-bar {
  display: flex;
  gap: 12px;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--border-color);
}

.action-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 24px;
  border: none;
  border-radius: var(--radius-lg);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-base);
  color: white;
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.action-btn svg {
  width: 20px;
  height: 20px;
}

.approve-btn {
  background: linear-gradient(135deg, var(--success), #4ade80);
  box-shadow: 0 4px 14px rgba(34, 197, 94, 0.3);
}

.approve-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(34, 197, 94, 0.4);
}

.reject-btn {
  background: linear-gradient(135deg, var(--error), #f87171);
  box-shadow: 0 4px 14px rgba(239, 68, 68, 0.3);
}

.reject-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(239, 68, 68, 0.4);
}

/* 队列提示 */
.queue-hint {
  text-align: right;
  padding: 8px 24px 12px;
  font-size: 12px;
  color: var(--text-muted);
}

/* 无待审核 */
.review-empty {
  text-align: center;
  padding: 60px 24px;
  background: var(--bg-secondary);
  border-radius: var(--radius-xl);
  border: 1px solid var(--border-color);
}

.empty-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto 16px;
  background: rgba(34, 197, 94, 0.1);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--success);
}

.empty-icon svg {
  width: 32px;
  height: 32px;
}

.review-empty h3 {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.review-empty p {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

/* ==================== 快捷操作 ==================== */
.quick-actions {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 40px;
}

@media (max-width: 768px) {
  .quick-actions { grid-template-columns: 1fr; }
}

.action-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-xl);
  cursor: pointer;
  transition: all var(--transition-base);
  text-align: left;
}

.action-card:hover {
  border-color: var(--primary);
  box-shadow: var(--shadow-lg);
  transform: translateY(-2px);
}

.action-card:hover .action-arrow {
  transform: translateX(4px);
  color: var(--primary);
}

.action-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.action-icon.users { background: rgba(59, 130, 246, 0.1); color: var(--info); }
.action-icon.tasks { background: rgba(16, 185, 129, 0.1); color: var(--primary); }

.action-icon svg { width: 28px; height: 28px; }

.action-content { flex: 1; }
.action-content h3 { font-size: 16px; font-weight: 600; color: var(--text-primary); margin: 0 0 4px; }
.action-content p { font-size: 13px; color: var(--text-muted); margin: 0; }

.action-arrow {
  width: 24px; height: 24px;
  color: var(--text-muted);
  transition: all var(--transition-base);
}

/* ==================== 数据概览 ==================== */
.stats-section { margin-bottom: 40px; }

.stats-section .section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 20px;
}

.stats-section .section-title svg {
  width: 22px; height: 22px;
  color: var(--primary);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

@media (max-width: 1200px) { .stats-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 900px)  { .stats-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 600px)  { .stats-grid { grid-template-columns: 1fr; } }

.stat-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-xl);
  padding: 20px;
  display: flex;
  align-items: flex-start;
  gap: 16px;
  transition: all var(--transition-base);
}

.stat-card.clickable { cursor: pointer; }
.stat-card.clickable:hover {
  border-color: var(--primary);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.stat-card.highlight {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.05), rgba(52, 211, 153, 0.08));
  border-color: rgba(16, 185, 129, 0.2);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon svg { width: 24px; height: 24px; }

.stat-icon.users { background: rgba(59, 130, 246, 0.1); color: var(--info); }
.stat-icon.primary { background: rgba(16, 185, 129, 0.1); color: var(--primary); }
.stat-icon.accent { background: rgba(245, 158, 11, 0.1); color: var(--accent); }
.stat-icon.success { background: rgba(34, 197, 94, 0.1); color: var(--success); }
.stat-icon.warning { background: rgba(239, 68, 68, 0.1); color: var(--error); }
.stat-icon.info { background: rgba(99, 102, 241, 0.1); color: #6366f1; }
.stat-icon.new { background: linear-gradient(135deg, var(--primary), var(--primary-light)); color: white; }
.stat-icon.muted { background: var(--bg-tertiary); color: var(--text-muted); }
.stat-icon.chats { background: rgba(59, 130, 246, 0.1); color: var(--info); }
.stat-icon.msgs { background: rgba(139, 92, 246, 0.1); color: #8b5cf6; }
.stat-icon.today { background: rgba(236, 72, 153, 0.1); color: #ec4899; }

.stat-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1;
}

.stat-label { font-size: 13px; color: var(--text-muted); }

.stat-trend {
  width: 24px; height: 24px;
  color: var(--success);
}

.stat-trend svg { width: 100%; height: 100%; }

/* ==================== 管理指南 ==================== */
.guide-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-xl);
  padding: 24px;
}

.guide-card h3 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 20px;
}

.guide-card h3 svg { width: 20px; height: 20px; color: var(--accent); }

.guide-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

@media (max-width: 600px) { .guide-grid { grid-template-columns: 1fr; } }

.guide-item {
  padding: 16px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-lg);
}

.guide-item h4 { font-size: 14px; font-weight: 600; color: var(--text-primary); margin: 0 0 8px; }
.guide-item p { font-size: 13px; color: var(--text-secondary); margin: 0; line-height: 1.6; }

/* ==================== 驳回弹窗 ==================== */
.dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(15, 23, 42, 0.5);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: overlay-in 0.2s ease;
}

@keyframes overlay-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

.dialog-panel {
  background: var(--bg-secondary);
  border-radius: var(--radius-xl);
  padding: 28px;
  width: 480px;
  max-width: 90vw;
  box-shadow: var(--shadow-xl);
  animation: panel-in 0.25s ease;
}

@keyframes panel-in {
  from { opacity: 0; transform: scale(0.95) translateY(10px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.dialog-title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.dialog-subtitle {
  margin: 0 0 16px;
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.dialog-subtitle strong {
  color: var(--text-primary);
}

.dialog-textarea {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  font-size: 14px;
  font-family: inherit;
  color: var(--text-primary);
  background: var(--bg-tertiary);
  resize: vertical;
  transition: border-color var(--transition-fast);
  outline: none;
}

.dialog-textarea:focus {
  border-color: var(--error);
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.1);
}

.dialog-textarea::placeholder {
  color: var(--text-muted);
}

.dialog-char-count {
  text-align: right;
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}

.dialog-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

.dialog-btn {
  flex: 1;
  padding: 12px 24px;
  border: none;
  border-radius: var(--radius-lg);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-base);
}

.cancel-btn {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}

.cancel-btn:hover {
  background: var(--border-color);
}

.confirm-btn {
  background: linear-gradient(135deg, var(--error), #f87171);
  color: white;
  box-shadow: 0 4px 14px rgba(239, 68, 68, 0.3);
}

.confirm-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(239, 68, 68, 0.4);
}

.confirm-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
