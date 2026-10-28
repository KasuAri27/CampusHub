<template>
  <div class="task-publish-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <button class="back-btn" @click="$router.push('/')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M19 12H5M12 19l-7-7 7-7"/>
        </svg>
        返回
      </button>
      <div class="header-content">
        <h1 class="page-title">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="8" x2="12" y2="16"/>
            <line x1="8" y1="12" x2="16" y2="12"/>
          </svg>
          发布新需求
        </h1>
        <p class="page-subtitle">选择需求类型，填写详情，让更多同学帮助你</p>
      </div>
    </div>

    <!-- 类型选择卡片 -->
    <div class="type-select-section" v-if="!selectedType">
      <div class="type-grid">
        <div
          v-for="item in taskTypes"
          :key="item.type"
          class="type-card"
          :class="{ 'type-card--active': selectedType === item.type }"
          @click="selectType(item.type)"
        >
          <div class="type-card-icon">{{ item.icon }}</div>
          <div class="type-card-name">{{ item.name }}</div>
          <div class="type-card-desc">{{ item.desc }}</div>
          <div class="type-card-arrow">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 18l6-6-6-6"/>
            </svg>
          </div>
        </div>
      </div>
    </div>

    <!-- 选中类型后的表单区域 -->
    <div v-if="selectedType" class="form-area">
      <!-- 已选类型标签 + 切换按钮 -->
      <div class="selected-type-bar">
        <div class="selected-type-info">
          <span class="selected-type-icon">{{ selectedTypeMeta.icon }}</span>
          <span class="selected-type-name">{{ selectedTypeMeta.name }}</span>
        </div>
        <button class="switch-type-btn" @click="selectedType = null">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M11 17l-5-5m0 0l5-5m-5 5h12"/>
          </svg>
          重新选择类型
        </button>
      </div>

      <!-- 表单卡片 -->
      <div class="form-card">
        <TaskForm :task-type="selectedType" @submitted="onSubmitSuccess" />
      </div>
    </div>

    <!-- 提示信息 -->
    <div class="tips-card" v-if="!selectedType">
      <h4>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <path d="M12 16v-4M12 8h.01"/>
        </svg>
        发布提示
      </h4>
      <ul>
        <li>标题要简洁明了，让别人一眼就能理解你的需求</li>
        <li>详细描述需求内容，包括具体要求和注意事项</li>
        <li>合理设置报酬，免费互助也欢迎</li>
        <li>设置合理的截止时间，给接单者足够的时间完成</li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import TaskForm from '@/task/TaskForm.vue'
import { useUserStore } from '@/user/userStore'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const selectedType = ref(null)

const taskTypes = [
  {
    type: 'RUNNING',
    icon: '🏃‍♀️',
    name: '跑腿服务',
    desc: '取快递、代买物品、送文件等跑腿需求'
  },
  {
    type: 'TUTORING',
    icon: '📚',
    name: '学习辅导',
    desc: '找学霸辅导功课、答疑解惑'
  },
  {
    type: 'FLEA',
    icon: '🛍️',
    name: '二手交易',
    desc: '闲置物品买卖、以物易物'
  },
  {
    type: 'TEAM',
    icon: '👥',
    name: '活动组队',
    desc: '组队开黑、运动搭子、活动招募'
  },
  {
    type: 'CONSULTING',
    icon: '💡',
    name: '信息咨询',
    desc: '经验分享、专业咨询、校园生活答疑'
  }
]

const selectedTypeMeta = computed(() => {
  return taskTypes.find(t => t.type === selectedType.value) || {}
})

function selectType(type) {
  // 拦截逻辑：如果信用分严格小于70，则弹窗提示并阻止进入表单
  if (userStore.creditScore < 70) {
    ElMessage.error('您的信用分低于70分，暂无发布需求权限！')
    return
  }

  selectedType.value = type
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function onSubmitSuccess(taskId) {
  if (taskId) {
    router.push(`/tasks/${taskId}`)
  } else {
    router.push('/')
  }
}
</script>

<style scoped>
.task-publish-page {
  max-width: 800px;
  margin: 0 auto;
  animation: fade-in 0.5s ease;
}

@keyframes fade-in {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 页面头部 */
.page-header { margin-bottom: 24px; }

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-full);
  background: var(--bg-secondary);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-base);
  margin-bottom: 20px;
}

.back-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.back-btn svg { width: 16px; height: 16px; }

.header-content {
  text-align: center;
  padding: 20px 0;
}

.page-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 8px;
}

.page-title svg {
  width: 32px;
  height: 32px;
  color: var(--primary);
}

.page-subtitle {
  font-size: 15px;
  color: var(--text-secondary);
  margin: 0;
}

/* 类型选择网格 */
.type-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

@media (max-width: 560px) {
  .type-grid {
    grid-template-columns: 1fr;
  }
}

.type-card {
  position: relative;
  padding: 28px 24px;
  background: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--radius-xl);
  cursor: pointer;
  transition: all var(--transition-base);
  box-shadow: var(--shadow-sm);
}

.type-card:hover {
  border-color: var(--primary-light);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.type-card:active {
  transform: translateY(0);
}

.type-card-icon {
  font-size: 36px;
  margin-bottom: 12px;
  transition: transform var(--transition-base);
}

.type-card:hover .type-card-icon {
  transform: scale(1.1);
}

.type-card-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.type-card-desc {
  font-size: 13px;
  color: var(--text-muted);
  line-height: 1.5;
}

.type-card-arrow {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%) translateX(0);
  width: 20px;
  height: 20px;
  color: var(--text-muted);
  opacity: 0;
  transition: all var(--transition-base);
}

.type-card:hover .type-card-arrow {
  opacity: 1;
  transform: translateY(-50%) translateX(4px);
}

.type-card-arrow svg {
  width: 20px;
  height: 20px;
}

/* 表单区域 */
.form-area {
  animation: fade-in 0.4s ease;
}

.selected-type-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-xl);
  margin-bottom: 20px;
  box-shadow: var(--shadow-sm);
}

.selected-type-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.selected-type-icon { font-size: 24px; }

.selected-type-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.switch-type-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-full);
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-base);
}

.switch-type-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.switch-type-btn svg {
  width: 14px;
  height: 14px;
}

/* 表单卡片 */
.form-card {
  background: var(--bg-secondary);
  border-radius: var(--radius-xl);
  padding: 32px;
  border: 1px solid var(--border-color);
  box-shadow: var(--shadow-sm);
}

/* 提示卡片 */
.tips-card {
  background: rgba(16, 185, 129, 0.05);
  border: 1px solid rgba(16, 185, 129, 0.15);
  border-radius: var(--radius-xl);
  padding: 20px 24px;
  margin-top: 20px;
}

.tips-card h4 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--primary-dark);
  margin: 0 0 12px;
}

.tips-card h4 svg { width: 18px; height: 18px; }

.tips-card ul { margin: 0; padding-left: 20px; }

.tips-card li {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.8;
}
</style>
