<template>
  <div class="task-card" @click="$router.push(`/tasks/${task.id}`)">
    <!-- 顶部装饰条 -->
    <div class="card-accent" :class="statusClass"></div>
    
    <!-- 卡片内容 -->
    <div class="card-content">
      <!-- 顶部：状态标签 + 报酬 -->
      <div class="card-header">
        <span class="status-badge" :class="statusClass">
          <span class="status-dot"></span>
          {{ statusLabel }}
        </span>
        <span class="reward" v-if="task.taskType === 'FLEA' && (task.tradeMode || 'SELL') === 'BUY'">
          收物
        </span>
        <span class="reward" v-else-if="task.reward">
          <span class="currency">&#xFFE5;</span>{{ task.reward }}
        </span>
        <span class="reward free" v-else>免费互助</span>
      </div>
      
      <!-- 标题 -->
      <h4 class="card-title">{{ task.title }}</h4>
      
      <!-- 描述 -->
      <p class="card-desc">{{ truncate(task.description, 60) }}</p>
      
      <!-- 底部信息 -->
      <div class="card-footer">
        <div class="footer-left">
          <span class="category-tag">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20.59 13.41l-7.17 7.17a2 2 0 01-2.83 0L2 12V2h10l8.59 8.59a2 2 0 010 2.82z"/>
              <line x1="7" y1="7" x2="7.01" y2="7"/>
            </svg>
            {{ task.categoryName }}
          </span>
          <span class="campus-tag" v-if="task.campus">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0118 0z"/>
              <circle cx="12" cy="10" r="3"/>
            </svg>
            {{ task.campus }}
          </span>
          <span class="deadline-tag" v-if="task.deadline">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
            </svg>
            {{ formatDeadline(task.deadline) }}
          </span>
        </div>
        <div class="footer-right">
          <span class="publisher">{{ task.publisher?.nickname || '匿名' }}</span>
          <span class="time">{{ formatDate(task.publishedAt) }}</span>
        </div>
      </div>
    </div>
    
    <!-- 悬浮箭头 -->
    <div class="hover-arrow">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M5 12h14M12 5l7 7-7 7"/>
      </svg>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ task: { type: Object, required: true } })

const statusMap = {
  pending_review: ['待审核', 'pending'],
  published: ['待接单', 'published'],
  in_progress: ['进行中', 'in-progress'],
  completed: ['已完成', 'completed'],
  cancelled: ['已取消', 'cancelled']
}

const statusLabel = computed(() => statusMap[props.task.status]?.[0] || props.task.status)
const statusClass = computed(() => statusMap[props.task.status]?.[1] || 'default')

function truncate(text, max) { return text?.length > max ? text.slice(0, max) + '...' : text || '' }
function formatDate(d) { 
  if (!d) return ''
  const date = new Date(d)
  const now = new Date()
  const diff = now - date
  
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 604800000) return Math.floor(diff / 86400000) + '天前'
  
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

// 处理未来时间的格式化
function formatDeadline(d) {
  if (!d) return ''
  const date = new Date(d)
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.task-card {
  background: var(--bg-secondary);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  position: relative;
  transition: all var(--transition-base);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-color);
}

.task-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-xl);
  border-color: transparent;
}

.task-card:hover .hover-arrow {
  opacity: 1;
  transform: translate(0, -50%);
}

/* 顶部装饰条 */
.card-accent {
  height: 4px;
  background: linear-gradient(90deg, var(--primary), var(--primary-light));
}

.card-accent.in-progress {
  background: linear-gradient(90deg, #3b82f6, #60a5fa); /* 柔和的蓝色渐变 */
}

.card-accent.completed {
  background: #94a3b8; /* 灰色 */
}

.card-accent.cancelled {
  background: var(--text-muted);
}

.card-accent.pending {
  background: linear-gradient(90deg, #f59e0b, #fbbf24);
}

/* 卡片内容 */
.card-content {
  padding: 16px 18px 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 卡片头部 */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 500;
  background: rgba(16, 185, 129, 0.1);
  color: var(--primary-dark);
}

.status-badge.in-progress {
  background: rgba(59, 130, 246, 0.1); /* 浅蓝色背景 */
  color: #2563eb; /* 适中的蓝色文字 */
}

.status-badge.completed {
  background: #f1f5f9; /* 浅灰色背景 */
  color: #64748b; /* 灰色文字 */
}

.status-badge.cancelled {
  background: var(--bg-tertiary);
  color: var(--text-muted);
}

.status-badge.pending {
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

.status-badge.completed .status-dot,
.status-badge.cancelled .status-dot {
  animation: none;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.8); }
}

.reward {
  font-size: 18px;
  font-weight: 700;
  color: var(--accent);
  display: flex;
  align-items: baseline;
}

.reward .currency {
  font-size: 12px;
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
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--text-primary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 描述 */
.card-desc {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 42px;
}

/* 底部信息 */
.card-footer {
  display: flex;
  flex-direction: column; /* 改为纵向排列（两行） */
  gap: 10px; /* 上下两行的间距 */
  padding-top: 12px;
  border-top: 1px solid var(--border-color);
  margin-top: 4px;
}

.footer-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap; /* 如果标签很多，允许自动换行 */
  gap: 6px;
  width: 100%;
}

.category-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-full);
  font-size: 11px;
  font-weight: 500;
  color: var(--text-secondary);
}

.category-tag svg {
  width: 12px;
  height: 12px;
}

.campus-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: rgba(59, 130, 246, 0.08);
  border-radius: var(--radius-full);
  font-size: 11px;
  font-weight: 500;
  color: var(--info);
}

.campus-tag svg {
  width: 12px;
  height: 12px;
}

.footer-right {
  display: flex;
  align-items: center;
  justify-content: flex-end; /* 发布者和时间整体靠右对齐 */
  gap: 8px;
  font-size: 12px;
  color: var(--text-muted);
  width: 100%;
}

.publisher {
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.footer-right::before {
  display: none;
}

.time::before {
  content: '·';
  margin-right: 8px;
  color: var(--text-muted);
}

.deadline-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: rgba(239, 68, 68, 0.08); /* 浅红色警示 */
  border-radius: var(--radius-full);
  font-size: 11px;
  font-weight: 500;
  color: var(--error);
}
.deadline-tag svg {
  width: 12px;
  height: 12px;
}

/* 悬浮箭头 */
.hover-arrow {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translate(10px, -50%);
  width: 32px;
  height: 32px;
  background: var(--primary);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  opacity: 0;
  transition: all var(--transition-base);
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
}

.hover-arrow svg {
  width: 16px;
  height: 16px;
}
</style>
