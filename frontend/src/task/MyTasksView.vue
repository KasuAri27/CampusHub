<template>
  <div class="my-tasks page">
    <PageHeader title="我的需求" description="你发布的需求和接下的单都在这里；付款、验收等履约操作在“我的订单”里完成。">
      <template #actions>
        <el-button v-if="userStore.isUser" type="primary" @click="$router.push('/tasks/publish')">发布需求</el-button>
      </template>
    </PageHeader>

    <div class="tabs" role="tablist" aria-label="需求筛选">
      <button
        v-for="item in tabs"
        :key="item.key"
        type="button"
        role="tab"
        class="tab"
        :class="{ on: tab === item.key }"
        :aria-selected="tab === item.key"
        @click="tab = item.key"
      >
        {{ item.label }}
        <span v-if="lists[item.key].length" class="count num">{{ lists[item.key].length }}</span>
      </button>
    </div>

    <div v-if="loading" class="grid">
      <div v-for="n in 3" :key="n" class="skeleton panel"><el-skeleton :rows="3" animated /></div>
    </div>
    <EmptyState v-else-if="!filtered.length" :title="emptyText[tab][0]" :description="emptyText[tab][1]">
      <el-button v-if="tab === 'published' && userStore.isUser" type="primary" @click="$router.push('/tasks/publish')">发布需求</el-button>
      <el-button v-else-if="tab === 'accepted'" @click="$router.push('/')">去需求广场</el-button>
    </EmptyState>
    <div v-else class="grid">
      <TaskCard v-for="t in filtered" :key="t.cardKey" :task="t" />
    </div>
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
import PageHeader from '@/common/ui/PageHeader.vue'
import EmptyState from '@/common/ui/EmptyState.vue'
import { useUserStore } from '@/user/userStore'

const userStore = useUserStore()
const tab = ref('published')
const loading = ref(false)
const publishedTasks = ref([])
const acceptedTasks = ref([])

const tabs = [
  { key: 'published', label: '我发布的' },
  { key: 'accepted', label: '我接单的' },
  { key: 'reviewing', label: '待审核' },
  { key: 'rejected', label: '被驳回' },
  { key: 'waiting', label: '等人接单' },
  { key: 'inProgress', label: '进行中' },
  { key: 'completed', label: '已完成' }
]

const emptyText = {
  published: ['你还没有发布过需求', '先去发布一个需求吧，方便同学帮助你完成任务。'],
  accepted: ['你还没有接过单', '可以去需求广场看看有没有合适的任务。'],
  reviewing: ['没有待审核的需求', '刚发布或修改后重新提交的需求，会在管理员审核前显示在这里。'],
  rejected: ['没有被驳回的需求', '被管理员驳回的需求会显示在这里，可以查看理由并修改后重新提交。'],
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
    reviewing: mine.filter(t => t.status === 'pending_review'),
    rejected: mine.filter(t => t.status === 'rejected'),
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
.tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--line);
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
.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.skeleton { padding: 18px; }
@media (max-width: 1024px) { .grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 720px) { .grid { grid-template-columns: 1fr; } }
</style>
