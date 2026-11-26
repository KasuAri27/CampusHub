<template>
  <div class="admin page">
    <aside class="admin-side" aria-label="管理导航">
      <p class="side-title">管理后台</p>
      <nav>
        <router-link
          v-for="item in items"
          :key="item.to"
          :to="item.to"
          class="side-link"
          :class="{ active: isActive(item) }"
        >
          <span>{{ item.label }}</span>
          <span v-if="item.key === 'tasks' && pendingCount" class="side-count num" :title="`${pendingCount} 条需求待审核`">{{ pendingCount }}</span>
        </router-link>
      </nav>
    </aside>
    <section class="admin-main">
      <router-view @pending-changed="refreshPending" />
    </section>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import adminApi from '@/admin/adminApi'

/**
 * 管理后台外壳：左侧松绿导航牌 + 右侧内容。
 * “需求管理”旁显示待审核数量，切换页面时刷新，提醒管理员及时处理审核队列。
 */
const route = useRoute()
const pendingCount = ref(0)

const items = [
  { key: 'home', to: '/admin', label: '概览', exact: true },
  { key: 'tasks', to: '/admin/tasks', label: '需求管理' },
  { key: 'users', to: '/admin/users', label: '用户管理' },
  { key: 'finance', to: '/admin/finance', label: '交易与结算' },
  { key: 'categories', to: '/admin/categories', label: '分类管理' },
  { key: 'notifications', to: '/admin/notifications', label: '系统通知' },
  { key: 'conversations', to: '/admin/conversations', label: '会话监管' }
]

function isActive(item) {
  if (item.exact) return route.path === item.to
  return route.path.startsWith(item.to) || (item.key === 'finance' && route.path.startsWith('/admin/complaint'))
}

async function refreshPending() {
  try {
    const res = await adminApi.listTasks({ status: 'pending_review', page: 1, page_size: 1 })
    pendingCount.value = res.data?.data?.pagination?.total || 0
  } catch {
    pendingCount.value = 0
  }
}

watch(() => route.path, refreshPending, { immediate: true })
</script>

<style scoped>
.admin {
  display: grid;
  grid-template-columns: 188px minmax(0, 1fr);
  gap: 28px;
  align-items: start;
}
.admin-side {
  position: sticky;
  top: calc(var(--header-h) + 20px);
  background: var(--pine);
  border-radius: var(--r-sign);
  padding: 16px 10px 14px;
  box-shadow: inset 0 -4px 0 var(--apricot);
}
.side-title {
  padding: 0 10px 10px;
  color: rgba(255, 255, 255, 0.7);
  font-size: var(--fs-xs);
  letter-spacing: 0.1em;
}
.side-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 10px;
  border-radius: var(--r-sign);
  color: rgba(255, 255, 255, 0.86);
  text-decoration: none;
  font-weight: 500;
}
.side-link:hover { background: rgba(255, 255, 255, 0.1); color: #fff; }
.side-link.active { background: #fff; color: var(--pine); font-weight: 700; }
.side-link:focus-visible { outline-color: var(--apricot); }
.side-count {
  min-width: 22px;
  padding: 0 6px;
  border-radius: var(--r-pill);
  background: var(--apricot);
  color: var(--ink);
  font-size: var(--fs-xs);
  font-weight: 700;
  line-height: 20px;
  text-align: center;
}
.admin-main { min-width: 0; }

@media (max-width: 900px) {
  .admin { grid-template-columns: 1fr; gap: 16px; }
  .admin-side { position: static; padding: 8px; }
  .side-title { display: none; }
  .admin-side nav { display: flex; overflow-x: auto; gap: 4px; }
  .side-link { white-space: nowrap; gap: 6px; }
}
</style>
