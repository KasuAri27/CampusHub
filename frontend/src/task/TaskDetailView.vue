<template>
  <div class="task-detail-wrapper">

    <div class="task-detail-page" v-loading="loading">
      <template v-if="task">
        <button class="back-btn" @click="$router.push('/')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
          返回列表
        </button>

        <div class="detail-container">
          <div class="detail-main">
            <div class="detail-header">
              <div class="header-top">
                <span class="status-badge" :class="statusClass">
                  <span class="status-dot"></span>
                  {{ statusLabel }}
                </span>
                <span class="category-badge">{{ task.categoryName }}</span>
              </div>
              <h1 class="detail-title">{{ task.title }}</h1>
              <div class="header-meta">
                <span class="meta-item">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
                  {{ formatTime(task.publishedAt) }}
                </span>
                <span class="meta-item">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0118 0z"/><circle cx="12" cy="10" r="3"/></svg>
                  {{ task.location || '不限地点' }}
                </span>
                <span class="meta-item" v-if="task.taskType === 'CONSULTING'">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/></svg>
                  {{ task.consultingMode === 'ONLINE' ? '线上咨询' : '线下咨询' }}
                </span>
                <span class="meta-item" v-if="task.deadline">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
                  </svg>
                  截止: {{ formatTime(task.deadline) }}
                </span>
              </div>
            </div>

            <div class="detail-card">
              <h3 class="card-section-title">需求详情</h3>
              <p class="description">{{ task.description }}</p>
            </div>
            <div class="detail-card review-display-card" v-if="reviewDetail && !isAutoReviewed">
              <h3 class="card-section-title">评价信息</h3>
              <div class="review-content">
                <div class="score-display">
                  <span class="label">服务评分：</span>
                  <el-rate v-model="reviewDetail.score" disabled show-score text-color="#ff9900" />
                </div>
                <div class="comment-display">
                  <p v-if="reviewDetail.comment" class="comment-text">"{{ reviewDetail.comment }}"</p>
                  <p v-else class="comment-text empty">该用户没有留下文字评价。</p>
                </div>
                <div class="review-time">
                  评价时间：{{ formatTime(reviewDetail.createdAt) }}
                </div>
              </div>
            </div>
          </div>

          <div class="detail-sidebar">
            <div class="reward-card" v-if="task.taskType !== 'TEAM'">
              <span class="reward-label">任务报酬</span>
              <div class="reward-amount" v-if="task.taskType === 'FLEA' && (task.tradeMode || 'SELL') === 'BUY'">
                <span class="amount">收物</span>
              </div>
              <div class="reward-amount" v-else-if="task.reward">
                <span class="currency">¥</span>
                <span class="amount">{{ task.reward }}</span>
              </div>
              <div class="reward-amount free" v-else>免费互助</div>
            </div>

            <div class="publisher-card" v-if="task.publisher">
              <h4>发布者</h4>
              <div class="publisher-info">
                <div class="publisher-avatar">
                  <span class="avatar-placeholder">{{ task.publisher.nickname?.charAt(0) || 'U' }}</span>
                </div>
                <div class="publisher-details">
                  <span class="publisher-name">{{ task.publisher.nickname }}</span>
                  <span class="publisher-meta">信用分 {{ task.publisher.creditScore ?? 100 }}</span>
                </div>
              </div>
            </div>

            <div class="action-buttons">
              <button
                  v-if="userStore.isProvider && task.status === 'published' && (!orderDetail || orderDetail.orderStatus === 'cancelled')"
                  class="accept-btn"
                  :loading="actionLoading"
                  @click="handleAccept"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 11.08V12a10 10 0 11-5.93-9.14"/>
                  <polyline points="22 4 12 14.01 9 11.01"/>
                </svg>
                立即接单
              </button>

              <button v-if="task.publisher?.userId === userStore.userId && task.status === 'published' && !orderDetail"
                class="cancel-btn" @click="handleCancel">取消需求</button>
              <OrderActions v-if="orderDetail" :order="orderDetail" @updated="refreshOrder" />
              <router-link v-if="task.taskType === 'TEAM' && task.publisher?.userId === userStore.userId" to="/my-orders">管理组队成员订单</router-link>
              <p v-if="userStore.isProvider && task.status === 'published' && (!orderDetail || orderDetail.orderStatus === 'cancelled')" class="action-hint">
                接单后订单进入进行中状态
              </p>
            </div>
          </div>
        </div>
      </template>
    </div>


  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import taskApi from '@/task/taskApi'
