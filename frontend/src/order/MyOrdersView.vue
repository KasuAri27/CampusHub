<template>
  <div class="orders page">
    <PageHeader title="我的订单" description="接下的单和你发布的需求产生的订单都在这里，按提示完成提交、验收、付款与评价。" />

    <div class="tabs" role="tablist" aria-label="订单筛选">
      <button
        v-for="t in tabs"
        :key="t.key"
        type="button"
        role="tab"
        class="tab"
        :class="{ on: activeTab === t.key, todo: t.key === 'todo' }"
        :aria-selected="activeTab === t.key"
        @click="activeTab = t.key"
      >
        {{ t.label }}
        <span v-if="counts[t.key]" class="count num">{{ counts[t.key] }}</span>
      </button>
    </div>

    <div v-loading="loading" class="list">
      <EmptyState
        v-if="!loading && !filtered.length"
        :title="emptyCopy.title"
        :description="emptyCopy.desc"
      >
        <el-button v-if="activeTab === 'all' && !user.restricted" type="primary" @click="$router.push('/')">去需求广场看看</el-button>
      </EmptyState>

      <article v-for="order in filtered" :key="order.orderId" class="order panel">
        <header class="order-head">
          <div class="order-title">
            <router-link :to="`/tasks/${order.taskId}`">{{ order.title }}</router-link>
          </div>
          <div class="order-meta">
            <span v-if="roleLabel(order)" class="role">{{ roleLabel(order) }}</span>
            <span v-if="moneyDirection(order, user.userId) && order.amountDue != null" class="amount">
              <span class="dir">{{ moneyDirection(order, user.userId) }}</span><span class="yen">¥</span><span class="num">{{ formatMoney(order.amountDue) }}</span>
            </span>
          </div>
        </header>
        <p class="order-sub">
          <span>订单 <span class="num">#{{ order.orderId }}</span></span>
          <span>接单于 {{ formatDateTime(order.acceptedAt) }}</span>
          <StatusTag v-if="order.orderStatus === 'cancelled'" label="已取消" tone="muted" />
          <StatusTag v-else-if="order.disputed" label="异议处理中" tone="danger" />
        </p>
        <OrderActions :order="order" @updated="load" />
      </article>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import orderApi from '@/order/orderApi'
import OrderActions from '@/order/OrderActions.vue'
import PageHeader from '@/common/ui/PageHeader.vue'
import StatusTag from '@/common/ui/StatusTag.vue'
import EmptyState from '@/common/ui/EmptyState.vue'
import { useUserStore } from '@/user/userStore'
import { formatDateTime, formatMoney } from '@/common/format'
import { nextHint, orderRole, moneyDirection } from '@/order/orderFlow'

/**
 * 我的订单：GET /v1/orders/mine 同时返回作为付款方与收款方的订单。
 * “待我处理”按 orderFlow.nextHint 的 need 计算（轮到我提交、验收或付款），有待办时默认打开这一栏。
 */
const user = useUserStore()
const loading = ref(false)
const orders = ref([])
const activeTab = ref('all')

const tabs = [
  { key: 'todo', label: '待我处理' },
  { key: 'in_progress', label: '服务中' },
  { key: 'awaiting_acceptance', label: '待验收' },
  { key: 'completed', label: '已验收' },
  { key: 'cancelled', label: '已取消' },
  { key: 'all', label: '全部' }
]

function isTodo(order) { return nextHint(order, user.userId).need }

const counts = computed(() => {
  const c = { todo: 0, in_progress: 0, awaiting_acceptance: 0, completed: 0, cancelled: 0, all: orders.value.length }
  for (const o of orders.value) {
    if (isTodo(o)) c.todo++
    if (o.orderStatus in c) c[o.orderStatus]++
  }
  // “全部”不显示数字，避免与筛选计数混淆
  c.all = 0
  return c
})

const filtered = computed(() => {
  if (activeTab.value === 'all') return orders.value
  if (activeTab.value === 'todo') return orders.value.filter(isTodo)
  return orders.value.filter(o => o.orderStatus === activeTab.value)
})

const emptyCopy = computed(() => ({
  todo: { title: '没有需要你处理的订单', desc: '轮到你提交完成、验收或付款时，订单会出现在这里。' },
  all: { title: '还没有订单', desc: '在需求广场接下一单，或者等别人接下你发布的需求。' }
}[activeTab.value] || { title: '这一栏暂时没有订单', desc: '切换到“全部”查看所有订单。' }))

function roleLabel(order) { return orderRole(order, user.userId).label }

async function load() {
  loading.value = true
  try {
    const list = (await orderApi.mine()).data.data || []
    // 后端按时间返回；这里把待我处理的排到前面，其余保持原顺序
    orders.value = [...list.filter(isTodo), ...list.filter(o => !isTodo(o))]
  } catch {
    // 请求层已提示错误；保留已有列表，避免加载失败被误当成订单消失
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await load()
  if (orders.value.some(isTodo)) activeTab.value = 'todo'
})
</script>

<style scoped>
.tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--line);
  margin-bottom: 16px;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
}
.tab {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  border: 0;
  background: none;
  color: var(--ink-2);
  font: inherit;
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
}
.tab:hover { color: var(--ink); }
.tab.on { color: var(--pine); font-weight: 700; }
.tab.on::after {
  content: "";
  position: absolute;
  left: 14px;
  right: 14px;
  bottom: 0;
  height: 3px;
  background: var(--apricot);
  border-radius: 2px 2px 0 0;
}
.count {
  min-width: 20px;
  padding: 0 6px;
  border-radius: var(--r-pill);
  background: #eef2f0;
  color: var(--ink-2);
  font-size: var(--fs-xs);
  line-height: 18px;
  text-align: center;
}
.tab.todo .count { background: var(--apricot); color: var(--ink); font-weight: 700; }

.list { display: flex; flex-direction: column; gap: 14px; min-height: 160px; }
.order { padding: 18px 20px 20px; }
.order-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.order-title { display: flex; align-items: center; gap: 10px; min-width: 0; }
.order-title a {
  color: var(--ink);
  font-size: var(--fs-lg);
  font-weight: 600;
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.order-title a:hover { color: var(--mint); }
.order-meta { display: flex; align-items: center; gap: 12px; flex-shrink: 0; }
.role {
  padding: 1px 8px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-sign);
  color: var(--ink-2);
  font-size: var(--fs-xs);
}
.amount { color: var(--amber-ink); font-weight: 600; }
.amount .dir { margin-right: 6px; color: var(--ink-2); font-size: var(--fs-sm); font-weight: 500; }
.amount .yen { font-size: var(--fs-sm); margin-right: 1px; }
.amount .num { font-size: 22px; }
.order-sub {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  margin: 4px 0 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid #edf1ef;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}
@media (max-width: 640px) {
  .order-head { flex-direction: column; }
}
</style>
