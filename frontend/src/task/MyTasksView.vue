<template>
  <div class="my-tasks-page">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">我的需求</h1>
        <p class="page-subtitle">我发布的和我接单的需求都在这里</p>
      </div>

      <div class="custom-tabs">
        <button
          v-for="item in tabs"
          :key="item.key"
          class="tab-btn"
          :class="{ active: tab === item.key }"
          @click="tab = item.key"
        >
          {{ item.label }}
          <span class="tab-count" v-if="lists[item.key].length">{{ lists[item.key].length }}</span>
        </button>
      </div>
    </div>

    <div v-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
      <p>正在加载...</p>
    </div>

    <div v-else-if="filtered.length === 0" class="empty-state">
      <div class="empty-icon">☆</div>
      <h3>{{ emptyText[tab][0] }}</h3>
      <p>{{ emptyText[tab][1] }}</p>
      <router-link v-if="tab === 'published' && !userStore.isAdmin" to="/tasks/publish" class="empty-action">
        去发布一个需求
      </router-link>
    </div>

    <TransitionGroup v-else name="card-list" tag="div" class="task-grid">
      <TaskCard v-for="(t, index) in filtered" :key="t.cardKey" :task="t" :style="{ '--delay': index * 0.05 + 's' }" />
    </TransitionGroup>
  </div>
</template>

<script setup>
/**
 * 我的需求：同一账号既能发布也能接单，这里把两边合在一起统计。
 * 数据来源：
 *   - 我发布的：GET /tasks/mine/published（全部状态）
 *   - 我接单的：GET /orders/my-accepted（按真实订单，不再从广场列表推断）
 * 接单记录展示时以“我这张订单”的状态为准：组队需求未满员时需求仍是待接单，
 * 但对已加入的成员来说是进行中。
 */
import { ref, computed, onMounted } from 'vue'
import taskApi from '@/task/taskApi'
import orderApi from '@/order/orderApi'
import TaskCard from '@/task/TaskCard.vue'
import { useUserStore } from '@/user/userStore'

const userStore = useUserStore()
const tab = ref('published')
const loading = ref(false)
const publishedTasks = ref([])
const acceptedTasks = ref([])

const tabs = [
  { key: 'published', label: '我发布的' },
  { key: 'accepted', label: '我接单的' },
  { key: 'waiting', label: '待接单' },
  { key: 'inProgress', label: '进行中' },
  { key: 'completed', label: '已完成' }
]

const emptyText = {
  published: ['你还没有发布过需求', '先去发布一个需求吧，方便同学帮助你完成任务。'],
  accepted: ['你还没有接过单', '可以去需求广场看看有没有合适的任务。'],
  waiting: ['没有等待接单的需求', '你发布且已通过审核、还没人接的需求会显示在这里。'],
  inProgress: ['没有进行中的需求', '你发布的或接下的、正在进行的需求会显示在这里。'],
  completed: ['暂无已完成的需求', '你发布的或接下的已完成需求都会显示在这里。']
}

/** 订单履约状态折算成卡片状态：待验收仍属于进行中。 */
const ORDER_TO_CARD_STATUS = {
  in_progress: 'in_progress',
  awaiting_acceptance: 'in_progress',
  completed: 'completed',
  cancelled: 'cancelled'
}

const lists = computed(() => {
  const mine = publishedTasks.value
  const taken = acceptedTasks.value
  return {
    published: mine,
    accepted: taken,
    // 未被接单：已过审核、正在广场等人接的需求（待审核的只在“我发布的”里出现）
    waiting: mine.filter(t => t.status === 'published'),
    inProgress: [...mine.filter(t => t.status === 'in_progress'), ...taken.filter(t => t.status === 'in_progress')],
    completed: [...mine.filter(t => t.status === 'completed'), ...taken.filter(t => t.status === 'completed')]
  }
})

const filtered = computed(() => lists.value[tab.value])

/** 把我接的订单转成卡片数据：卡片需要发布者、分类等需求信息，按需求ID补查详情。 */
async function loadAcceptedTasks() {
  const res = await orderApi.myAccepted()
  const orders = res.data?.data || []
  const taskIds = [...new Set(orders.map(o => o.taskId))]
  const details = await Promise.allSettled(taskIds.map(id => taskApi.getById(id)))
  const taskById = new Map()
  details.forEach((r, i) => {
    if (r.status === 'fulfilled' && r.value.data?.data) taskById.set(taskIds[i], r.value.data.data)
  })
  return orders.map(o => ({
    // 详情查询失败时用订单里的标题等信息兜底，保证列表不缺项
    ...(taskById.get(o.taskId) || { id: o.taskId, title: o.title, description: o.description, taskType: o.taskType, reward: o.reward }),
    status: ORDER_TO_CARD_STATUS[o.orderStatus] || o.orderStatus,
    cardKey: `order-${o.orderId}`
  }))
}

async function fetchData() {
  loading.value = true
  try {
    const [mineRes, taken] = await Promise.all([
      taskApi.minePublished({ page: 1, page_size: 200 }),
      loadAcceptedTasks()
    ])
    publishedTasks.value = (mineRes.data?.data?.items || []).map(t => ({ ...t, cardKey: `task-${t.id}` }))
    acceptedTasks.value = taken
  } catch {
    publishedTasks.value = []
    acceptedTasks.value = []
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.my-tasks-page {
  animation: fade-in 0.5s ease;
}

@keyframes fade-in {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.page-header {
  margin-bottom: 32px;
}

.header-content {
  text-align: center;
  padding: 24px 0 32px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 8px;
}

.page-subtitle {
  font-size: 15px;
  color: var(--text-secondary);
  margin: 0;
}

.custom-tabs {
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-full);
  background: var(--bg-secondary);
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-base);
}

.tab-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.tab-btn.active {
  background: linear-gradient(135deg, var(--primary), var(--primary-light));
  border-color: transparent;
  color: white;
}

.tab-count {
  background: rgba(255, 255, 255, 0.2);
  padding: 2px 8px;
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 600;
}

.tab-btn:not(.active) .tab-count {
  background: var(--bg-tertiary);
  color: var(--text-muted);
}

.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  gap: 16px;
}

.loading-spinner {
  width: 48px;
  height: 48px;
  border: 3px solid var(--border-color);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.empty-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-tertiary);
  color: var(--text-muted);
  font-size: 28px;
}

.empty-state h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.empty-state p {
  font-size: 14px;
  color: var(--text-muted);
  margin: 0 0 8px;
}

.empty-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 12px 24px;
  border-radius: var(--radius-full);
  background: linear-gradient(135deg, var(--primary), var(--primary-light));
  color: #fff;
  text-decoration: none;
  font-weight: 600;
}

.task-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
}

@media (max-width: 1200px) {
  .task-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .task-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 600px) {
  .task-grid {
    grid-template-columns: 1fr;
  }
}

.card-list-enter-active {
  transition: all 0.4s ease;
  transition-delay: var(--delay, 0s);
}

.card-list-leave-active {
  transition: all 0.3s ease;
}

.card-list-enter-from,
.card-list-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}

.card-list-move {
  transition: transform 0.3s ease;
}
</style>
