<template>
  <div class="task-list-page">
    <!-- 页面标题区域 -->
    <div class="page-hero">
      <!-- 左侧装饰图案 -->
      <div class="hero-decoration hero-decoration-left">
        <svg viewBox="0 0 200 200" fill="none" xmlns="http://www.w3.org/2000/svg">
          <!-- 书本 -->
          <rect x="20" y="80" width="50" height="60" rx="4" fill="#E8F5E9" stroke="#1abc9c" stroke-width="2"/>
          <line x1="45" y1="80" x2="45" y2="140" stroke="#1abc9c" stroke-width="2"/>
          <line x1="30" y1="95" x2="40" y2="95" stroke="#1abc9c" stroke-width="1.5"/>
          <line x1="30" y1="105" x2="38" y2="105" stroke="#1abc9c" stroke-width="1.5"/>
          <line x1="30" y1="115" x2="42" y2="115" stroke="#1abc9c" stroke-width="1.5"/>
          <!-- 咖啡杯 -->
          <ellipse cx="130" cy="120" rx="25" ry="8" fill="#FFF3E0"/>
          <path d="M105 120 L110 155 Q130 165 150 155 L155 120" fill="#FFF3E0" stroke="#f39c12" stroke-width="2"/>
          <path d="M155 125 Q175 125 175 140 Q175 155 155 150" stroke="#f39c12" stroke-width="2" fill="none"/>
          <path d="M120 105 Q125 95 130 105 Q135 95 140 105" stroke="#f39c12" stroke-width="2" fill="none" opacity="0.6"/>
          <!-- 小圆点装饰 -->
          <circle cx="80" cy="60" r="6" fill="#1abc9c" opacity="0.3"/>
          <circle cx="160" cy="70" r="4" fill="#f39c12" opacity="0.4"/>
          <circle cx="100" cy="170" r="5" fill="#1abc9c" opacity="0.2"/>
        </svg>
      </div>
      
      <!-- 标题内容 -->
      <div class="hero-content">
        <h1 class="hero-title">
          <span class="title-char" style="--i:0">需</span>
          <span class="title-char" style="--i:1">求</span>
          <span class="title-char" style="--i:2">广</span>
          <span class="title-char" style="--i:3">场</span>
        </h1>
        <div class="title-underline"></div>
        <p class="hero-subtitle">发现校园里的互助机会，找到你需要的帮助</p>
      </div>
      
      <!-- 右侧装饰图案 -->
      <div class="hero-decoration hero-decoration-right">
        <svg viewBox="0 0 200 200" fill="none" xmlns="http://www.w3.org/2000/svg">
          <!-- 笔记本电脑 -->
          <rect x="60" y="70" width="80" height="50" rx="4" fill="#E3F2FD" stroke="#1abc9c" stroke-width="2"/>
          <rect x="70" y="80" width="60" height="30" rx="2" fill="#fff" stroke="#1abc9c" stroke-width="1"/>
          <path d="M50 120 L60 120 L60 125 L140 125 L140 120 L150 120 L145 135 L55 135 Z" fill="#E8F5E9" stroke="#1abc9c" stroke-width="2"/>
          <!-- 铅笔 -->
          <rect x="25" y="90" width="8" height="50" rx="1" fill="#FFF3E0" stroke="#f39c12" stroke-width="1.5" transform="rotate(-20 29 115)"/>
          <polygon points="29,145 25,155 33,155" fill="#f39c12" transform="rotate(-20 29 150)"/>
          <!-- 星星 -->
          <path d="M170 80 L173 90 L183 90 L175 97 L178 107 L170 100 L162 107 L165 97 L157 90 L167 90 Z" fill="#f39c12" opacity="0.6"/>
          <!-- 小圆点装饰 -->
          <circle cx="45" cy="70" r="5" fill="#1abc9c" opacity="0.3"/>
          <circle cx="175" cy="140" r="4" fill="#f39c12" opacity="0.4"/>
          <circle cx="100" cy="160" r="6" fill="#1abc9c" opacity="0.2"/>
        </svg>
      </div>
    </div>

    <!-- 筛选栏 -->
    <TaskFilter @filter-change="handleFilterChange" />

    <!-- 加载态 -->
    <div v-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
      <p>正在加载需求...</p>
    </div>

    <!-- 空状态 -->
    <div v-else-if="tasks.length === 0" class="empty-state">
      <div class="empty-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"/>
          <path d="M12 11v6M9 14h6"/>
        </svg>
      </div>
      <h3>暂无需求</h3>
      <p>当前没有符合条件的需求，试试调整筛选条件</p>
    </div>

    <!-- 需求卡片网格 -->
    <TransitionGroup v-else name="card-list" tag="div" class="task-grid">
      <TaskCard v-for="(task, index) in tasks" :key="task.id" :task="task" :style="{ '--delay': index * 0.05 + 's' }" />
    </TransitionGroup>

    <!-- 分页器 -->
    <div class="pagination-wrapper" v-if="pagination.total > pagination.pageSize">
      <Pagination
        :page="pagination.page"
        :page-size="pagination.pageSize"
        :total="pagination.total"
        @page-change="page => { pagination.page = page; fetchTasks() }"
        @size-change="size => { pagination.pageSize = size; pagination.page = 1; fetchTasks() }"
      />
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import taskApi from '@/task/taskApi'
import TaskFilter from '@/task/TaskFilter.vue'
import TaskCard from '@/task/TaskCard.vue'
import Pagination from '@/common/ui/Pagination.vue'

