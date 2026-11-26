<template>
  <div class="square page">
    <!-- 导视牌：标题、搜索与校区选择 -->
    <section class="sign" aria-label="需求广场筛选">
      <div class="sign-head">
        <div>
          <h1 class="sign-title">需求广场</h1>
          <p class="sign-desc">同学们发布的跑腿、辅导、二手、组队与咨询需求，挑一单接下，或者发布你自己的需求。</p>
        </div>
        <form class="search" role="search" @submit.prevent="applySearch">
          <label class="visually-hidden" for="task-search">搜索需求</label>
          <input id="task-search" v-model.trim="keywordInput" type="search" placeholder="搜索标题或描述" maxlength="50" />
          <button type="submit">搜索</button>
        </form>
      </div>
      <div class="plates" role="radiogroup" aria-label="校区">
        <button
          v-for="c in campusOptions"
          :key="c.value"
          type="button"
          role="radio"
          class="plate"
          :class="{ on: filters.campus === c.value }"
          :aria-checked="filters.campus === c.value"
          @click="update({ campus: c.value })"
        >{{ c.label }}</button>
      </div>
    </section>

    <!-- 分类、状态、排序 -->
    <div class="toolbar">
      <div class="chips" role="radiogroup" aria-label="分类">
        <button
          type="button"
          role="radio"
          class="chip"
          :class="{ on: !filters.categoryId }"
          :aria-checked="!filters.categoryId"
          @click="update({ categoryId: '' })"
        >全部分类</button>
        <button
          v-for="cat in categories"
          :key="cat.id"
          type="button"
          role="radio"
          class="chip"
          :class="{ on: filters.categoryId === String(cat.id) }"
          :aria-checked="filters.categoryId === String(cat.id)"
          @click="update({ categoryId: String(cat.id) })"
        >{{ cat.name }}</button>
      </div>
      <div class="selects">
        <el-select :model-value="filters.status" size="default" aria-label="状态" @update:model-value="v => update({ status: v })">
          <el-option v-if="userStore.isAdmin" label="待审核" value="pending_review" />
          <el-option v-if="userStore.isAdmin" label="被驳回" value="rejected" />
          <el-option label="待接单" value="published" />
          <el-option label="进行中" value="in_progress" />
          <el-option label="已完成" value="completed" />
        </el-select>
        <el-select :model-value="filters.sort" size="default" aria-label="排序" @update:model-value="v => update({ sort: v })">
          <el-option label="最新发布" value="latest" />
          <el-option label="报酬最高" value="reward" />
          <el-option label="即将截止" value="deadline_asc" />
        </el-select>
      </div>
    </div>

    <p class="result-line" aria-live="polite">
      <template v-if="loading">正在加载…</template>
      <template v-else>
        共 <span class="num">{{ total }}</span> 条
        <template v-if="filters.keyword">与“{{ filters.keyword }}”相关的</template>需求
        <button v-if="hasFilter" type="button" class="clear-btn" @click="resetFilters">清除筛选</button>
      </template>
    </p>

    <div v-if="loading && !tasks.length" class="grid">
      <div v-for="n in 6" :key="n" class="skeleton panel"><el-skeleton :rows="3" animated /></div>
    </div>

    <EmptyState
      v-else-if="!tasks.length"
      :title="hasFilter ? '没有符合条件的需求' : '广场上还没有需求'"
      :description="hasFilter ? '换个校区或分类试试，也可以清除筛选看看全部需求。' : '发布第一条需求，让同学们来帮你。'"
    >
      <el-button v-if="hasFilter" @click="resetFilters">清除筛选</el-button>
      <el-button v-else-if="userStore.isUser" type="primary" @click="$router.push('/tasks/publish')">发布需求</el-button>
    </EmptyState>

    <div v-else class="grid" :class="{ dim: loading }">
      <TaskCard v-for="task in tasks" :key="task.id" :task="task" />
    </div>

    <Pagination
      v-if="total > pageSize"
      :page="filters.page"
      :page-size="pageSize"
      :total="total"
      @page-change="p => update({ page: p }, false)"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import taskApi from '@/task/taskApi'
import TaskCard from '@/task/TaskCard.vue'
import Pagination from '@/common/ui/Pagination.vue'
import EmptyState from '@/common/ui/EmptyState.vue'
import http from '@/common/http'
import { CAMPUSES } from '@/common/labels'
import { useUserStore } from '@/user/userStore'