import orderApi from '@/order/orderApi'
import { useUserStore } from '@/user/userStore'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrderActions from '@/order/OrderActions.vue'
import reviewApi from '@/review/reviewApi'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const actionLoading = ref(false)
const task = ref(null)
const lastOrderId = ref(null)
const orderDetail = ref(null)
const reviewDetail = ref(null)

const statusMap = {
  pending_review: ['待审核', 'pending'],
  published: ['待接单', 'published'],
  in_progress: ['进行中', 'in-progress'],
  completed: ['已完成', 'completed'],
  cancelled: ['已取消', 'cancelled']
}

const statusLabel = computed(() => task.value ? (statusMap[task.value.status]?.[0] || task.value.status) : '')
const statusClass = computed(() => task.value ? (statusMap[task.value.status]?.[1] || 'default') : 'default')

function formatTime(d) {
  if (!d) return ''
  return new Date(d).toLocaleString('zh-CN', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

onMounted(async () => {
  try {
    const res = await taskApi.getById(route.params.id)
    task.value = res.data.data

    await refreshOrder()

  } finally {
    loading.value = false
  }
})

async function handleAccept() {
  // 拦截逻辑：判断信用分是否低于70
  if (userStore.creditScore < 70) {
    ElMessage.error('您的信用分低于70分，暂无接单权限！')
    return
  }

  executeAcceptApi()
}

async function executeAcceptApi() {
  actionLoading.value = true
  try {
    const res = await orderApi.accept(task.value.id)
    lastOrderId.value = res.data?.data?.orderId
    ElMessage.success('接单成功！需求已进入进行中')
    task.value.status = res.data.data.taskStatus
    await refreshOrder()
  } finally {
    actionLoading.value = false
  }
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm('确定要取消该需求吗？', '取消需求', { type: 'warning' })
    actionLoading.value = true
    await taskApi.deleteTask(task.value.id)
    ElMessage.success('已删除需求')
    router.push('/')
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  } finally {
    actionLoading.value = false
  }
}

// 从本人订单列表定位记录，不对无关用户发起订单详情请求，也不依赖页面角色。
async function refreshOrder() {
  if (!userStore.isLoggedIn) return
  const { data } = await orderApi.mine()
  orderDetail.value = data.data.find(order => order.taskId === Number(route.params.id)) || null
  if (orderDetail.value) {
    lastOrderId.value = orderDetail.value.orderId
    task.value.status = orderDetail.value.taskStatus
    if (orderDetail.value.reviewEligibleAt && orderDetail.value.hasReview)
      reviewDetail.value = (await reviewApi.getByOrderId(orderDetail.value.orderId)).data.data
  }
}
</script>

<style scoped>
.task-detail-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
  animation: fade-in 0.5s ease;
}

@keyframes fade-in {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: transparent;
  border: none;
  color: var(--text-secondary);
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  border-radius: var(--radius-full);
  transition: all var(--transition-base);
  margin-bottom: 20px;
}

.back-btn:hover {
  background: var(--bg-tertiary);
  color: var(--primary);
}

.back-btn svg {
  width: 18px;
  height: 18px;
}

.detail-container {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 24px;
  align-items: start;
}

@media (max-width: 900px) {
  .detail-container {
    grid-template-columns: 1fr;
  }
}