const loading = ref(false)
const tasks = reactive([])
const filters = reactive({})
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

async function fetchTasks() {
  loading.value = true
  try {
    const params = {
      page: pagination.page, page_size: pagination.pageSize,
      sort: filters.sort || 'latest',
      ...(filters.categoryId && { categoryId: filters.categoryId }),
      ...(filters.status && { status: filters.status }),
      ...(filters.campus && { campus: filters.campus }),
      ...(filters.keyword && { keyword: filters.keyword })
    }
    const res = await taskApi.list(params)
    tasks.splice(0, tasks.length, ...(res.data?.data?.items || []))
    pagination.total = res.data?.data?.pagination?.total || 0
  } catch {
    tasks.splice(0, tasks.length)
  } finally {
    loading.value = false
  }
}

function handleFilterChange(f) {
  Object.assign(filters, f)
  pagination.page = 1
  fetchTasks()
}

onMounted(fetchTasks)
</script>

<style scoped>
.task-list-page {
  animation: fade-in 0.5s ease;
}

@keyframes fade-in {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 页面标题区域 */
.page-hero {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px 48px;
  position: relative;
  gap: 24px;
}

.hero-content {
  text-align: center;
  z-index: 1;
}

.hero-title {
  font-size: 42px;
  font-weight: 800;
  margin: 0 0 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  letter-spacing: 8px;
}

.title-char {
  display: inline-block;
  background: linear-gradient(135deg, #1abc9c 0%, #16a085 50%, #f39c12 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: char-float 3s ease-in-out infinite;
  animation-delay: calc(var(--i) * 0.15s);
  text-shadow: 0 4px 12px rgba(26, 188, 156, 0.15);
  position: relative;
}

.title-char::after {
  content: attr(data-char);
  position: absolute;
  left: 0;
  top: 0;
  z-index: -1;
  background: linear-gradient(135deg, #1abc9c 0%, #16a085 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  filter: blur(8px);
  opacity: 0.4;
}

@keyframes char-float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-6px);
  }
}

.title-underline {
  width: 80px;
  height: 4px;
  background: linear-gradient(90deg, transparent, #1abc9c, #f39c12, transparent);
  margin: 0 auto 16px;
  border-radius: 2px;
  animation: underline-glow 2s ease-in-out infinite alternate;
}

@keyframes underline-glow {
  from {
    opacity: 0.6;
    width: 80px;
  }
  to {
    opacity: 1;
    width: 120px;
  }
}

.hero-subtitle {
  font-size: 16px;
  color: var(--text-secondary);
  margin: 0;
  font-weight: 400;
}

/* 装饰图案 */
.hero-decoration {
  width: 160px;
  height: 160px;
  flex-shrink: 0;
  opacity: 0.9;
  transition: transform 0.3s ease, opacity 0.3s ease;
}

.hero-decoration:hover {
  transform: scale(1.05);
  opacity: 1;
}

.hero-decoration svg {
  width: 100%;
  height: 100%;
}

.hero-decoration-left {
  animation: float-left 4s ease-in-out infinite;
}

.hero-decoration-right {
  animation: float-right 4s ease-in-out infinite;
}

@keyframes float-left {
  0%, 100% {
    transform: translateY(0) rotate(-2deg);
  }
  50% {
    transform: translateY(-10px) rotate(2deg);
  }
}

@keyframes float-right {
  0%, 100% {
    transform: translateY(-5px) rotate(2deg);
  }
  50% {
    transform: translateY(5px) rotate(-2deg);
  }
}

@media (max-width: 768px) {
  .hero-decoration {
    display: none;
  }
  
  .hero-title {
    font-size: 32px;
    letter-spacing: 4px;
  }
}

/* 加载状态 */
.loading-state {
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
  to { transform: rotate(360deg); }
}

.loading-state p {
  color: var(--text-muted);
  font-size: 14px;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  padding: 48px 24px;
  text-align: center;
}

.empty-icon {
  width: 80px;
  height: 80px;
  background: var(--bg-tertiary);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
}

.empty-icon svg {
  width: 40px;
  height: 40px;
  color: var(--text-muted);
}

.empty-state h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 8px;
}

.empty-state p {
  font-size: 14px;
  color: var(--text-muted);
  margin: 0;
}

/* 卡片网格 */
.task-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

@media (max-width: 1200px) { .task-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 900px)  { .task-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 600px)  { .task-grid { grid-template-columns: 1fr; } }

/* 卡片列表动画 */
.card-list-enter-active {
  transition: all 0.4s ease;
  transition-delay: var(--delay, 0s);
}

.card-list-leave-active {
  transition: all 0.3s ease;
}

.card-list-enter-from {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}

.card-list-leave-to {
  opacity: 0;
  transform: scale(0.95);
}

.card-list-move {
  transition: transform 0.3s ease;
}

/* 分页器包装 */
.pagination-wrapper {
  margin-top: 40px;
  display: flex;
  justify-content: center;
}
</style>