/**
 * 需求广场。
 * 筛选条件保存在地址栏查询参数里：从详情页返回、刷新或分享链接时保留同一筛选结果。
 * 后端 status 缺省按 published 查询，广场默认只展示待接单需求。
 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const pageSize = 18
const loading = ref(false)
const tasks = ref([])
const total = ref(0)
const categories = ref([])
const keywordInput = ref('')

const campusOptions = [{ label: '全部校区', value: '' }, ...CAMPUSES.map(c => ({ label: c.replace('校区', ''), value: c }))]

const filters = computed(() => ({
  campus: route.query.campus || '',
  categoryId: route.query.categoryId || '',
  status: route.query.status || 'published',
  sort: route.query.sort || 'latest',
  keyword: route.query.keyword || '',
  page: Number(route.query.page) || 1
}))

const hasFilter = computed(() => {
  const f = filters.value
  return Boolean(f.campus || f.categoryId || f.keyword || f.status !== 'published')
})

/** 修改筛选条件：默认回到第一页；空值从地址栏移除，保持链接干净。 */
function update(patch, resetPage = true) {
  const next = { ...filters.value, ...patch }
  if (resetPage && !('page' in patch)) next.page = 1
  const query = {}
  if (next.campus) query.campus = next.campus
  if (next.categoryId) query.categoryId = next.categoryId
  if (next.status && next.status !== 'published') query.status = next.status
  if (next.sort && next.sort !== 'latest') query.sort = next.sort
  if (next.keyword) query.keyword = next.keyword
  if (next.page > 1) query.page = String(next.page)
  router.replace({ query })
}

function applySearch() {
  update({ keyword: keywordInput.value })
}

function resetFilters() {
  keywordInput.value = ''
  router.replace({ query: {} })
}

async function fetchTasks() {
  const f = filters.value
  loading.value = true
  try {
    const params = { page: f.page, page_size: pageSize, sort: f.sort, status: f.status }
    if (f.categoryId) params.categoryId = f.categoryId
    if (f.campus) params.campus = f.campus
    if (f.keyword) params.keyword = f.keyword
    const res = await taskApi.list(params)
    tasks.value = res.data?.data?.items || []
    total.value = res.data?.data?.pagination?.total || 0
  } catch {
    tasks.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function fetchCategories() {
  try {
    categories.value = (await http.get('/categories')).data.data || []
  } catch {
    categories.value = []
  }
}

watch(() => route.query, () => {
  keywordInput.value = filters.value.keyword
  fetchTasks()
}, { immediate: true })
fetchCategories()
</script>

<style scoped>
/* —— 导视牌 —— */
.sign {
  background: var(--pine);
  color: #fff;
  border-radius: var(--r-sign);
  padding: 26px 28px 0;
  /* 底边一条杏黄标示线，呼应校园导视牌 */
  box-shadow: inset 0 -4px 0 var(--apricot);
  overflow: hidden;
}
.sign-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 32px;
}
.sign-title {
  color: #fff;
  font-size: var(--fs-3xl);
  font-weight: 700;
  letter-spacing: 0.12em;
}
.sign-desc {
  margin-top: 6px;
  color: rgba(255, 255, 255, 0.78);
  font-size: var(--fs-md);
  max-width: 46ch;
}

.search {
  display: flex;
  width: min(420px, 100%);
  flex-shrink: 0;
  background: #fff;
  border-radius: 6px;
  overflow: hidden;
}
.search input {
  flex: 1;
  min-width: 0;
  padding: 10px 14px;
  border: 0;
  font: inherit;
  color: var(--ink);
  outline: none;
}
.search:focus-within { box-shadow: 0 0 0 3px rgba(242, 181, 68, 0.6); }
.search button {
  padding: 0 18px;
  border: 0;
  background: var(--apricot);
  color: var(--ink);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
.search button:hover { background: #eaa62b; }

/* 校区牌：像导视牌上的分区标识，选中时翻成杏黄 */
.plates {
  display: flex;
  gap: 2px;
  margin-top: 22px;
  overflow-x: auto;
  scrollbar-width: none;
}
.plates::-webkit-scrollbar { display: none; }
.plate {
  padding: 9px 20px 13px;
  border: 0;
  border-radius: var(--r-sign) var(--r-sign) 0 0;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.85);
  font: inherit;
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
}
.plate:hover { background: rgba(255, 255, 255, 0.16); color: #fff; }
.plate.on {
  background: var(--apricot);
  color: var(--ink);
  font-weight: 700;
}
.plate:focus-visible { outline-color: var(--apricot); outline-offset: -2px; }

/* —— 分类与排序 —— */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 18px;
  flex-wrap: wrap;
}
.chips { display: flex; flex-wrap: wrap; gap: 8px; }
.chip {
  padding: 5px 14px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-pill);
  background: var(--surface);
  color: var(--ink-2);
  font: inherit;
  font-size: var(--fs-sm);
  cursor: pointer;
}
.chip:hover { border-color: var(--mint); color: var(--mint); }
.chip.on { background: var(--mint); border-color: var(--mint); color: #fff; }
.selects { display: flex; gap: 8px; }
.selects .el-select { width: 120px; }

.result-line {
  margin: 14px 0 12px;
  font-size: var(--fs-sm);
  color: var(--ink-2);
}
.clear-btn {
  margin-left: 10px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--mint);
  font: inherit;
  cursor: pointer;
}
.clear-btn:hover { text-decoration: underline; }

.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  transition: opacity var(--transition-fast);
}
.grid.dim { opacity: 0.55; }
.skeleton { padding: 18px; }

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

@media (max-width: 1024px) {
  .grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 720px) {
  .sign { padding: 20px 18px 0; }
  .sign-head { flex-direction: column; align-items: stretch; gap: 16px; }
  .sign-title { font-size: var(--fs-2xl); }
  .grid { grid-template-columns: 1fr; }
  .selects { width: 100%; }
  .selects .el-select { flex: 1; width: auto; }
}
</style>
