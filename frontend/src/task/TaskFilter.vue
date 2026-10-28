<template>
  <div class="filter-bar">
    <div class="filter-inner">
      <!-- 分类筛选 -->
      <div class="filter-item">
        <label class="filter-label">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20.59 13.41l-7.17 7.17a2 2 0 01-2.83 0L2 12V2h10l8.59 8.59a2 2 0 010 2.82z"/>
            <line x1="7" y1="7" x2="7.01" y2="7"/>
          </svg>
          分类
        </label>
        <el-select v-model="filters.categoryId" placeholder="全部分类" clearable @change="emitFilter" class="custom-select">
          <el-option
            v-for="cat in categories"
            :key="cat.id"
            :label="cat.name"
            :value="cat.id"
          />
        </el-select>
      </div>

      <!-- 校区筛选 -->
      <div class="filter-item">
        <label class="filter-label">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0118 0z"/>
            <circle cx="12" cy="10" r="3"/>
          </svg>
          校区
        </label>
        <el-select v-model="filters.campus" placeholder="全部校区" clearable @change="emitFilter" class="custom-select">
          <el-option label="鼓楼校区" value="鼓楼校区" />
          <el-option label="仙林校区" value="仙林校区" />
          <el-option label="浦口校区" value="浦口校区" />
          <el-option label="苏州校区" value="苏州校区" />
        </el-select>
      </div>

      <!-- 状态筛选 -->
      <div class="filter-item">
        <label class="filter-label">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <polyline points="12 6 12 12 16 14"/>
          </svg>
          状态
        </label>
        <el-select v-model="filters.status" placeholder="全部状态" clearable @change="emitFilter" class="custom-select">
          <el-option v-if="userStore.isAdmin" label="待审核" value="pending_review" />
          <el-option label="待接单" value="published" />
          <el-option label="进行中" value="in_progress" />
          <el-option label="已完成" value="completed" />
        </el-select>
      </div>

      <!-- 关键词搜索 -->
      <div class="filter-item search-item">
        <label class="filter-label">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/>
            <path d="M21 21l-4.35-4.35"/>
          </svg>
          搜索
        </label>
        <div class="search-wrapper">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索需求标题..."
            clearable
            @keyup.enter="emitFilter"
            @clear="emitFilter"
            class="search-input"
          />
          <button class="search-btn" @click="emitFilter">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"/>
              <path d="M21 21l-4.35-4.35"/>
            </svg>
          </button>
        </div>
      </div>

      <!-- 排序 -->
      <div class="filter-item">
        <label class="filter-label">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="4" y1="9" x2="20" y2="9"/>
            <line x1="4" y1="15" x2="14" y2="15"/>
            <line x1="4" y1="21" x2="8" y2="21"/>
          </svg>
          排序
        </label>
        <el-select v-model="filters.sort" @change="emitFilter" class="custom-select">
          <el-option label="最新发布" value="latest" />
          <el-option label="报酬最高" value="reward" />
          <el-option label="最近截止" value="deadline_asc" />
        </el-select>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import http from '@/common/http'
import { useUserStore } from '@/user/userStore'

const emit = defineEmits(['filter-change'])
const userStore = useUserStore()

const filters = reactive({
  categoryId: null,
  campus: '',
  status: '',
  keyword: '',
  sort: 'latest'
})

const categories = reactive([])

onMounted(async () => {
  try {
    const res = await http.get('/categories')
    categories.push(...res.data.data)
  } catch {
    categories.push(
      { id: 1, name: '跑腿服务' },
      { id: 2, name: '学习辅导' },
      { id: 3, name: '二手交易' },
      { id: 4, name: '组队匹配' },
        { id: 5, name: '信息咨询' }
    )
  }
})

function emitFilter() {
  emit('filter-change', { ...filters })
}
</script>

<style scoped>
.filter-bar {
  background: var(--bg-secondary);
  border-radius: var(--radius-xl);
  padding: 20px 24px;
  margin-bottom: 24px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-color);
}

.filter-inner {
  display: flex;
  align-items: flex-end;
  gap: 20px;
  flex-wrap: wrap;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-width: 140px;
}

.filter-item.search-item {
  flex: 2;
  min-width: 200px;
}

.filter-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.filter-label svg {
  width: 14px;
  height: 14px;
  color: var(--text-muted);
}

.custom-select {
  width: 100%;
}

.search-wrapper {
  display: flex;
  gap: 8px;
}

.search-input {
  flex: 1;
}

.search-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--primary), var(--primary-light));
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-base);
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.25);
  flex-shrink: 0;
}

.search-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 16px rgba(16, 185, 129, 0.35);
}

.search-btn:active {
  transform: scale(0.95);
}

.search-btn svg {
  width: 18px;
  height: 18px;
}

/* 响应式 */
@media (max-width: 768px) {
  .filter-bar {
    padding: 16px;
  }
  
  .filter-inner {
    flex-direction: column;
    gap: 16px;
  }
  
  .filter-item {
    width: 100%;
    min-width: 100%;
  }
  
  .filter-item.search-item {
    min-width: 100%;
  }
}
</style>
