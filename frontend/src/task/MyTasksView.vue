<template>
  <div class="my-tasks-page">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">我的需求</h1>
        <p class="page-subtitle">管理你发布和接收的所有需求</p>
      </div>

      <div class="custom-tabs">
        <button
          v-if="userStore.isRequester"
          class="tab-btn"
          :class="{ active: tab === 'myPublished' }"
          @click="switchTab('myPublished')"
        >
          我发布的
          <span class="tab-count" v-if="published.length">{{ published.length }}</span>
        </button>
        <button
          v-if="userStore.isProvider"
          class="tab-btn"
          :class="{ active: tab === 'myAccepted' }"
          @click="switchTab('myAccepted')"
        >
          我接单的
          <span class="tab-count" v-if="myAccepted.length">{{ myAccepted.length }}</span>
        </button>
        <button
          class="tab-btn"
          :class="{ active: tab === 'accepted' }"
          @click="switchTab('accepted')"
        >
          已接单的
          <span class="tab-count" v-if="acceptedCount">{{ acceptedCount }}</span>
        </button>
        <button
          class="tab-btn"
          :class="{ active: tab === 'completed' }"
          @click="switchTab('completed')"
        >
          已完成的
          <span class="tab-count" v-if="completed.length">{{ completed.length }}</span>
        </button>
      </div>
    </div>

    <div v-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
      <p>正在加载...</p>
    </div>

    <div v-else-if="filtered.length === 0" class="empty-state">
      <div class="empty-icon">☆</div>
      <h3>{{ emptyDesc }}</h3>
      <p>{{ emptyHint }}</p>
      <router-link v-if="tab === 'myPublished'" to="/tasks/publish" class="empty-action">
        去发布一个需求
      </router-link>
    </div>

    <TransitionGroup v-else name="card-list" tag="div" class="task-grid">
      <TaskCard v-for="(t, index) in filtered" :key="t.id" :task="t" :style="{ '--delay': index * 0.05 + 's' }" />
    </TransitionGroup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import taskApi from '@/task/taskApi'
import TaskCard from '@/task/TaskCard.vue'
import { useUserStore } from '@/user/userStore'

const userStore = useUserStore()
const tab = ref(userStore.isRequester ? 'myPublished' : 'myAccepted')
const loading = ref(false)
const publicTasks = ref([])
const publishedTasks = ref([])

function sameUserId(a, b) {
  if (a === null || a === undefined || b === null || b === undefined) return false
  return Number(a) === Number(b)
}

function switchTab(nextTab) {
  tab.value = nextTab
  fetchData()
}

const published = computed(() => {
  if (userStore.isRequester) return publishedTasks.value
  return publicTasks.value.filter(t => sameUserId(t.publisher?.userId, userStore.userId))
})

const accepted = computed(() => {
  if (userStore.isRequester) return published.value.filter(t => t.status === 'in_progress')
  return publicTasks.value.filter(t => t.status === 'in_progress' && !sameUserId(t.publisher?.userId, userStore.userId))
})

const myAccepted = computed(() => {
  return publicTasks.value.filter(t => t.status === 'in_progress' && !sameUserId(t.publisher?.userId, userStore.userId))
})

const completed = computed(() => {
  if (userStore.isRequester) return published.value.filter(t => t.status === 'completed')
  return publicTasks.value.filter(t => t.status === 'completed' && !sameUserId(t.publisher?.userId, userStore.userId))
})

const acceptedCount = computed(() => accepted.value.length)

const filtered = computed(() => {
  if (tab.value === 'myPublished') return published.value
  if (tab.value === 'myAccepted') return myAccepted.value
  if (tab.value === 'accepted') return accepted.value
  return completed.value
})

const emptyDesc = computed(() => {
  const map = {
    myPublished: '你还没有发布过需求',
    myAccepted: '你还没有接过单',
    accepted: '暂无已接单的需求',
    completed: '暂无已完成的需求'
  }
  return map[tab.value] || '暂无数据'
})

const emptyHint = computed(() => {
  const map = {
    myPublished: '先去发布一个需求吧，方便同学帮助你完成任务。',
    myAccepted: '可以去需求广场看看有没有合适的任务。',
    accepted: '这里会显示当前已接单的需求。',
    completed: '完成后的需求会显示在这里。'
  }
  return map[tab.value] || ''
})

async function fetchData() {
  loading.value = true
  try {
    const [listRes, mineRes] = await Promise.all([
      taskApi.list({ page: 1, page_size: 200, sort: 'latest' }),
      userStore.isRequester
        ? taskApi.minePublished({ page: 1, page_size: 200 })
        : Promise.resolve(null)
    ])

    publicTasks.value = listRes.data?.data?.items || []
    publishedTasks.value = mineRes?.data?.data?.items || []
  } catch {
    publicTasks.value = []
    publishedTasks.value = []
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