.detail-main {
  background: var(--bg-secondary);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  border: 1px solid var(--border-color);
  position: relative;
}

.detail-main::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, var(--primary), var(--primary-light));
}

.detail-header {
  padding: 32px;
  border-bottom: 1px solid var(--border-color);
}

.header-top {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: var(--radius-full);
  font-size: 13px;
  font-weight: 600;
  background: rgba(16, 185, 129, 0.1);
  color: var(--primary-dark);
}
.status-badge.in-progress { background: rgba(59, 130, 246, 0.1); color: #2563eb; }
.status-badge.completed { background: #f1f5f9; color: #64748b; }
.status-badge.cancelled { background: var(--bg-tertiary); color: var(--text-muted); }
.status-badge.pending { background: rgba(245, 158, 11, 0.1); color: var(--accent-dark); }

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.category-badge {
  padding: 4px 12px;
  border-radius: var(--radius-full);
  font-size: 13px;
  font-weight: 500;
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}

.detail-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 16px;
  line-height: 1.4;
}

.header-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--text-muted);
}
.meta-item svg { width: 16px; height: 16px; }

.detail-card {
  padding: 32px;
}

.card-section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 16px;
}

.description {
  font-size: 15px;
  line-height: 1.8;
  color: var(--text-secondary);
  white-space: pre-wrap;
  margin: 0;
}

.detail-sidebar {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.reward-card, .publisher-card {
  background: var(--bg-secondary);
  border-radius: var(--radius-lg);
  padding: 24px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-color);
}

.reward-label {
  display: block;
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.reward-amount {
  color: var(--accent);
  font-weight: 700;
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.reward-amount .currency { font-size: 20px; }
.reward-amount .amount { font-size: 36px; line-height: 1; }
.reward-amount.free { font-size: 24px; color: var(--text-muted); }

.publisher-card h4 {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 16px;
  color: var(--text-primary);
}

.publisher-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.publisher-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--primary), var(--primary-light));
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 20px;
  font-weight: 600;
  flex-shrink: 0;
}

.publisher-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.publisher-name { font-size: 16px; font-weight: 600; color: var(--text-primary); }
.publisher-meta { font-size: 13px; color: var(--text-muted); }

.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.action-buttons button {
  width: 100%;
  padding: 14px;
  border-radius: var(--radius-full);
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: all var(--transition-base);
  border: none;
}

.action-buttons button svg { width: 20px; height: 20px; }

.accept-btn {
  background: linear-gradient(135deg, var(--primary), var(--primary-light));
  color: white;
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.25);
}
.accept-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(16, 185, 129, 0.35);
}

.complete-btn { background: var(--success); color: white; }
.complete-btn:hover { background: #15803d; }
.review-btn { background: var(--primary); color: white; }
.review-btn:hover { opacity: 0.9; }

.cancel-btn {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}
.cancel-btn:hover {
  background: #fee2e2;
  color: #ef4444;
}

.complaint-btn {
  background: transparent;
  border: 1px solid #f59e0b;
  color: #f59e0b;
  padding: 10px 18px;
  border-radius: var(--radius-md);
  font-size: 13px;
  cursor: pointer;
  transition: all var(--transition-base);
  margin-top: 8px;
}
.complaint-btn:hover { background: #fef3c7; }

.action-hint {
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
  margin: 0;
}

/* 新增：评价展示区样式 */
.review-display-card {
  border-top: 1px solid var(--border-color);
  background-color: rgba(245, 158, 11, 0.02);
}

.review-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.score-display {
  display: flex;
  align-items: center;
  gap: 8px;
}

.score-display .label {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.comment-display {
  background: var(--bg-primary);
  padding: 16px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
}

.comment-text {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.comment-text.empty {
  color: var(--text-muted);
  font-style: normal;
}

.review-time {
  font-size: 12px;
  color: var(--text-muted);
  text-align: right;
}
</style>
